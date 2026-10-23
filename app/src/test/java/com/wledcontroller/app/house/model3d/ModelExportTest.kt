package com.wledcontroller.app.house.model3d

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ModelExportTest {
    /** A house built from photos (front textured) plus a scan-like part with a colour at each corner. */
    private fun model(): Pair<Mesh, List<ByteArray>> {
        val shell = Shell.build(ShellSpec(), mapOf(Face.FRONT to 0, Face.LEFT to 1))
        val scanned = MeshPart(floatArrayOf(0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f), null, intArrayOf(0, 1, 2), -1, -1, intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()))
        return Mesh(shell.parts + scanned) to listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6, 7, 8))
    }

    private fun sameShape(a: Mesh, b: Mesh) {
        assertEquals(a.triangleCount, b.triangleCount)
        val (alo, ahi) = a.bounds; val (blo, bhi) = b.bounds
        assertTrue((alo - blo).length < 1e-4f && (ahi - bhi).length < 1e-4f)
    }

    @Test
    fun glbOpensAgainAsItWasSaved() {
        val (mesh, images) = model()
        val lights = ModelExport.lightMarkers(listOf(P3(0f, 3f, 0f), null, P3(1f, 3f, 0f)), intArrayOf(0xFF0000, 0, 0x00FF00))!!
        val bytes = ByteArrayOutputStream().also { ModelExport.writeGlb(mesh, images, it, lights) }.toByteArray()
        assertEquals(0, bytes.size % 4)
        val back = GlbReader.read(bytes)
        // The house, then the lights (two markers of 8 triangles).
        assertEquals(mesh.triangleCount + 16, back.mesh.triangleCount)
        assertArrayEquals(images[0], back.images[0]); assertArrayEquals(images[1], back.images[1])
        val front = back.mesh.parts[0]
        assertEquals(0, front.texture); assertArrayEquals(mesh.parts[0].uvs, front.uvs, 1e-6f)
        assertEquals(1, back.mesh.parts[3].texture)
        val scanned = back.mesh.parts[mesh.parts.size - 1]
        assertArrayEquals(mesh.parts.last().vertexColors, scanned.vertexColors)
        assertEquals(0xFF00FF00.toInt(), back.mesh.parts.last().vertexColors!![6])
        sameShape(Mesh(mesh.parts), Mesh(back.mesh.parts.dropLast(1)))
    }

    @Test
    fun objZipOpensAgainAsItWasSaved() {
        val (mesh, images) = model()
        val bytes = ByteArrayOutputStream().also { ModelExport.writeObjZip(mesh, images, it) }.toByteArray()
        val files = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; files[e.name] = z.readBytes() } }
        assertEquals(setOf("house.obj", "house.mtl", "tex0.jpg", "tex1.jpg"), files.keys)
        val back = ObjReader.read(files.getValue("house.obj").decodeToString()) { files[it] }
        sameShape(mesh, back.mesh)
        assertEquals(2, back.images.size)
        val textured = back.mesh.parts.first { it.texture >= 0 }
        // Picture coordinates come back the same (OBJ stores them bottom-up).
        assertArrayEquals(mesh.parts[0].uvs, textured.uvs, 1e-4f)
        val colored = back.mesh.parts.first { it.vertexColors != null }
        assertEquals(0xFF0000FF.toInt(), colored.vertexColors!![2])
    }
}

class ModelLightsTest {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun lightsComeBackWhereTheyWere() {
        val spec = ShellSpec()
        val mesh = Shell.build(spec)
        val wall = Shell.frontCorners(spec)
        val cam = Camera.orbit(P3(0f, 2f, 0f), 0.3f, 0.1f, 15f, 1f)
        val photo = wall.map { cam.project(it, 1.5f)!!.let { (x, y, _) -> com.wledcontroller.app.house.MapPoint(x, y) } }
        val model = House3D("a", House3D.Kind.SCAN, lineup = cam, lineupModel = wall, lineupPhoto = photo)
        val side = listOf(P3(6f, 3f, 0f), P3(6f, 3f, -9f))
        val back = listOf(P3(6f, 3f, -9f), P3(-6f, 3f, -9f))
        val runs = listOf(
            com.wledcontroller.app.house.LightRun("r1", "Side eave", "d", 0, 10, points3d = side),
            com.wledcontroller.app.house.LightRun("r2", "Back eave", "d", 10, 10, points3d = back),
            com.wledcontroller.app.house.LightRun("r3", "Bush", "d", 20, 1),
        )
        val map = com.wledcontroller.app.house.HouseMap(photoPath = "/p.jpg", aspect = 1.5f, runs = runs, model3d = model)
        val placement = ModelLights.of(map, model)
        val data = json.encodeToJsonElement(ModelLights.serializer(), placement) as kotlinx.serialization.json.JsonObject
        val markers = ModelExport.lightMarkers(listOf(P3(0f, 3f, 5f)), intArrayOf(0xFFFFFF))

        // Saved as .glb and opened again: same model (markers left out), same placement.
        val glb = java.io.ByteArrayOutputStream().also { ModelExport.writeGlb(mesh, emptyList(), it, markers, data) }.toByteArray()
        val read = GlbReader.read(glb)
        assertEquals(mesh.triangleCount, read.mesh.triangleCount)
        val restoredFrom = json.decodeFromString(ModelLights.serializer(), read.lights!!)
        assertEquals(placement, restoredFrom)

        // On another phone: the same runs but new ids — matched by name. A different photo: needs lining up again.
        val elsewhere = map.copy(model3d = null, runs = runs.map { it.copy(id = it.id + "x", points3d = null) })
        val fresh = House3D("b", House3D.Kind.SCAN)
        val restored = restoredFrom.applyTo(elsewhere, fresh)
        assertEquals(side, restored.runs[0].points3d); assertEquals(back, restored.runs[1].points3d)
        assertEquals(null, restored.runs[2].points3d)
        assertEquals(cam, restored.model3d!!.lineup)
        assertTrue(restoredFrom.summary(restored).contains("2 runs"))
        val newPhoto = restoredFrom.applyTo(elsewhere.copy(aspect = 0.75f), fresh)
        assertEquals(null, newPhoto.model3d!!.lineup)

        // As an OBJ zip: the placement goes along beside it, and the markers aren't read in as house.
        val zip = java.io.ByteArrayOutputStream().also { ModelExport.writeObjZip(mesh, emptyList(), it, markers, data) }.toByteArray()
        val files = HashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(zip.inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; files[e.name] = z.readBytes() } }
        assertEquals(placement, json.decodeFromString(ModelLights.serializer(), files.getValue(ModelExport.OBJ_DATA).decodeToString()))
        assertEquals(mesh.triangleCount, ObjReader.read(files.getValue("house.obj").decodeToString()) { files[it] }.mesh.triangleCount)
    }
}
