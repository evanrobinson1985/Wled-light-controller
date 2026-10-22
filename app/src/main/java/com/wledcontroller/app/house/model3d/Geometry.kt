package com.wledcontroller.app.house.model3d

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point or direction in the house's 3D space, in metres: x across, y up, z out of the front of the house. */
@Serializable
data class P3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: P3) = P3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: P3) = P3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = P3(x * s, y * s, z * s)
    infix fun dot(o: P3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: P3) = P3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length get() = sqrt(dot(this))
    fun normalized(): P3 = length.let { if (it > 1e-9f) P3(x / it, y / it, z / it) else this }

    companion object {
        val ZERO = P3(0f, 0f, 0f)
        val UP = P3(0f, 1f, 0f)
    }
}

/**
 * A pinhole camera, like the phone's when it took a photo. [focal] is the focal length in
 * picture heights. Pictures are measured as on the house map: 0..1 across and down.
 */
@Serializable
data class Camera(val eye: P3, val forward: P3, val up: P3, val focal: Float) {
    val right: P3 get() = (forward cross up).normalized()
    private val trueUp: P3 get() = (right cross forward).normalized()

    /** The direction from the eye through ([sx], [sy]) on a picture [aspect] (width / height) wide. */
    fun ray(sx: Float, sy: Float, aspect: Float): P3 {
        val f = forward.normalized()
        return (f * focal + right * ((sx - 0.5f) * aspect) + trueUp * (0.5f - sy)).normalized()
    }

    /** Where [p] lands on the picture (x, y) and how far in front of the camera it is; null if behind it. */
    fun project(p: P3, aspect: Float): Triple<Float, Float, Float>? {
        val d = p - eye
        val z = d dot forward.normalized()
        if (z <= 1e-4f) return null
        val x = focal * (d dot right) / z
        val y = focal * (d dot trueUp) / z
        return Triple(x / aspect + 0.5f, 0.5f - y, z)
    }

    /** Column-major view-projection matrix for OpenGL, for a screen [aspect] wide. */
    fun viewProjection(aspect: Float, near: Float, far: Float): FloatArray {
        val f = forward.normalized(); val r = right; val u = trueUp
        val view = floatArrayOf(
            r.x, u.x, -f.x, 0f,
            r.y, u.y, -f.y, 0f,
            r.z, u.z, -f.z, 0f,
            -(r dot eye), -(u dot eye), f dot eye, 1f,
        )
        val g = 2f * focal
        val proj = floatArrayOf(
            g / aspect, 0f, 0f, 0f,
            0f, g, 0f, 0f,
            0f, 0f, (far + near) / (near - far), -1f,
            0f, 0f, 2f * far * near / (near - far), 0f,
        )
        return multiply(proj, view)
    }

    companion object {
        /** Looking at [target] from [dist] away, turned [yaw] round and tilted [pitch] down (radians). */
        fun orbit(target: P3, yaw: Float, pitch: Float, dist: Float, focal: Float = 1.2f): Camera {
            val eye = target + P3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch)) * dist
            return Camera(eye, (target - eye).normalized(), P3.UP, focal)
        }

        fun multiply(a: FloatArray, b: FloatArray): FloatArray {
            val out = FloatArray(16)
            for (c in 0 until 4) for (r in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
                out[c * 4 + r] = s
            }
            return out
        }
    }
}

/**
 * One piece of a model: triangles with picture coordinates (u right, v down) into texture
 * [texture], or a colour at each corner ([vertexColors], as from a scan), or flat [color].
 */
class MeshPart(
    val positions: FloatArray,
    val uvs: FloatArray?,
    val indices: IntArray,
    val texture: Int = -1,
    val color: Int = 0xFF8A8F99.toInt(),
    val vertexColors: IntArray? = null,
) {
    val vertexCount get() = positions.size / 3
    val triangleCount get() = indices.size / 3
    fun vertex(i: Int) = P3(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])
}

/** A 3D model of the house. */
class Mesh(val parts: List<MeshPart>) {
    val triangleCount get() = parts.sumOf { it.triangleCount }

    /** The smallest and largest corner of the box round it. */
    val bounds: Pair<P3, P3> by lazy {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (p in parts) {
            val a = p.positions
            var i = 0
            while (i < a.size) {
                minX = minOf(minX, a[i]); maxX = maxOf(maxX, a[i])
                minY = minOf(minY, a[i + 1]); maxY = maxOf(maxY, a[i + 1])
                minZ = minOf(minZ, a[i + 2]); maxZ = maxOf(maxZ, a[i + 2])
                i += 3
            }
        }
        if (minX > maxX) P3.ZERO to P3.ZERO else P3(minX, minY, minZ) to P3(maxX, maxY, maxZ)
    }

    /** Every point moved by [f]. */
    fun transformed(f: (P3) -> P3): Mesh = Mesh(parts.map { p ->
        val out = FloatArray(p.positions.size)
        for (i in 0 until p.vertexCount) { val q = f(p.vertex(i)); out[i * 3] = q.x; out[i * 3 + 1] = q.y; out[i * 3 + 2] = q.z }
        MeshPart(out, p.uvs, p.indices, p.texture, p.color, p.vertexColors)
    })

    /** Where a ray from [origin] along [dir] first meets the model, as a distance along it. */
    fun raycast(origin: P3, dir: P3): Float? {
        var best = Float.MAX_VALUE
        for (p in parts) for (t in 0 until p.triangleCount) {
            val d = hitTriangle(p, t, origin, dir) ?: continue
            if (d < best) best = d
        }
        return best.takeIf { it < Float.MAX_VALUE }
    }

    /**
     * Where rays from [camera] through many points of its picture ([points], as x / y pairs)
     * meet the model. Much faster than one [raycast] each: triangles are sorted into a grid over
     * the picture first.
     */
    fun castFrom(camera: Camera, aspect: Float, points: List<Pair<Float, Float>>): List<P3?> {
        val n = GRID
        val cells = Array(n * n) { IntArrayList() }
        // Each vertex's spot on the picture, once.
        val projected = parts.map { p ->
            val xy = FloatArray(p.vertexCount * 2)
            val ok = BooleanArray(p.vertexCount)
            for (i in 0 until p.vertexCount) {
                camera.project(p.vertex(i), aspect)?.let { (x, y, _) -> xy[i * 2] = x; xy[i * 2 + 1] = y; ok[i] = true }
            }
            xy to ok
        }
        val starts = IntArray(parts.size)
        for (k in 1 until parts.size) starts[k] = starts[k - 1] + parts[k - 1].triangleCount
        for ((k, p) in parts.withIndex()) {
            val (xy, ok) = projected[k]
            for (t in 0 until p.triangleCount) {
                val a = p.indices[t * 3]; val b = p.indices[t * 3 + 1]; val c = p.indices[t * 3 + 2]
                // Partly behind the camera: test it everywhere (rare; near the photographer).
                if (!ok[a] || !ok[b] || !ok[c]) { cells.forEach { it.add(starts[k] + t) }; continue }
                val x0 = minOf(xy[a * 2], xy[b * 2], xy[c * 2]); val x1 = maxOf(xy[a * 2], xy[b * 2], xy[c * 2])
                val y0 = minOf(xy[a * 2 + 1], xy[b * 2 + 1], xy[c * 2 + 1]); val y1 = maxOf(xy[a * 2 + 1], xy[b * 2 + 1], xy[c * 2 + 1])
                if (x1 < 0f || y1 < 0f || x0 > 1f || y0 > 1f) continue
                val cx0 = (x0 * n).toInt().coerceIn(0, n - 1); val cx1 = (x1 * n).toInt().coerceIn(0, n - 1)
                val cy0 = (y0 * n).toInt().coerceIn(0, n - 1); val cy1 = (y1 * n).toInt().coerceIn(0, n - 1)
                for (cy in cy0..cy1) for (cx in cx0..cx1) cells[cy * n + cx].add(starts[k] + t)
            }
        }
        return points.map { (sx, sy) ->
            if (sx < 0f || sy < 0f || sx > 1f || sy > 1f) return@map null
            val dir = camera.ray(sx, sy, aspect)
            val cell = cells[(sy * n).toInt().coerceIn(0, n - 1) * n + (sx * n).toInt().coerceIn(0, n - 1)]
            var best = Float.MAX_VALUE
            for (j in 0 until cell.size) {
                val g = cell[j]
                var k = 0
                while (k < parts.size - 1 && g >= starts[k + 1]) k++
                val d = hitTriangle(parts[k], g - starts[k], camera.eye, dir) ?: continue
                if (d < best) best = d
            }
            if (best < Float.MAX_VALUE) camera.eye + dir * best else null
        }
    }

    private fun hitTriangle(p: MeshPart, t: Int, o: P3, d: P3): Float? {
        val pos = p.positions
        val ia = p.indices[t * 3] * 3; val ib = p.indices[t * 3 + 1] * 3; val ic = p.indices[t * 3 + 2] * 3
        // Möller–Trumbore, both sides.
        val e1x = pos[ib] - pos[ia]; val e1y = pos[ib + 1] - pos[ia + 1]; val e1z = pos[ib + 2] - pos[ia + 2]
        val e2x = pos[ic] - pos[ia]; val e2y = pos[ic + 1] - pos[ia + 1]; val e2z = pos[ic + 2] - pos[ia + 2]
        val px = d.y * e2z - d.z * e2y; val py = d.z * e2x - d.x * e2z; val pz = d.x * e2y - d.y * e2x
        val det = e1x * px + e1y * py + e1z * pz
        if (abs(det) < 1e-10f) return null
        val inv = 1f / det
        val tx = o.x - pos[ia]; val ty = o.y - pos[ia + 1]; val tz = o.z - pos[ia + 2]
        val u = (tx * px + ty * py + tz * pz) * inv
        if (u < -EDGE || u > 1f + EDGE) return null
        val qx = ty * e1z - tz * e1y; val qy = tz * e1x - tx * e1z; val qz = tx * e1y - ty * e1x
        val v = (d.x * qx + d.y * qy + d.z * qz) * inv
        if (v < -EDGE || u + v > 1f + EDGE) return null
        val dist = (e2x * qx + e2y * qy + e2z * qz) * inv
        return dist.takeIf { it > 1e-4f }
    }

    private class IntArrayList {
        var data = IntArray(4); var size = 0
        fun add(v: Int) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = v }
        operator fun get(i: Int) = data[i]
    }

    companion object {
        private const val GRID = 64
        /** Lights sit right on edges (eaves, corners): don't let them slip between two triangles. */
        private const val EDGE = 1e-3f
    }
}
