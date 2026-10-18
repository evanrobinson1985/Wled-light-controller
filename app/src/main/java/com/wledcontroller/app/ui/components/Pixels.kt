package com.wledcontroller.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PlacedLed
import kotlin.math.hypot

/**
 * How lights are drawn everywhere the app shows pixels: [sizeDp] is a light's diameter and
 * [gapDp] the least space kept between neighbouring lights (lights shrink to keep it).
 */
data class PixelStyle(val sizeDp: Float = DEFAULT_SIZE, val gapDp: Float = DEFAULT_GAP) {
    companion object {
        const val DEFAULT_SIZE = 4f
        const val DEFAULT_GAP = 1f
        const val MIN_SIZE = 1f
        const val MAX_SIZE = 16f
        const val MAX_GAP = 8f
    }
}

val LocalPixelStyle = compositionLocalOf { PixelStyle() }

/** No neighbour: nothing limits the size. */
const val NO_NEIGHBOUR = Float.MAX_VALUE

/**
 * Distance from each LED to its nearest neighbour in the same run, in units of the picture's
 * height (x is scaled by [aspect] so distances are true on screen).
 */
fun neighbourGaps(leds: List<PlacedLed>, aspect: Float): FloatArray {
    val out = FloatArray(leds.size) { NO_NEIGHBOUR }
    leds.indices.groupBy { leds[it].runIndex }.values.forEach { idx ->
        val sorted = idx.sortedBy { leds[it].orderInRun }
        for (k in 0 until sorted.size - 1) {
            val a = leds[sorted[k]]; val b = leds[sorted[k + 1]]
            val d = hypot((b.x - a.x) * aspect, b.y - a.y)
            if (d < out[sorted[k]]) out[sorted[k]] = d
            if (d < out[sorted[k + 1]]) out[sorted[k + 1]] = d
        }
    }
    return out
}

/** The same for one run's LEDs in order (null = not placed). */
fun neighbourGaps(points: List<MapPoint?>, aspect: Float): FloatArray {
    val out = FloatArray(points.size) { NO_NEIGHBOUR }
    for (k in 0 until points.size - 1) {
        val a = points[k] ?: continue
        val b = points[k + 1] ?: continue
        val d = hypot((b.x - a.x) * aspect, b.y - a.y)
        if (d < out[k]) out[k] = d
        if (d < out[k + 1]) out[k + 1] = d
    }
    return out
}

/**
 * A light's radius in pixels: the chosen size, made smaller when needed so it keeps the chosen
 * gap to its nearest neighbour ([neighbour] in picture heights, [pictureHeight] in pixels).
 * [scale] shrinks everything, e.g. for a zoomed-in canvas or a thumbnail.
 */
fun DrawScope.pixelRadius(style: PixelStyle, neighbour: Float = NO_NEIGHBOUR, pictureHeight: Float = size.height, scale: Float = 1f): Float {
    val want = style.sizeDp.dp.toPx() / 2f * scale
    if (neighbour == NO_NEIGHBOUR) return want
    val room = (neighbour * pictureHeight - style.gapDp.dp.toPx() * scale) / 2f
    return minOf(want, room).coerceAtLeast(0.5f * scale)
}

/** A preview light: a soft glow round a solid dot, or a faint dot when it's off. */
fun DrawScope.drawPixel(rgb: Int, center: Offset, radius: Float, glow: Float = 1f) {
    if (rgb and 0xFFFFFF == 0) {
        drawCircle(Color.White.copy(alpha = 0.12f), radius * 0.8f, center)
        return
    }
    val c = Color(rgb or (0xFF shl 24))
    if (glow > 0f) drawCircle(c.copy(alpha = 0.3f * glow.coerceAtMost(1f)), radius * (1f + 1.2f * glow), center, blendMode = BlendMode.Plus)
    drawCircle(c, radius, center)
}
