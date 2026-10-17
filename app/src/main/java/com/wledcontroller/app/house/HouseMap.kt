package com.wledcontroller.app.house

import kotlinx.serialization.Serializable
import kotlin.math.hypot

/** A point on the house photo, normalised: x and y run 0..1 across the photo's width and height. */
@Serializable
data class MapPoint(val x: Float, val y: Float)

/**
 * One physical run of lights (a roofline, a window, a bush …) on the photo.
 * Either traced as a line ([points]: the first point is LED [startLed], LEDs spread evenly
 * along it) or placed LED by LED ([ledPoints], from a camera scan or manual editing).
 */
@Serializable
data class LightRun(
    val id: String,
    val name: String,
    val deviceId: String,
    val startLed: Int,
    val count: Int,
    val points: List<MapPoint> = emptyList(),
    /** Exact position of each LED in the run; null = not placed (stays dark). Overrides [points]. */
    val ledPoints: List<MapPoint?>? = null,
    /** What kind of prop this run is; sets the natural direction effects travel in. */
    val prop: PropType = PropType.LINE,
) {
    val endLed get() = startLed + count
    val isPlacedPerLed get() = ledPoints != null
    val placedCount get() = ledPoints?.count { it != null } ?: if (points.size >= 2) count else 0
}

@Serializable
data class HouseMap(
    /** Path of the downscaled photo in app storage, or null for a plain dark canvas. */
    val photoPath: String? = null,
    /** Photo width / height. */
    val aspect: Float = 1.5f,
    val runs: List<LightRun> = emptyList(),
    val groups: List<PropGroup> = emptyList(),
)

/** A named set of props (e.g. "All arches") that sequences can target together. */
@Serializable
data class PropGroup(val id: String, val name: String, val runIds: List<String>)

/** How an effect's motion is laid out over the lights it covers. */
@Serializable
enum class Direction(val label: String) {
    AUTO("Prop default"),
    ALONG("Along"),
    REVERSE("Reverse"),
    ENDS_IN("Ends → middle"),
    CENTER_OUT("Centre out"),
    LEFT_RIGHT("Left → right"),
    RIGHT_LEFT("Right → left"),
    UP("Bottom → top"),
    DOWN("Top → bottom"),
}

@Serializable
enum class PropType(val label: String, val natural: Direction) {
    LINE("Line / roofline", Direction.ALONG),
    ARCH("Arch", Direction.ALONG),
    TREE("Tree", Direction.UP),
    WINDOW("Window / outline", Direction.ALONG),
    CANDY_CANE("Candy cane", Direction.ALONG),
    STAR("Star", Direction.CENTER_OUT),
    MATRIX("Matrix / panel", Direction.LEFT_RIGHT),
    BUSH("Bush / cluster", Direction.CENTER_OUT),
}

/** A placed LED: which device and physical index, where it is, and its order within its run. */
data class PlacedLed(val deviceId: String, val index: Int, val x: Float, val y: Float, val runIndex: Int, val orderInRun: Int)

/** Position of every LED in the run (index = order in run); null where an LED isn't placed. */
fun LightRun.ledPositions(aspect: Float): List<MapPoint?> {
    ledPoints?.let { lp -> return List(count) { lp.getOrNull(it) } }
    return tracedPositions(aspect)
}

/** Spreads [LightRun.count] LEDs evenly by distance along the traced line (in photo-proportional space). */
fun LightRun.tracedPositions(aspect: Float): List<MapPoint> {
    if (count <= 0 || points.isEmpty()) return emptyList()
    if (points.size == 1 || count == 1) return List(count) { points.first() }
    // Measure in a space where x and y have the same scale so diagonal runs aren't distorted.
    val seg = points.zipWithNext { a, b -> hypot(((b.x - a.x) * aspect).toDouble(), (b.y - a.y).toDouble()) }
    val total = seg.sum().takeIf { it > 0 } ?: return List(count) { points.first() }
    return List(count) { i ->
        var d = total * i / (count - 1)
        var k = 0
        while (k < seg.size - 1 && d > seg[k]) { d -= seg[k]; k++ }
        val f = if (seg[k] > 0) (d / seg[k]).toFloat().coerceIn(0f, 1f) else 0f
        val a = points[k]; val b = points[k + 1]
        MapPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }
}

fun HouseMap.placedLeds(): List<PlacedLed> = runs.flatMapIndexed { r, run ->
    run.ledPositions(aspect).mapIndexedNotNull { i, p -> p?.let { PlacedLed(run.deviceId, run.startLed + i, it.x, it.y, r, i) } }
}

/** Fills unplaced LEDs by interpolating between the nearest placed LEDs on either side. */
fun List<MapPoint?>.fillGaps(): List<MapPoint?> {
    val placed = indices.filter { this[it] != null }
    if (placed.size < 2) return this
    return List(size) { i ->
        this[i] ?: run {
            val before = placed.lastOrNull { it < i }
            val after = placed.firstOrNull { it > i }
            when {
                before != null && after != null -> {
                    val a = this[before]!!; val b = this[after]!!
                    val f = (i - before).toFloat() / (after - before)
                    MapPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                }
                else -> null // Outside the detected span: leave for the user to place.
            }
        }
    }
}
