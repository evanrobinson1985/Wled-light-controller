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

class CastFromTest {
    @Test
    fun aPhotoTakenStandingOnTheScannedLawnStillPlacesLights() {
        // 40 × 40 m of lawn (51,200 triangles) all round where the photo was taken, and the house.
        val n = 160
        val pos = FloatArray((n + 1) * (n + 1) * 3); val idx = IntArray(n * n * 6)
        for (j in 0..n) for (i in 0..n) { val k = j * (n + 1) + i; pos[k * 3] = -20f + i * 0.25f; pos[k * 3 + 1] = 0f; pos[k * 3 + 2] = 20f - j * 0.25f }
        var w = 0
        for (j in 0 until n) for (i in 0 until n) { val a = j * (n + 1) + i; for (v in intArrayOf(a, a + 1, a + n + 2, a, a + n + 2, a + n + 1)) idx[w++] = v }
        val lawn = MeshPart(pos, null, idx)
        val house = Shell.build(ShellSpec(widthM = 10f, depthM = 8f, wallM = 3f))
        val mesh = Mesh(house.parts + lawn)
        // Standing 12 m in front, phone at 1.6 m: most of the lawn is beside and behind.
        val cam = Camera(P3(0f, 1.6f, 12f), P3(0f, -0.05f, -1f).normalized(), P3.UP, 1f)
        val wall = Shell.frontCorners(ShellSpec(widthM = 10f, depthM = 8f, wallM = 3f))
        val photo = wall.map { cam.project(it, 1.5f)!!.let { (x, y, _) -> x to y } }
        val start = System.nanoTime()
        val hits = mesh.castFrom(cam, 1.5f, photo + listOf(0.5f to 0.98f))
        assertTrue("took ${(System.nanoTime() - start) / 1_000_000} ms", System.nanoTime() - start < 3_000_000_000L)
        wall.zip(hits.take(4)).forEach { (p, h) -> assertTrue("$p vs $h", (p - h!!).length < 0.05f) }
        // Low in the picture: the lawn in front of the house.
        assertEquals(0f, hits[4]!!.y, 1e-3f)
    }
}

class LassoTest {
    @Test
    fun circlingErasesWhatCanBeSeenOrRightThrough() {
        // A house of fine pieces (so a loop can take part of a wall), seen straight on.
        val v = TsdfVolume(voxel = 0.1f, truncation = 0.3f)
        val box = Shell.build(ShellSpec(widthM = 6f, depthM = 4f, wallM = 3f, roof = Roof.FLAT))
        for (k in 0..24) {
            val cam = Camera.orbit(P3(0f, 1.5f, -2f), k * 0.26f, 0.2f, 8f, 0.8f)
            val w = 80; val h = 60
            val pts = FloatArray(w * h * 3); val cols = IntArray(w * h); val wts = FloatArray(w * h); var n = 0
            for (y in 0 until h) for (x in 0 until w) {
                val dir = cam.ray((x + 0.5f) / w, (y + 0.5f) / h, w.toFloat() / h)
                val d = box.raycast(cam.eye, dir) ?: continue
                val p = cam.eye + dir * d
                pts[n * 3] = p.x; pts[n * 3 + 1] = p.y; pts[n * 3 + 2] = p.z; cols[n] = -1; wts[n] = 1f; n++
            }
            v.integrate(cam.eye, pts, cols, wts, n)
        }
        val mesh = v.extract()
        val cam = Camera.orbit(P3(0f, 1.5f, -2f), 0f, 0f, 12f, 1f)
        // A loop round the middle of the front wall.
        val loop = (0 until 24).map { val a = it / 24f * 6.2832f; 0.5f + 0.12f * kotlin.math.cos(a) to 0.5f + 0.12f * kotlin.math.sin(a) }
        fun lasso(through: Boolean) = MeshEdit.Lasso(cam, 1.5f, loop.map { it.first }, loop.map { it.second }, through)
        fun frontMiddle(m: Mesh) = m.raycast(P3(0f, 1.5f, 12f), P3(0f, 0f, -1f))
        val seen = MeshEditor.apply(mesh, lasso(false))
        assertTrue(seen.triangleCount < mesh.triangleCount)
        // The front wall's middle is gone, so the ray now reaches the back wall, still there.
        assertEquals(16f, frontMiddle(seen)!!, 0.2f)
        // Right through: the back wall goes too.
        val through = MeshEditor.apply(mesh, lasso(true))
        assertTrue(frontMiddle(through) == null || frontMiddle(through)!! > 16.5f)
        // Kept as an edit, and it turns with the model.
        val turn: (P3) -> P3 = { p -> P3(p.z, p.y, -p.x) }
        assertEquals(seen.triangleCount, MeshEditor.apply(mesh.transformed(turn), listOf(lasso(false).moved(turn))).triangleCount)
        val json = kotlinx.serialization.json.Json.encodeToString(MeshEdit.serializer(), lasso(true))
        assertEquals(lasso(true), kotlinx.serialization.json.Json.decodeFromString(MeshEdit.serializer(), json))
    }
}
