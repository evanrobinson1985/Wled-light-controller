package com.wledcontroller.app.house.model3d

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns one view's depth into points for the scan, with how much to trust each: ARCore's
 * confident ("raw") depth where it has it, its smoothed depth to fill the gaps at low trust,
 * and less trust for surfaces seen at a slant. Points on the edge of a jump in depth (the
 * outline of a post against the wall behind it) are left out: they're usually half one and
 * half the other.
 */
object DepthFusion {
    /** What came out of one view. [count] points; [medianM] how far away things mostly were. */
    class Result(val points: FloatArray, val colors: IntArray, val weights: FloatArray, val count: Int, val medianM: Float)

    const val NEAR_MM = 300
    const val FAR_MM = 6500
    /** Trust in the smoothed depth (a guess between confident points). */
    const val FILL_TRUST = 0.12f

    /**
     * [raw] and [conf] (0..255) are [dw] × [dh]; [smooth] (or null) is [sw] × [sh]. Depth in
     * mm, 0 = none. [fx] etc. are the lens for the depth picture's size; [pose] places the
     * camera in the world (OpenGL style, looking down its -z). [color] gives the colour at a
     * depth pixel. [split] > 1 spreads each pixel into split × split points over its footprint
     * (its depth following the surface's slant), so cubes finer than the depth pixels are all
     * reached rather than every other one.
     */
    fun fuse(
        dw: Int, dh: Int, raw: ShortArray, conf: ByteArray,
        smooth: ShortArray?, sw: Int, sh: Int,
        fx: Float, fy: Float, cx: Float, cy: Float, pose: FloatArray,
        split: Int = 1,
        color: (Int, Int) -> Int,
    ): Result {
        val n = dw * dh
        // The depth to use at each pixel, and how much to trust it.
        val depth = FloatArray(n); val trust = FloatArray(n)
        for (r in 0 until dh) for (c in 0 until dw) {
            val k = r * dw + c
            val mm = raw[k].toInt() and 0xFFFF
            val cf = conf[k].toInt() and 0xFF
            if (mm in NEAR_MM..FAR_MM && cf >= 15) {
                depth[k] = mm / 1000f
                trust[k] = Math.pow(cf / 255.0, 1.2).toFloat()
            } else if (smooth != null) {
                val sm = smooth[((r * sh / dh).coerceIn(0, sh - 1)) * sw + (c * sw / dw).coerceIn(0, sw - 1)].toInt() and 0xFFFF
                if (sm in NEAR_MM..FAR_MM) { depth[k] = sm / 1000f; trust[k] = FILL_TRUST }
            }
        }
        val s = split.coerceIn(1, 4)
        val pts = FloatArray(n * s * s * 3); val cols = IntArray(n * s * s); val wts = FloatArray(n * s * s)
        val seen = FloatArray(n * s * s)
        var count = 0
        fun camPoint(c: Float, r: Float, z: Float) = P3((c + 0.5f - cx) / fx * z, -(r + 0.5f - cy) / fy * z, -z)
        fun camPoint(c: Int, r: Int, z: Float) = camPoint(c.toFloat(), r.toFloat(), z)
        for (r in 0 until dh) for (c in 0 until dw) {
            val k = r * dw + c
            val z = depth[k]
            if (z <= 0f) continue
            // Neighbours across and down (or back and up at the edges), for the surface's slant.
            val cn = if (c + 1 < dw) c + 1 else c - 1
            val rn = if (r + 1 < dh) r + 1 else r - 1
            if (cn < 0 || rn < 0) continue
            val za = depth[r * dw + cn]; val zb = depth[rn * dw + c]
            if (za <= 0f || zb <= 0f) continue
            // On an edge between near and far: neither, really.
            if (abs(za - z) > 0.06f + 0.04f * z || abs(zb - z) > 0.06f + 0.04f * z) continue
            val p = camPoint(c, r, z)
            val normal = ((camPoint(cn, r, za) - p) cross (camPoint(c, rn, zb) - p)).normalized()
            val facing = abs(normal dot p.normalized())
            if (facing < 0.2f) continue
            val col = color(c, r)
            // Depth changes this much per pixel across and down (toward the neighbours used above).
            val dzc = (za - z) * (if (cn > c) 1f else -1f); val dzr = (zb - z) * (if (rn > r) 1f else -1f)
            for (i in 0 until s) for (j in 0 until s) {
                val q = if (s == 1) p else {
                    val oc = (i + 0.5f) / s - 0.5f; val orow = (j + 0.5f) / s - 0.5f
                    camPoint(c + oc, r + orow, z + dzc * oc + dzr * orow)
                }
                val x = q.x; val y = q.y; val zz = q.z
                pts[count * 3] = pose[0] * x + pose[4] * y + pose[8] * zz + pose[12]
                pts[count * 3 + 1] = pose[1] * x + pose[5] * y + pose[9] * zz + pose[13]
                pts[count * 3 + 2] = pose[2] * x + pose[6] * y + pose[10] * zz + pose[14]
                cols[count] = col
                wts[count] = trust[k] * facing
                seen[count] = z
                count++
            }
        }
        val median = if (count == 0) 0f else seen.copyOf(count).also { it.sort() }[count / 2]
        return Result(pts, cols, wts, count, median)
    }

    /** What to tell the person scanning, from how far away things mostly are. */
    fun distanceHint(medianM: Float): String? = when {
        medianM <= 0f -> null
        medianM > 4.5f -> "Move closer — about 2 to 4 m (6 to 12 ft) from the house is best."
        medianM < 0.6f -> "Too close — step back a little."
        else -> null
    }

    /** Whether the phone jumped between two views (tracking caught up with itself), so the next moment's views can't be trusted. */
    fun jumped(a: FloatArray, b: FloatArray, seconds: Float): Boolean {
        val moved = sqrt((a[12] - b[12]).let { it * it } + (a[13] - b[13]).let { it * it } + (a[14] - b[14]).let { it * it })
        val facing = a[8] * b[8] + a[9] * b[9] + a[10] * b[10]
        // Faster than anyone walks while scanning, or turned more than about 35° per second.
        val s = seconds.coerceAtLeast(0.02f)
        return moved / s > 2.5f || (1f - facing) / s > 0.9f
    }
}
