package com.wledcontroller.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Rows, LEDs per row and spacing (dp) to show [count] LEDs across [widthDp]. */
internal fun stripLayout(count: Int, widthDp: Float): Triple<Int, Int, Float> {
    val n = count.coerceAtLeast(1)
    // As few rows as keep every LED at least 4 dp apart, up to 14 rows; then they just get smaller.
    val rows = ceil(n * MIN_PITCH / widthDp).toInt().coerceIn(1, MAX_ROWS)
    val perRow = ceil(n / rows.toFloat()).toInt()
    val pitch = (widthDp / perRow).coerceAtMost(MAX_PITCH)
    return Triple(rows, perRow, pitch)
}

private const val MIN_PITCH = 4f
private const val MAX_PITCH = 12f
private const val MAX_ROWS = 14

/**
 * Every LED of a strip as a dot in its colour ([colors] holds 0xRRGGBB per LED), left to right,
 * wrapping onto more rows for long strips so each LED stays visible.
 */
@Composable
fun LedStripPreview(colors: IntArray, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val (rows, perRow, pitch) = stripLayout(colors.size, maxWidth.value)
        Canvas(Modifier.fillMaxWidth().height((rows * pitch).dp)) {
            val p = pitch.dp.toPx()
            val r = p * 0.42f
            val left = (size.width - perRow * p) / 2f
            for (i in colors.indices) {
                val c = colors[i] and 0xFFFFFF
                val o = Offset(left + (i % perRow) * p + p / 2f, (i / perRow) * p + p / 2f)
                // Unlit LEDs stay faintly visible so the strip's length shows.
                if (c == 0) drawCircle(Color(0xFF262B36), r * 0.8f, o)
                else drawCircle(Color(0xFF000000.toInt() or c), r, o)
            }
        }
    }
}
