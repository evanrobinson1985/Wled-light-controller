package com.wledcontroller.app.house.model3d

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshEditsTest {
    /** A house (6 m wide, 3 m walls) with lawn round it and a few floating specks. */
    private fun scan(): Mesh {
        val house = Shell.build(ShellSpec(widthM = 6f, depthM = 4f, wallM = 3f, roof = Roof.FLAT))
        // Lawn: a 14 × 12 m sheet of 0.5 m squares just under the house, joined up.
        val n = 28; val m = 24
        val pos = FloatArray((n + 1) * (m + 1) * 3); val idx = ArrayList<Int>()
        for (j in 0..m) for (i in 0..n) { val k = j * (n + 1) + i; pos[k * 3] = -7f + i * 0.5f; pos[k * 3 + 1] = -0.02f; pos[k * 3 + 2] = 4f - j * 0.5f }
        for (j in 0 until m) for (i in 0 until n) { val a = j * (n + 1) + i; idx += listOf(a, a + 1, a + n + 2, a, a + n + 2, a + n + 1) }
        val lawn = MeshPart(pos, null, idx.toIntArray(), -1, 0xFF00AA00.toInt(), IntArray((n + 1) * (m + 1)) { 0xFF00AA00.toInt() })
        // Specks: lone triangles floating about.
        val specks = (0 until 5).map { s ->
            val x = -5f + s * 2f
            MeshPart(floatArrayOf(x, 4f, 2f, x + 0.05f, 4f, 2f, x, 4.05f, 2f), null, intArrayOf(0, 1, 2), -1, -1)
        }
        return Mesh(house.parts + lawn + specks)
    }

    @Test
    fun cropsAwayTheLawn() {
        val mesh = scan()
        val houseTris = Shell.build(ShellSpec(widthM = 6f, depthM = 4f, wallM = 3f, roof = Roof.FLAT)).triangleCount
        // Up off the ground, and close round the house.
        val cropped = MeshEditor.apply(mesh, MeshEdit.Crop(P3(-3.5f, 0.05f, -4.5f), P3(3.5f, 3.5f, 0.5f)))
        val (lo, hi) = cropped.bounds
        assertTrue(lo.y >= 0f && hi.y <= 3.5f && lo.x >= -3.5f && hi.x <= 3.5f)
        assertTrue(cropped.triangleCount in 1..houseTris)
        assertTrue(cropped.parts.none { it.color == 0xFF00AA00.toInt() })
    }

    @Test
    fun erasesWhereItsPainted() {
        val mesh = scan()
        // A stroke along the front of the lawn.
        val stroke = MeshEdit.Erase((0..20).map { P3(-5f + it * 0.5f, 0f, 3f) }, 0.6f)
        val after = MeshEditor.apply(mesh, stroke)
        assertTrue(after.triangleCount < mesh.triangleCount)
        // Nothing left near the stroke; the house untouched.
        for (p in after.parts) for (t in 0 until p.triangleCount) {
            val c = (0 until 3).map { p.vertex(p.indices[t * 3 + it]) }.reduce { a, b -> a + b } * (1f / 3)
            assertTrue(!(c.z > 2.45f && c.z < 3.55f && c.x in -5f..5f && c.y < 0.5f))
        }
        assertEquals(10f, after.raycast(P3(0f, 1.5f, 10f), P3(0f, 0f, -1f))!!, 1e-3f)
        // Colours stay with their corners.
        val lawn = after.parts.first { it.color == 0xFF00AA00.toInt() }
        assertEquals(lawn.vertexCount, lawn.vertexColors!!.size)
    }

    @Test
    fun tidiesAwayLooseBits() {
        val mesh = scan()
        val tidy = MeshEditor.apply(mesh, MeshEdit.DropBits(minTriangles = 5))
        assertEquals(mesh.triangleCount - 5, tidy.triangleCount)
        // Keep only the biggest piece: the lawn (the house's walls aren't joined to it).
        val one = MeshEditor.apply(mesh, MeshEdit.DropBits(keepLargestOnly = true))
        assertEquals(28 * 24 * 2, one.triangleCount)
        // A model painted from photos (every square with corners of its own) still counts as one piece.
        val split = Mesh(listOf(MeshPart(floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 2f, 1f, 0f), null, intArrayOf(0, 1, 2, 3, 4, 5))))
        assertEquals(2, MeshEditor.apply(split, MeshEdit.DropBits(minTriangles = 2)).triangleCount)
    }

    @Test
    fun editsAreKeptAndMoveWithTheModel() {
        val edits: List<MeshEdit> = listOf(MeshEdit.Crop(P3(-1f, 0f, -2f), P3(1f, 3f, 0f)), MeshEdit.Erase(listOf(P3(0f, 1f, 0f)), 0.3f), MeshEdit.DropBits(50))
        val json = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MeshEdit.serializer()), edits)
        assertEquals(edits, Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MeshEdit.serializer()), json))
        // A quarter turn about y: the box turns with it and stays square.
        val turn: (P3) -> P3 = { p -> P3(p.z, p.y, -p.x) }
        val c = edits[0].moved(turn) as MeshEdit.Crop
        assertEquals(P3(-2f, 0f, -1f), c.lo); assertEquals(P3(0f, 3f, 1f), c.hi)
        // Doing it to a turned model is the same as turning the done model.
        val mesh = scan()
        val a = MeshEditor.apply(mesh.transformed(turn), edits.map { it.moved(turn) })
        val b = MeshEditor.apply(mesh, edits).transformed(turn)
        assertEquals(b.triangleCount, a.triangleCount)
    }
}
