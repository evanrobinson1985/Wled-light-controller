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
    /** Photos kept from scanning with the phone, for sharpening the colours. */
    val photos: Int = 0,
    /** Painted from those photos (rather than the scan's own colours). */
    val sharpened: Boolean = false,
    /** Cropping, erasing and tidying done to it, in order; the original is kept, so they can be undone. */
    val edits: List<MeshEdit> = emptyList(),
) {
    @Serializable
    enum class Kind { PHOTOS, SCAN }
}

/** One run's place on the model, saved with an exported model. */
@Serializable
data class SavedPlacement(val id: String, val name: String, val points3d: List<P3>)

/**
 * Where the lights go on a model, saved inside an exported model file so loading it again puts
 * everything back: the line-up with the house photo, and each run traced on the model.
 */
@Serializable
data class ModelLights(
    val lineup: Camera? = null,
    val lineupModel: List<P3>? = null,
    val lineupPhoto: List<MapPoint>? = null,
    /** The house photo's shape when it was lined up: a different photo needs lining up again. */
    val photoAspect: Float? = null,
    val runs: List<SavedPlacement> = emptyList(),
) {
    /** [map] with [model] in place and the lights put back: runs matched by id, or else by name. */
    fun applyTo(map: HouseMap, model: House3D): HouseMap {
        val samePhoto = map.photoPath != null && photoAspect != null && kotlin.math.abs(map.aspect - photoAspect) < 0.01f
        val placed = if (lineup != null && samePhoto) model.copy(lineup = lineup, lineupModel = lineupModel, lineupPhoto = lineupPhoto) else model
        val byName = runs.groupBy { it.name }
        return map.copy(
            model3d = placed,
            runs = map.runs.map { r ->
                val saved = runs.firstOrNull { it.id == r.id } ?: byName[r.name]?.singleOrNull()
                r.copy(points3d = saved?.points3d)
            },
        )
    }

    /** How much came back, for saying so. */
    fun summary(map: HouseMap): String {
        val lined = lineup != null && map.model3d?.lineup != null
        val traced = map.runs.count { it.points3d != null }
        return listOfNotNull(
            if (lined) "lined up with your house photo" else if (lineup != null) "line it up again (the house photo has changed)" else null,
            if (traced > 0) "$traced run${if (traced == 1) "" else "s"} traced on it" else null,
        ).joinToString(", ")
    }

    companion object {
        fun of(map: HouseMap, model: House3D) = ModelLights(
            model.lineup, model.lineupModel, model.lineupPhoto, map.aspect.takeIf { model.lineup != null },
            map.runs.mapNotNull { r -> r.points3d?.let { SavedPlacement(r.id, r.name, it) } },
        )
    }
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
        val rawHits = mesh.castFrom(cam, map.aspect, fromPhoto.map { leds[it].x to leds[it].y })
        // A string hangs on one surface: each light's depth is steadied against its neighbours',
        // so a ray slipping through a hole in the scan or catching a stray bit doesn't pull it off.
        val hits = arrayOfNulls<P3>(fromPhoto.size)
        fromPhoto.indices.groupBy { leds[fromPhoto[it]].runIndex }.values.forEach { ks ->
            val inRun = ks.sortedBy { leds[fromPhoto[it]].orderInRun }
            val depths = steadyDepths(inRun.map { k -> rawHits[k]?.let { (it - cam.eye).length } })
            inRun.forEachIndexed { j, k ->
                val l = leds[fromPhoto[k]]
                hits[k] = depths[j]?.let { t -> cam.eye + cam.ray(l.x, l.y, map.aspect) * t }
            }
        }
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

    /**
     * Steadies the distances to a run's lights (in order along it; null where the model was
     * missed): each one goes to its neighbourhood's distance, leaning to the nearer surface (lights
     * hang on the front of things), unless it's already close to that. Misses get it too.
     */
    fun steadyDepths(depths: List<Float?>, near: Float = 0.35f): List<Float?> {
        val all = depths.withIndex().mapNotNull { (j, d) -> d?.let { j to it } }
        if (all.isEmpty()) return depths
        val half = 10
        return depths.indices.map { i ->
            val around = (maxOf(0, i - half)..minOf(depths.lastIndex, i + half)).mapNotNull { j -> depths[j]?.let { j to it } }
            // Too few hits nearby to judge: the whole run's.
            val pool = if (around.size >= 3) around else all.takeIf { it.size <= 4 * half } ?: all.filterIndexed { k, _ -> k % (all.size / (4 * half) + 1) == 0 }
            // The string may run away from the camera (along the side of the house): follow its
            // slope (the middle of the slopes between pairs, which stray hits barely move).
            val slopes = ArrayList<Float>()
            for (a in pool.indices) for (b in a + 1 until pool.size) {
                val (ja, da) = pool[a]; val (jb, db) = pool[b]
                slopes += (db - da) / (jb - ja)
            }
            val slope = if (slopes.isEmpty()) 0f else slopes.sorted()[slopes.size / 2]
            val atI = pool.map { (j, d) -> d - slope * (j - i) }.sorted()
            val typical = atI[((atI.size - 1) * near).toInt()]
            val d = depths[i]
            if (d != null && kotlin.math.abs(d - typical) <= maxOf(0.25f, typical * 0.04f)) d else typical
        }
    }

    /** [run]'s LEDs along a line traced on the model: every [LightRun.spacingCm], or spread end to end. */
    fun along(traced: List<P3>, run: LightRun): List<P3> {
        val n = run.count
        val points = droop(traced, run.sag, run.hooksBetween)
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

    /** A line hung between hooks on the model: extra hooks put in, and each span drooping straight down by [sag] of its length. */
    fun droop(points: List<P3>, sag: Float, hooksBetween: Int, steps: Int = 12): List<P3> {
        if (points.size < 2 || (sag <= 0f && hooksBetween <= 0)) return points
        val out = ArrayList<P3>()
        points.zipWithNext().forEach { (a, b) ->
            val hooks = (0..hooksBetween).map { j -> a + (b - a) * (j / (hooksBetween + 1f)) } + b
            hooks.zipWithNext().forEach { (p, q) ->
                val drop = sag * (q - p).length
                val n = if (drop > 0f) steps else 1
                for (s in 0 until n) { val f = s / n.toFloat(); out += p + (q - p) * f - P3.UP * (4f * drop * f * (1f - f)) }
            }
        }
        out += points.last()
        return out
    }

    /** Spots on the house photo for points on the model, through the line-up (for runs traced only on the model). */
    fun toPhoto(points: List<P3>, cam: Camera, aspect: Float): List<MapPoint>? =
        points.map { p -> cam.project(p, aspect)?.let { (x, y, _) -> MapPoint(x, y) } ?: return null }
}
