package com.wledcontroller.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A spotlight (a DMX fixture) on a house picture: its head at [at] and a beam of light into the
 * night in its colour ([rgb], 0xRRGGBB, brightness included), swung left and right by [pan] and
 * raised or lowered by [tilt] (both 0..1; 0.5, 0.5 points straight up). Dark, it's just its head.
 */
fun DrawScope.drawSpotlight(at: Offset, rgb: Int, pan: Float, tilt: Float, scale: Float = 1f) {
    val head = 3.5.dp.toPx() * scale
    val level = maxOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF) / 255f
    if (level < 0.02f) {
        drawCircle(Color(0xFF3A3F4C), head, at)
        return
    }
    // The colour at full strength; brightness goes into how strongly the beam shows.
    val k = 1f / level
    val full = Color(
        (((rgb shr 16) and 0xFF) * k / 255f).coerceIn(0f, 1f),
        (((rgb shr 8) and 0xFF) * k / 255f).coerceIn(0f, 1f),
        ((rgb and 0xFF) * k / 255f).coerceIn(0f, 1f),
    )
    val angle = (pan - 0.5f) * 2.6f                      // left / right of straight up
    val len = size.height * (0.25f + 0.65f * tilt) * scale
    val spread = 0.06f + 0.05f * (1f - tilt)
    fun end(a: Float) = Offset(at.x + sin(a) * len, at.y - cos(a) * len)
    val beam = Path().apply {
        moveTo(at.x, at.y)
        end(angle - spread).let { lineTo(it.x, it.y) }
        end(angle + spread).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(
        beam,
        Brush.radialGradient(
            listOf(full.copy(alpha = 0.55f * level), full.copy(alpha = 0.18f * level), full.copy(alpha = 0f)),
            center = at, radius = len,
        ),
        blendMode = BlendMode.Plus,
    )
    drawCircle(full.copy(alpha = 0.45f * level), head * 2.6f, at, blendMode = BlendMode.Plus)
    drawCircle(full, head, at)
}
