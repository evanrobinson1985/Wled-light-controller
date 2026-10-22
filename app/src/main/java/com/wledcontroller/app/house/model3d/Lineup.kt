package com.wledcontroller.app.house.model3d

import com.wledcontroller.app.house.MapPoint
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * Works out where the house photo was taken from: four corners of a flat wall on the 3D model
 * and the same four corners on the photo give the camera's position, direction and lens.
 */
object Lineup {
    /** The lens when the photo can't tell us: a phone's main camera (about 24 mm, full-frame equivalent). */
    fun defaultFocal(aspect: Float): Float = 0.5547f * sqrt(aspect * aspect + 1f)

    /**
     * The camera that sees [model] (4 points on one flat surface, in order round it) at [photo]
     * (the same 4 points on a picture [aspect] wide). Null if the points don't make sense.
     */
    fun solve(model: List<P3>, photo: List<MapPoint>, aspect: Float): Camera? {
        if (model.size != 4 || photo.size != 4) return null
        val o = model[0]
        val e1 = (model[1] - o).normalized()
        val e3 = (e1 cross (model[3] - o)).normalized()
        if (e3.length < 0.5f) return null
        val e2 = e3 cross e1
        val plane = model.map { p -> (p - o).let { d -> (d dot e1).toDouble() to (d dot e2).toDouble() } }
        val image = photo.map { p -> ((p.x - 0.5) * aspect) to (0.5 - p.y) }
        val h = homography(plane, image) ?: return null
        val f = focalFrom(h, aspect)
        return pose(h, f, o, e1, e2, e3)
    }

    /** The 3×3 map (row-major, last entry 1) from points [from] to points [to]. */
    fun homography(from: List<Pair<Double, Double>>, to: List<Pair<Double, Double>>): DoubleArray? {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = from[i]; val (u, v) = to[i]
            a[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        val s = solve8(a) ?: return null
        return doubleArrayOf(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], 1.0)
    }

    private fun solve8(a: Array<DoubleArray>): DoubleArray? {
        val n = 8
        for (c in 0 until n) {
            val piv = (c until n).maxByOrNull { abs(a[it][c]) }!!
            if (abs(a[piv][c]) < 1e-12) return null
            val tmp = a[c]; a[c] = a[piv]; a[piv] = tmp
            for (r in 0 until n) if (r != c) {
                val k = a[r][c] / a[c][c]
                if (k != 0.0) for (j in c..n) a[r][j] -= k * a[c][j]
            }
        }
        return DoubleArray(n) { a[it][n] / a[it][it] }
    }

    /** The lens, from the wall's corners being square; the phone default when that can't be told. */
    private fun focalFrom(h: DoubleArray, aspect: Float): Float {
        val h11 = h[0]; val h12 = h[1]; val h21 = h[3]; val h22 = h[4]; val h31 = h[6]; val h32 = h[7]
        val guesses = ArrayList<Double>()
        if (abs(h31 * h32) > 1e-9) guesses += -(h11 * h12 + h21 * h22) / (h31 * h32)
        val d = h31 * h31 - h32 * h32
        if (abs(d) > 1e-9) guesses += -(h11 * h11 + h21 * h21 - h12 * h12 - h22 * h22) / d
        // Only believable lenses (about 20° to 100° top to bottom), and only from a clear view.
        val ok = guesses.filter { it > 0 }.map { sqrt(it) }.filter { it in 0.42..2.8 }
        val def = defaultFocal(aspect)
        if (ok.isEmpty()) return def
        val f = ok.average().toFloat()
        // Nearly straight-on photos say little about the lens; stay near the phone's.
        return if (abs(f - def) / def > 0.6f) def else f
    }

    private fun pose(h: DoubleArray, f: Float, o: P3, e1: P3, e2: P3, e3: P3): Camera? {
        fun col(c: Int) = doubleArrayOf(h[c] / f, h[3 + c] / f, h[6 + c])
        val m1 = col(0); val m2 = col(1); val m3 = col(2)
        fun len(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        var lambda = 2.0 / (len(m1) + len(m2))
        if (m3[2] * lambda < 0) lambda = -lambda
        val r1 = m1.map { it * lambda }; val r2raw = m2.map { it * lambda }; val t = m3.map { it * lambda }
        // Square up the two wall directions.
        val r1n = r1.let { v -> val l = sqrt(v.sumOf { it * it }); v.map { it / l } }
        val dot = (0..2).sumOf { r1n[it] * r2raw[it] }
        val r2n = (0..2).map { r2raw[it] - dot * r1n[it] }.let { v -> val l = sqrt(v.sumOf { it * it }); if (l < 1e-9) return null; v.map { it / l } }
        // The camera's axes (right, up, forward) turn the other way from the house's.
        val r3 = listOf(
            -(r1n[1] * r2n[2] - r1n[2] * r2n[1]),
            -(r1n[2] * r2n[0] - r1n[0] * r2n[2]),
            -(r1n[0] * r2n[1] - r1n[1] * r2n[0]),
        )
        // Camera position in the wall's own coordinates: -Rᵀ t.
        val ca = -(r1n[0] * t[0] + r1n[1] * t[1] + r1n[2] * t[2])
        val cb = -(r2n[0] * t[0] + r2n[1] * t[1] + r2n[2] * t[2])
        val cc = -(r3[0] * t[0] + r3[1] * t[1] + r3[2] * t[2])
        fun world(a: Double, b: Double, c: Double) = e1 * a.toFloat() + e2 * b.toFloat() + e3 * c.toFloat()
        val eye = o + world(ca, cb, cc)
        val up = world(r1n[1], r2n[1], r3[1])
        val forward = world(r1n[2], r2n[2], r3[2])
        return Camera(eye, forward.normalized(), up.normalized(), f)
    }

    /** The camera's view, top to bottom, in degrees. */
    fun fieldOfView(c: Camera) = Math.toDegrees(2.0 * atan(0.5 / c.focal)).toFloat()
}
