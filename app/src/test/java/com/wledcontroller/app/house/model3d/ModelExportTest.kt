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
