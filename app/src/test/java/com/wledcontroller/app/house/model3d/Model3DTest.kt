package com.wledcontroller.app.house.model3d

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.placedLeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class Model3DTest {
    private fun near(a: P3, b: P3, tol: Float = 0.02f) = assertTrue("$a vs $b", (a - b).length < tol)

    @Test
    fun cameraRaysAndProjectionAgree() {
        val cam = Camera.orbit(P3(0f, 2f, 0f), 0.4f, 0.2f, 15f)
        val p = P3(3f, 1f, -2f)
        val (x, y, z) = cam.project(p, 1.5f)!!
        near(cam.eye + cam.ray(x, y, 1.5f) * ((p - cam.eye).length), p)
        assertTrue(z > 0f)
        assertNull(cam.project(cam.eye - cam.forward, 1.5f))
        // The OpenGL matrix puts the point at the same spot.
        val m = cam.viewProjection(1.5f, 0.1f, 100f)
        val cx = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val cy = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val cw = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        assertEquals(x, (cx / cw + 1f) / 2f, 1e-3f)
        assertEquals(y, (1f - cy / cw) / 2f, 1e-3f)
    }

    @Test
    fun linesUpWithThePhotoItWasTakenFrom() {
        val spec = ShellSpec(widthM = 14f, depthM = 10f, wallM = 3f)
        val wall = Shell.frontCorners(spec)
        for ((yaw, pitch, focal) in listOf(Triple(0.35f, 0.1f, 0.9f), Triple(-0.5f, 0.25f, 1.3f), Triple(0f, 0.05f, 0.92f))) {
            val truth = Camera.orbit(P3(0.5f, 2f, -1f), yaw, pitch, 18f, focal)
            val photo = wall.map { truth.project(it, 4f / 3f)!!.let { (x, y, _) -> MapPoint(x, y) } }
            val cam = Lineup.solve(wall, photo, 4f / 3f)!!
            // Every corner lands back where it was in the photo.
            wall.zip(photo).forEach { (p, q) ->
                val (x, y, _) = cam.project(p, 4f / 3f)!!
                assertEquals(q.x, x, 0.003f); assertEquals(q.y, y, 0.003f)
            }
            // The camera's in front of the house, about where it really was (the lens is only known from an angle).
            assertTrue(cam.eye.z > 0f)
            if (abs(yaw) > 0.3f) { near(cam.eye, truth.eye, 0.6f); assertEquals(focal, cam.focal, 0.08f) }
            // Off the wall too: the roof's peak.
            val peak = P3(0f, 5.5f, -5f)
            if (abs(yaw) > 0.3f) {
                val a = truth.project(peak, 4f / 3f)!!; val b = cam.project(peak, 4f / 3f)!!
                assertEquals(a.first, b.first, 0.01f); assertEquals(a.second, b.second, 0.01f)
            }
        }
    }

    @Test
    fun buildsAHouseFromItsSize() {
        val spec = ShellSpec(widthM = 12f, depthM = 8f, wallM = 3f, roof = Roof.GABLE, roofM = 2f, ridgeAcross = true)
        val mesh = Shell.build(spec, mapOf(Face.FRONT to 0))
        val (lo, hi) = mesh.bounds
        near(lo, P3(-6f, 0f, -8f)); near(hi, P3(6f, 5f, 0f))
        assertNotNull(mesh.parts[0].uvs); assertNull(mesh.parts[1].uvs)
        assertTrue(Shell.hasGable(spec, Face.LEFT) && !Shell.hasGable(spec, Face.FRONT))
        assertEquals(0.6f, Shell.wallShare(spec, Face.LEFT), 1e-4f)
        // Straight in at the middle of the front wall, and down onto the roof.
        assertEquals(10f, mesh.raycast(P3(0f, 1.5f, 10f), P3(0f, 0f, -1f))!!, 1e-3f)
        assertEquals(5f, mesh.raycast(P3(0f, 10f, -4f), P3(0f, -1f, 0f))!!, 1e-3f)
        for (r in Roof.entries) for (across in listOf(true, false)) for (w in listOf(12f, 6f))
            assertTrue(Shell.build(spec.copy(roof = r, ridgeAcross = across, widthM = w)).raycast(P3(0f, 20f, -4f), P3(0f, -1f, 0f)) != null)
    }

    @Test
    fun placesTheLightsFromThePhotoOntoTheModel() {
        val spec = ShellSpec(widthM = 10f, depthM = 8f, wallM = 3f, roof = Roof.GABLE, roofM = 2f)
        val mesh = Shell.build(spec)
        val truth = Camera.orbit(P3(0f, 2f, 0f), 0.4f, 0.12f, 16f, 1f)
        val aspect = 1.5f
        fun photo(p: P3) = truth.project(p, aspect)!!.let { (x, y, _) -> MapPoint(x, y) }
        val wall = Shell.frontCorners(spec)
        val eave = LightRun("eave", "Eave", "d", 0, 11, points = listOf(photo(wall[0]), photo(wall[1])))
        val bush = LightRun("bush", "Bush", "d", 11, 1, points = listOf(photo(P3(2f, 0.5f, 0f))), depthCm = 150f)
        val side = LightRun("side", "Side", "d", 12, 5, points = listOf(MapPoint(0.9f, 0.3f), MapPoint(0.95f, 0.3f)), points3d = listOf(P3(5f, 3f, 0f), P3(5f, 3f, -8f)))
        val cam = Lineup.solve(wall, wall.map(::photo), aspect)!!
        val map = HouseMap(aspect = aspect, runs = listOf(eave, bush, side))
        val model = House3D("x", House3D.Kind.PHOTOS, lineup = cam, lineupModel = wall, lineupPhoto = wall.map(::photo))
        val leds = map.placedLeds()
        val pos = Led3D.positions(map, leds, model, mesh)
        near(pos[0]!!, wall[0], 0.05f); near(pos[10]!!, wall[1], 0.05f); (1..9).forEach { assertEquals(3f, pos[it]!!.y, 0.05f); assertEquals(0f, pos[it]!!.z, 0.05f); assertTrue(pos[it]!!.x > pos[it - 1]!!.x) }
        // The bush stands out in front of the wall.
        assertEquals(1.5f, pos[11]!!.z, 0.05f)
        // The side run goes along the side of the house, every 2 m.
        near(pos[12]!!, P3(5f, 3f, 0f)); near(pos[14]!!, P3(5f, 3f, -4f)); near(pos[16]!!, P3(5f, 3f, -8f))
        // And can be shown on the photo.
        assertEquals(5, Led3D.toPhoto(side.points3d!!.let { Led3D.along(it, side) }, cam, aspect)!!.size)
    }

    @Test
    fun meshFilesRoundTrip() {
        val mesh = Shell.build(ShellSpec(), mapOf(Face.FRONT to 0, Face.BACK to 1))
        val bytes = ByteArrayOutputStream().also { MeshFile.write(mesh, it) }.toByteArray()
        val back = MeshFile.read(bytes.inputStream())
        assertEquals(mesh.parts.size, back.parts.size)
        assertEquals(mesh.triangleCount, back.triangleCount)
        assertEquals(1, back.parts[2].texture)
        assertTrue(mesh.parts[0].positions.contentEquals(back.parts[0].positions))
    }

    @Test
    fun readsObjWithItsMaterial() {
        val obj = """
            mtllib house.mtl
            v 0 0 0
            v 2 0 0
            v 2 3 0
            v 0 3 0
            vt 0 0
            vt 1 0
            vt 1 1
            vt 0 1
            usemtl wall
            f 1/1 2/2 3/3 4/4
        """.trimIndent()
        val files = mapOf("house.mtl" to "newmtl wall\nKd 0.5 0.5 0.5\nmap_Kd tex.jpg\n".toByteArray(), "tex.jpg" to byteArrayOf(1, 2, 3))
        val m = ObjReader.read(obj) { files[it] }
        assertEquals(1, m.images.size)
        val p = m.mesh.parts.single()
        assertEquals(2, p.triangleCount); assertEquals(0, p.texture)
        // Picture coordinates flipped to run top to bottom.
        assertEquals(1f, p.uvs!![1], 1e-6f)
        assertEquals(3f, m.mesh.bounds.second.y, 1e-6f)
    }

    @Test
    fun readsGlbWithANodeTransformAndPicture() {
        // One triangle, moved up 2 m by its node, with a picture.
        val bin = ByteBuffer.allocate(36 + 24 + 8 + 4).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f).forEach { bin.putFloat(it) }
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f).forEach { bin.putFloat(it) }
        bin.put(byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2))
        bin.putShort(0); bin.putShort(1)
        val json = """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],
            "nodes":[{"mesh":0,"translation":[0,2,0]}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0,"TEXCOORD_0":1},"material":0}]}],
            "materials":[{"pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}],
            "textures":[{"source":0}],"images":[{"bufferView":2,"mimeType":"image/jpeg"}],
            "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},{"bufferView":1,"componentType":5126,"count":3,"type":"VEC2"}],
            "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":24},{"buffer":0,"byteOffset":60,"byteLength":8}],
            "buffers":[{"byteLength":72}]}""".replace(Regex("\\s+"), "")
        val jb = json.toByteArray().let { it + ByteArray((4 - it.size % 4) % 4) { ' '.code.toByte() } }
        val out = ByteBuffer.allocate(12 + 8 + jb.size + 8 + 72).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67); out.putInt(2); out.putInt(out.capacity())
        out.putInt(jb.size); out.putInt(0x4E4F534A); out.put(jb)
        out.putInt(72); out.putInt(0x004E4942); out.put(bin.array())
        val m = GlbReader.read(out.array())
        val p = m.mesh.parts.single()
        assertEquals(2f, p.positions[1], 1e-6f); assertEquals(3f, p.positions[7], 1e-6f)
        assertEquals(0, p.texture)
        assertTrue(m.images.single().contentEquals(byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)))
    }
}
