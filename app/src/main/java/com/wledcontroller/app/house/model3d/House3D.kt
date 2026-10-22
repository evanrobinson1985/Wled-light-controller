package com.wledcontroller.app.house.model3d

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PlacedLed
import kotlinx.serialization.Serializable

/**
 * The house as a 3D model (optional): built from photos of its sides ([shell]) or imported
 * from a 3D scan. Its files live in the app's storage under [dir]. Once it's lined up with the
 * house photo ([lineup]), every light on the map has a place on it.
 */
@Serializable
data class House3D(
    val dir: String,
    val kind: Kind,
    val name: String = "",
    /** Use it for the 3D views. */
    val show: Boolean = true,
    val shell: ShellSpec? = null,
    val faces: List<FacePhoto> = emptyList(),
    /** Where the house photo was taken from, in the model's space. */
    val lineup: Camera? = null,
    /** The four points on the model used to line it up (top-left, top-right, bottom-right, bottom-left). */
    val lineupModel: List<P3>? = null,
    /** The same four points on the house photo. */
    val lineupPhoto: List<MapPoint>? = null,
    val triangles: Int = 0,
) {
    @Serializable
    enum class Kind { PHOTOS, SCAN }
}

/** Where each light is on the 3D model. */
object Led3D {
    /**
     * A spot for each of [leds] (null where there isn't one): runs traced on the model follow
     * their tracing; the rest are found from the photo through the line-up. Yard props on a
     * house built from photos stand out in front of the wall by their distance.
     */
    fun positions(map: HouseMap, leds: List<PlacedLed>, model: House3D, mesh: Mesh): List<P3?> {
        val out = arrayOfNulls<P3>(leds.size)
        // Traced on the model.
        val traced = map.runs.map { r -> r.points3d?.takeIf { it.isNotEmpty() }?.let { along(it, r) } }
        val fromPhoto = ArrayList<Int>()
        leds.forEachIndexed { i, l ->
            val t = traced.getOrNull(l.runIndex)
            if (t != null) out[i] = t.getOrNull(l.orderInRun) else fromPhoto += i
        }
        val cam = model.lineup ?: return out.toList()
        val hits = mesh.castFrom(cam, map.aspect, fromPhoto.map { leds[it].x to leds[it].y })
        val plane = model.lineupModel?.takeIf { it.size >= 3 }
        val normal = plane?.let { ((it[1] - it[0]) cross (it[3 % it.size] - it[0])).normalized() }
        fromPhoto.forEachIndexed { k, i ->
            val l = leds[i]
            val dir = cam.ray(l.x, l.y, map.aspect)
            // Missed the model: onto the wall's plane.
            var p = hits[k] ?: if (plane != null && normal != null) {
                val denom = dir dot normal
                if (kotlin.math.abs(denom) > 1e-4f) cam.eye + dir * (((plane[0] - cam.eye) dot normal) / denom) else null
            } else null
            val depth = (map.runs.getOrNull(l.runIndex)?.depthCm ?: 0f) / 100f
            if (p != null && depth > 0f && model.kind == House3D.Kind.PHOTOS && normal != null) {
                // Toward the camera until it's that far out from the wall.
                p = p - dir * (depth / kotlin.math.abs(dir dot normal).coerceAtLeast(0.2f))
            }
            out[i] = p
        }
        return out.toList()
    }

    /** [run]'s LEDs along a line traced on the model: every [LightRun.spacingCm], or spread end to end. */
    fun along(points: List<P3>, run: LightRun): List<P3> {
        val n = run.count
        if (n <= 0) return emptyList()
        if (points.size == 1 || n == 1) return List(n) { points[0] }
        val segs = points.zipWithNext { a, b -> (b - a).length }
        val total = segs.sum()
        val step = run.spacingCm?.takeIf { it > 0f }?.let { it / 100f } ?: (total / (n - 1))
        return List(n) { i ->
            var d = step * i
            var k = 0
            while (k < segs.size - 1 && d > segs[k]) { d -= segs[k]; k++ }
            val f = if (segs[k] > 0f) d / segs[k] else 0f
            points[k] + (points[k + 1] - points[k]) * f
        }
    }

    /** Spots on the house photo for points on the model, through the line-up (for runs traced only on the model). */
    fun toPhoto(points: List<P3>, cam: Camera, aspect: Float): List<MapPoint>? =
        points.map { p -> cam.project(p, aspect)?.let { (x, y, _) -> MapPoint(x, y) } ?: return null }
}
