package com.wledcontroller.app.house.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BigScanTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    @Test
    fun splittingReachesEveryFineCube() {
        // A wall 3 m away seen by a coarse depth picture: each pixel is ~6 cm across there.
        val w = 40; val h = 30; val f = 50f
        val raw = ShortArray(w * h) { 3000 }; val conf = ByteArray(w * h) { 220.toByte() }
        fun cubesHit(split: Int): Int {
            val out = DepthFusion.fuse(w, h, raw, conf, null, 0, 0, f, f, w / 2f, h / 2f, identity, split) { _, _ -> -1 }
            val v = TsdfVolume(voxel = 0.03f, truncation = 0.1f)
            v.integrate(P3(0f, 0f, 0f), out.points, out.colors, out.weights, out.count)
            return v.surfacePoints(1_000_000).second.size
        }
        val plain = cubesHit(1); val split = cubesHit(2)
        assertTrue("$plain vs $split", split > plain * 1.6f)
        // Split points stay on the wall.
        val out = DepthFusion.fuse(w, h, raw, conf, null, 0, 0, f, f, w / 2f, h / 2f, identity, 2) { _, _ -> -1 }
        for (i in 0 until out.count) assertEquals(3f, -out.points[i * 3 + 2], 0.01f)
    }

    @Test
    fun toggleSetKeepsWhatWasAddedOddTimes() {
        val s = LongToggleSet(4)
        val r = Random(3)
        val counts = HashMap<Long, Int>()
        repeat(20000) { val k = r.nextLong(0, 3000); counts.merge(k, 1, Int::plus); s.toggle(k) }
        val left = HashSet<Long>(); s.forEach { left += it }
        assertEquals(counts.filterValues { it % 2 == 1 }.keys, left)
        assertEquals(left.size, s.size)
    }

    @Test
    fun longIntMapStoresAndFinds() {
        val m = LongIntMap(4)
        for (i in 0 until 50000) m[i * 7919L - 100000] = i
        for (i in 0 until 50000) assertEquals(i, m[i * 7919L - 100000])
        assertEquals(-1, m[123456789L])
    }

    @Test
    fun tidiesABigMeshQuickly() {
        // A 600 × 600 grid sheet (720k triangles) with a small hole: filled and smoothed in plain arrays.
        val n = 600
        val pos = FloatArray((n + 1) * (n + 1) * 3)
        for (y in 0..n) for (x in 0..n) { val k = (y * (n + 1) + x) * 3; pos[k] = x * 0.03f; pos[k + 1] = y * 0.03f; pos[k + 2] = 0f }
        val idx = ArrayList<Int>()
        for (y in 0 until n) for (x in 0 until n) {
            if (x == 300 && y == 300) continue
            val a = y * (n + 1) + x; val b = a + 1; val c = a + n + 1; val d = c + 1
            idx += listOf(a, b, d, a, d, c)
        }
        val part = MeshPart(pos, null, idx.toIntArray(), -1, 0, IntArray(pos.size / 3))
        val t0 = System.nanoTime()
        val tidy = MeshTidy.tidy(part)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(part.vertexCount + 1, tidy.vertexCount)
        assertTrue("took $ms ms", ms < 20_000)
    }

    @Test
    fun noGuessedDepthInTheSky() {
        // Looking level at a wall 4 m away (lower half of the picture, measured); the upper half is
        // blue sky where only the smoothed depth has a (made-up) distance.
        val w = 40; val h = 30; val f = 30f
        val raw = ShortArray(w * h); val conf = ByteArray(w * h); val smooth = ShortArray(w * h) { 4000 }
        for (r in h / 2 until h) for (c in 0 until w) { raw[r * w + c] = 4000; conf[r * w + c] = 200.toByte() }
        val sky = 0xFF5A9BE6.toInt(); val wall = 0xFFC8C8C0.toInt()
        val out = DepthFusion.fuse(w, h, raw, conf, smooth, w, h, f, f, w / 2f, h / 2f, identity) { _, r -> if (r < h / 2) sky else wall }
        // Nothing much above the wall's top edge (a pixel or so of fill next to it at most).
        for (i in 0 until out.count) assertTrue("${out.points[i * 3 + 1]}", out.points[i * 3 + 1] < 4f * 4.5f / f)
        assertTrue(out.count > w * (h / 2) * 0.8)
        assertTrue(DepthFusion.skyLike(sky)); assertTrue(!DepthFusion.skyLike(wall))
    }

    @Test
    fun theLiveViewSpreadsOverTheWholeScan() {
        // A long wall: far more surface cubes than the view shows.
        val v = TsdfVolume(voxel = 0.05f, truncation = 0.15f)
        val n = 20000
        val pts = FloatArray(n * 3); val cols = IntArray(n) { 0xFFFFFF }; val wts = FloatArray(n) { 1f }
        for (i in 0 until n) { pts[i * 3] = (i % 400) * 0.05f; pts[i * 3 + 1] = (i / 400) * 0.05f; pts[i * 3 + 2] = -3f }
        repeat(3) { v.integrate(P3(10f, 1.25f, 5f), pts, cols, wts, n) }
        v.surfacePoints(2000)
        val shown = v.surfacePoints(2000).first
        val xs = (0 until shown.size / 3).map { shown[it * 3] }
        assertTrue("${v.surfaceCount}", v.surfaceCount > 4000)
        // From one end of the wall to the other, not just a corner of it.
        assertTrue(xs.min() < 2f && xs.max() > 17f)
    }
}
