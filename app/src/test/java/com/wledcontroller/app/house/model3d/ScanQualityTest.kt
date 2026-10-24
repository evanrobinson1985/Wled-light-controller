package com.wledcontroller.app.house.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ScanQualityTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    @Test
    fun fillsGapsAtLowTrustAndSkipsEdges() {
        // A wall 3 m away, with a post 1.5 m away in the middle; confident depth on only a third of the pixels.
        val w = 64; val h = 48; val f = 50f
        val raw = ShortArray(w * h); val conf = ByteArray(w * h); val smooth = ShortArray(w * h)
        for (r in 0 until h) for (c in 0 until w) {
            val mm = if (c in 30..33) 1500 else 3000
            smooth[r * w + c] = mm.toShort()
            if ((r * w + c) % 3 == 0) { raw[r * w + c] = mm.toShort(); conf[r * w + c] = 220.toByte() }
        }
        val out = DepthFusion.fuse(w, h, raw, conf, smooth, w, h, f, f, w / 2f, h / 2f, identity) { _, _ -> -1 }
        // Nearly every pixel used (gaps filled), but not those on the post's outline.
        assertTrue("${out.count}", out.count > w * h * 0.8 && out.count < w * h)
        val trusts = out.weights.copyOf(out.count).toList()
        assertTrue(trusts.any { it < 0.15f } && trusts.any { it > 0.7f })
        // Every point really on the wall or the post: nothing in between.
        for (i in 0 until out.count) {
            val z = -out.points[i * 3 + 2]
            assertTrue("$z", abs(z - 3f) < 0.01f || abs(z - 1.5f) < 0.01f)
        }
        assertEquals(3f, out.medianM, 0.01f)
        assertEquals(null, DepthFusion.distanceHint(3f))
        assertTrue(DepthFusion.distanceHint(5.5f)!!.startsWith("Move closer"))
    }

    @Test
    fun trustsSlantedSurfacesLess() {
        // A floor seen at a low angle vs a wall straight on.
        val w = 40; val h = 30; val f = 40f
        fun view(slanted: Boolean): Float {
            val raw = ShortArray(w * h); val conf = ByteArray(w * h) { 200.toByte() }
            for (r in 0 until h) for (c in 0 until w) raw[r * w + c] = (if (slanted) 1500 + c * 60 else 3000).toShort()
            val out = DepthFusion.fuse(w, h, raw, conf, null, 0, 0, f, f, w / 2f, h / 2f, identity) { _, _ -> -1 }
            return out.weights.copyOf(out.count).average().toFloat()
        }
        assertTrue(view(true) < view(false) * 0.9f)
    }

    @Test
    fun noticesTrackingJumps() {
        val a = identity.copyOf(); val b = identity.copyOf().also { it[12] = 0.5f }
        assertTrue(DepthFusion.jumped(a, b, 0.033f))
        val c = identity.copyOf().also { it[12] = 0.02f }
        assertTrue(!DepthFusion.jumped(a, c, 0.033f))
    }

    @Test
    fun fillsSmallHolesAndSmoothsWithoutShrinking() {
        // A bumpy 2 × 2 m sheet of 5 cm squares with a hole, scanned as squares in pairs.
        val n = 40; val rnd = Random(3)
        val pos = FloatArray((n + 1) * (n + 1) * 3)
        for (j in 0..n) for (i in 0..n) { val k = j * (n + 1) + i; pos[k * 3] = i * 0.05f; pos[k * 3 + 1] = j * 0.05f; pos[k * 3 + 2] = (rnd.nextFloat() - 0.5f) * 0.02f }
        val idx = ArrayList<Int>()
        for (j in 0 until n) for (i in 0 until n) {
            if (i in 18..20 && j in 18..20) continue // the hole
            val a = j * (n + 1) + i
            idx += listOf(a, a + 1, a + n + 2, a, a + n + 2, a + n + 1)
        }
        val part = MeshPart(pos, null, idx.toIntArray(), -1, -1, IntArray((n + 1) * (n + 1)) { 0xFF808080.toInt() })
        val filled = MeshTidy.fillHoles(part, 60)
        assertTrue(filled.triangleCount > part.triangleCount)
        assertEquals(0, filled.indices.size % 6)
        // The middle of the hole is covered now.
        assertTrue(Mesh(listOf(filled)).raycast(P3(1f, 1f, 1f), P3(0f, 0f, -1f)) != null)
        assertTrue(Mesh(listOf(part)).raycast(P3(1f, 1f, 1f), P3(0f, 0f, -1f)) == null)
        // The outer edge isn't a "hole": it stays open (a little over 60 edges round would be).
        val smooth = MeshTidy.smooth(filled, 2)
        fun bumpiness(m: MeshPart) = (0 until m.vertexCount).map { abs(m.positions[it * 3 + 2]) }.average()
        assertTrue(bumpiness(smooth) < bumpiness(filled) * 0.6)
        // Same size as before: the sheet hasn't shrunk.
        val (lo, hi) = Mesh(listOf(smooth)).bounds
        assertTrue(hi.x - lo.x > 1.95f && hi.y - lo.y > 1.95f)
        // Still paints from photos: squares in pairs.
        PhotoTexturer.texture(smooth, emptyList(), { null }, pageSize = 512, onPage = { _, _ -> })
    }

    @Test
    fun showsHowWellEachPartIsScanned() {
        val v = TsdfVolume(0.1f, 0.3f)
        // One glance at a wall: thin (red). Many more: solid (green).
        val pts = FloatArray(3 * 10) { i -> if (i % 3 == 2) -2f else (i / 3) * 0.1f }
        v.integrate(P3.ZERO, pts, IntArray(10) { 0xFF123456.toInt() }, FloatArray(10) { 1f }, 10)
        val (_, once) = v.surfacePoints(1000, quality = true)
        assertTrue(once.isNotEmpty() && once.all { it == 0xFFFF3B30.toInt() })
        repeat(30) { v.integrate(P3.ZERO, pts, IntArray(10) { 0xFF123456.toInt() }, FloatArray(10) { 1f }, 10) }
        val (_, many) = v.surfacePoints(1000, quality = true)
        assertTrue(many.any { it == 0xFF34C759.toInt() })
        // Its own colours otherwise.
        assertTrue(v.surfacePoints(1000).second.all { it and 0xFFFFFF == 0x123456 })
    }
}
