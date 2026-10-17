package com.wledcontroller.app.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.wledcontroller.app.data.PaletteStop
import com.wledcontroller.app.data.WledColor
import kotlin.math.ln
import kotlin.math.pow

fun Int.toComposeColor() = Color(this or (0xFF shl 24))

fun WledColor.toComposeColor() = rgb.toComposeColor()

fun hsvToRgb(h: Float, s: Float, v: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

fun rgbToHsv(rgb: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(rgb, it) }

fun rgbHex(rgb: Int) = String.format("%06X", rgb and 0xFFFFFF)

fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toIntOrNull(16)?.let { it or (0xFF shl 24) }
}

/** Approximate colour of a black body at [kelvin] (Tanner Helland's fit). */
fun kelvinToRgb(kelvin: Int): Int {
    val t = kelvin / 100.0
    val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
    val g = if (t <= 66) 99.4708025861 * ln(t) - 161.1195681661 else 288.1221695283 * (t - 60).pow(-0.0755148492)
    val b = when {
        t >= 66 -> 255.0
        t <= 19 -> 0.0
        else -> 138.5177312231 * ln(t - 10) - 305.0447927307
    }
    fun c(x: Double) = x.coerceIn(0.0, 255.0).toInt()
    return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
}

val HueColors = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
)

private val RandomFill = listOf(0xFFE53935, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA).map { Color(it) }

/** Builds a horizontal gradient for a palette preview, resolving "c1".."c3" from the segment colours. */
fun paletteBrush(stops: List<PaletteStop>?, segmentColors: List<WledColor>): Brush? {
    if (stops.isNullOrEmpty()) return null
    var randomIdx = 0
    val resolved = stops.map { s ->
        val color = when {
            s.color != null -> s.color.toComposeColor()
            s.slot != null -> (segmentColors.getOrNull(s.slot) ?: WledColor.BLACK).toComposeColor()
            else -> RandomFill[randomIdx++ % RandomFill.size]
        }
        s.pos.coerceIn(0f, 1f) to color
    }.sortedBy { it.first }
    val list = if (resolved.size == 1) listOf(0f to resolved[0].second, 1f to resolved[0].second) else resolved
    return Brush.horizontalGradient(colorStops = list.toTypedArray())
}

/** WLED-style quick colours. */
val QuickColors: List<Int> = listOf(
    0xFFFF0000, 0xFFFFA000, 0xFFFFC800, 0xFFFFE0A0, 0xFFFFFFFF, 0xFFBFDFFF,
    0xFF00FF00, 0xFF00FFC8, 0xFF00C8FF, 0xFF0000FF, 0xFF8800FF, 0xFFFF00C8,
).map { it.toInt() }
