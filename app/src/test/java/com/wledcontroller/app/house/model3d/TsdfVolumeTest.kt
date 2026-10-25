package com.wledcontroller.app.house.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.random.Random

class TsdfVolumeTest {
    /** A 6 × 3 × 4 m box (front face red, the rest grey) sitting on the ground at the origin. */
    private val box = Shell.build(ShellSpec(widthM = 6f, depthM = 4f, wallM = 3f, roof = Roof.FLAT))

    private fun colorAt(p: P3) = if (p.z > -0.01f) 0xFFFF0000.toInt() else 0xFF808080.toInt()

    /** What a depth camera at [cam] would measure, with a little noise. */
    private fun scan(v: TsdfVolume, cam: Camera, rnd: Random) {
        val w = 64; val h = 48
        val pts = FloatArray(w * h * 3); val cols = IntArray(w * h); val wts = FloatArray(w * h)
        var n = 0
        for (y in 0 until h) for (x in 0 until w) {
            val dir = cam.ray((x + 0.5f) / w, (y + 0.5f) / h, w.toFloat() / h)
            val d = box.raycast(cam.eye, dir) ?: continue
            val noisy = d + (rnd.nextFloat() - 0.5f) * 0.03f
            val p = cam.eye + dir * noisy
            pts[n * 3] = p.x; pts[n * 3 + 1] = p.y; pts[n * 3 + 2] = p.z
            cols[n] = colorAt(cam.eye + dir * d); wts[n] = 1f; n++
        }
        v.integrate(cam.eye, pts, cols, wts, n)
    }

    @Test
    fun buildsTheSurfaceItWasShownInColour() {
        val v = TsdfVolume(voxel = 0.08f, truncation = 0.24f)
        val rnd = Random(1)
        // Walking along the front and round the right side.
        for (k in 0..16) {
            val yaw = -0.6f + k * 0.12f
            scan(v, Camera.orbit(P3(0f, 1.5f, -2f), yaw, 0.15f, 7f, 0.8f), rnd)
        }
        val mesh = v.extract()
        val part = mesh.parts.single()
        assertTrue("triangles: ${part.triangleCount}", part.triangleCount > 1000)
        // Every corner of the mesh lies on the box's surface (within a cube).
        var far = 0
        for (i in 0 until part.vertexCount) {
            val p = part.vertex(i)
            val dFace = minOf(abs(p.z), abs(p.z + 4f), abs(p.x - 3f), abs(p.x + 3f), abs(p.y - 3f), abs(p.y))
            if (dFace > 0.08f) far++
        }
        assertTrue("$far of ${part.vertexCount} off the surface", far < part.vertexCount / 50)
        // The front is red where it was seen.
        val front = (0 until part.vertexCount).filter { val p = part.vertex(it); abs(p.z) < 0.04f && abs(p.x) < 2.5f && p.y in 0.5f..2.5f }
        assertTrue(front.size > 100)
        assertTrue(front.count { part.vertexColors!![it] == 0xFFFF0000.toInt() } > front.size * 0.9)
        // Lines up with a ray cast like any model.
        assertEquals(10f, mesh.raycast(P3(0f, 1.5f, 10f), P3(0f, 0f, -1f))!!, 0.08f)
        // Saved and read back with its colours.
        val bytes = ByteArrayOutputStream().also { MeshFile.write(mesh, it) }.toByteArray()
        assertTrue(MeshFile.read(bytes.inputStream()).parts.single().vertexColors!!.contentEquals(part.vertexColors!!))
        // Progress points are on the surface too.
        val (pts, cols) = v.surfacePoints(5000)
        assertTrue(cols.size > 500 && pts.size == cols.size * 3)
    }

    @Test
    fun dropsStrayBits() {
        val v = TsdfVolume(voxel = 0.08f, truncation = 0.24f)
        // One lone measurement far from anything else.
        v.integrate(P3(0f, 0f, 0f), floatArrayOf(0f, 0f, -5f), intArrayOf(0xFFFFFF), floatArrayOf(1f), 1)
        assertEquals(0, v.extract().triangleCount)
    }

    @Test
    fun aFullScanStopsGrowingInsteadOfRunningOutOfMemory() {
        val v = TsdfVolume(voxel = 0.03f, truncation = 0.1f).apply { maxBlocks = 50 }
        val n = 4000
        val pts = FloatArray(n * 3); val cols = IntArray(n) { 0xFFFFFF }; val wts = FloatArray(n) { 1f }
        val r = Random(1)
        for (i in 0 until n) { pts[i * 3] = r.nextFloat() * 20f - 10f; pts[i * 3 + 1] = r.nextFloat() * 5f; pts[i * 3 + 2] = -5f }
        v.integrate(P3(0f, 2f, 5f), pts, cols, wts, n)
        assertEquals(50, v.blockCount)
        assertTrue(v.full)
    }
}
