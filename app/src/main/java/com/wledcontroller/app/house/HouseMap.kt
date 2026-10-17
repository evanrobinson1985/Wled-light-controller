package com.wledcontroller.app.house

import kotlinx.serialization.Serializable
import kotlin.math.hypot

/** A point on the house photo, normalised: x and y run 0..1 across the photo's width and height. */
@Serializable
data class MapPoint(val x: Float, val y: Float)

/**
 * One physical run of lights (a roofline, a window, a bush …) traced on the photo.
 * The first point is where LED [startLed] sits; LEDs are spread evenly along the line.
 */
@Serializable
data class LightRun(
    val id: String,
    val name: String,
    val deviceId: String,
    val startLed: Int,
    val count: Int,
    val points: List<MapPoint> = emptyList(),
) {
    val endLed get() = startLed + count
}

@Serializable
data class HouseMap(
    /** Path of the downscaled photo in app storage, or null for a plain dark canvas. */
    val photoPath: String? = null,
    /** Photo width / height. */
    val aspect: Float = 1.5f,
    val runs: List<LightRun> = emptyList(),
)

/** A placed LED: which device and physical index, where it is, and its order within its run. */
data class PlacedLed(val deviceId: String, val index: Int, val x: Float, val y: Float, val runIndex: Int, val orderInRun: Int)

/** Spreads [LightRun.count] LEDs evenly by distance along the traced line (in photo-proportional space). */
fun LightRun.ledPositions(aspect: Float): List<MapPoint> {
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
    run.ledPositions(aspect).mapIndexed { i, p -> PlacedLed(run.deviceId, run.startLed + i, p.x, p.y, r, i) }
}
