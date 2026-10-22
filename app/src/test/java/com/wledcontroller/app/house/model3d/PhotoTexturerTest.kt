package com.wledcontroller.app.house.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.floor

class PhotoTexturerTest {
    private val box = Shell.build(ShellSpec(widthM = 6f, depthM = 4f, wallM = 3f, roof = Roof.FLAT))
    /** Fine stripes on the front, 10 cm wide: far finer than the 8 cm scan. */
    private fun truth(p: P3): Int = if (p.z > -0.02f) (if (floor(p.x * 10f).toInt() % 2 == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()) else 0xFF808080.toInt()

    /** A photo from [cam]: what it saw, and the depth it measured. */
    private fun shoot(cam: Camera, w: Int, h: Int): Pair<ScanFrame, IntArray> {
        val img = IntArray(w * h)
        val dw = 40; val dh = 30
        val depth = ShortArray(dw * dh)
        val focalPx = cam.focal * h
        for (y in 0 until h) for (x in 0 until w) {
            val dir = cam.ray((x + 0.5f) / w, (y + 0.5f) / h, w.toFloat() / h)
            val d = box.raycast(cam.eye, dir)
            img[y * w + x] = d?.let { truth(cam.eye + dir * it) } ?: 0xFF87CEEB.toInt()
        }
        for (y in 0 until dh) for (x in 0 until dw) {
            val dir = cam.ray((x + 0.5f) / dw, (y + 0.5f) / dh, w.toFloat() / h)
            box.raycast(cam.eye, dir)?.let { d -> depth[y * dw + x] = ((d * (dir dot cam.forward)) * 1000).toInt().toShort() }
        }
        val r = cam.right; val f = cam.forward
        val u = r cross f
        val pose = floatArrayOf(r.x, r.y, r.z, 0f, u.x, u.y, u.z, 0f, -f.x, -f.y, -f.z, 0f, cam.eye.x, cam.eye.y, cam.eye.z, 1f)
        return ScanFrame(pose, focalPx, focalPx, w / 2f, h / 2f, w, h, dw, dh, depth) to img
    }

    @Test
    fun paintsTheScanFromThePhotos() {
        // Scan the box (coarsely), then take a few photos of it.
        val v = TsdfVolume(voxel = 0.08f, truncation = 0.24f)
        for (k in 0..12) {
            val cam = Camera.orbit(P3(0f, 1.5f, -2f), -0.5f + k * 0.08f, 0.15f, 7f, 0.8f)
            val w = 64; val h = 48
            val pts = FloatArray(w * h * 3); val cols = IntArray(w * h); val wts = FloatArray(w * h); var n = 0
            for (y in 0 until h) for (x in 0 until w) {
                val dir = cam.ray((x + 0.5f) / w, (y + 0.5f) / h, w.toFloat() / h)
                val d = box.raycast(cam.eye, dir) ?: continue
                val p = cam.eye + dir * d
                pts[n * 3] = p.x; pts[n * 3 + 1] = p.y; pts[n * 3 + 2] = p.z; cols[n] = truth(p); wts[n] = 1f; n++
            }
            v.integrate(cam.eye, pts, cols, wts, n)
        }
        val part = v.extract().parts.single()
        val shots = listOf(-0.3f, 0f, 0.3f).map { yaw -> shoot(Camera.orbit(P3(0f, 1.5f, 0f), yaw, 0.1f, 6f, 0.9f), 320, 240) }
        // Photos survive being saved.
        val frames = shots.map { (f, _) -> ByteArrayOutputStream().also { f.write(it) }.toByteArray().let { ScanFrame.read(it.inputStream()) } }
        val pages = HashMap<Int, IntArray>()
        val mesh = PhotoTexturer.texture(part, frames, { shots[it].second }, pageSize = 1024, onPage = { i, px -> pages[i] = px })
        assertEquals(part.triangleCount, mesh.triangleCount)
        assertTrue(mesh.parts.all { it.uvs != null && it.texture in pages.keys })
        // Look up the picture at the middle of each front triangle: it shows the stripes.
        var right = 0; var total = 0
        for (p in mesh.parts) for (t in 0 until p.triangleCount) {
            val ids = (0 until 3).map { p.indices[t * 3 + it] }
            val c = ids.map { p.vertex(it) }.reduce { a, b -> a + b } * (1f / 3)
            if (abs(c.z) > 0.03f || c.y !in 0.4f..2.6f || abs(c.x) > 2.6f) continue
            val fx = (c.x * 10f) - floor(c.x * 10f)
            if (fx < 0.25f || fx > 0.75f) continue // Right on a stripe's edge.
            val u = ids.map { p.uvs!![it * 2] }.average().toFloat(); val vv = ids.map { p.uvs!![it * 2 + 1] }.average().toFloat()
            val px = pages.getValue(p.texture)[(vv * 1024).toInt() * 1024 + (u * 1024).toInt()]
            total++
            if (px == truth(c.copy(z = 0f))) right++ else if (((px shr 16) and 0xFF) > 200 == (truth(c.copy(z = 0f)) == 0xFFFF0000.toInt())) right++
        }
        assertTrue("$right of $total", total > 200 && right > total * 0.9)
    }
}
