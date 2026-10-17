package com.wledcontroller.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Colour wheel plus a value slider. Keeps its own HSV so hue survives when saturation or value
 * hit zero, and ignores device echoes while the user is dragging.
 */
@Composable
fun ColorPickerPanel(
    color: Int,
    onColorChange: (Int) -> Unit,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hsv by remember { mutableStateOf(rgbToHsv(color)) }
    var interacting by remember { mutableStateOf(false) }

    LaunchedEffect(color) {
        if (interacting) return@LaunchedEffect
        val shown = hsvToRgb(hsv[0], hsv[1], hsv[2])
        if (shown and 0xFFFFFF != color and 0xFFFFFF) {
            val n = rgbToHsv(color)
            if (n[1] < 0.01f || n[2] < 0.01f) n[0] = hsv[0]
            hsv = n
        }
    }

    fun emit(h: Float, s: Float, v: Float) {
        hsv = floatArrayOf(h, s, v)
        onColorChange(hsvToRgb(h, s, v))
    }

    val pure = hsvToRgb(hsv[0], hsv[1], 1f).toComposeColor()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ColorWheel(
            hue = hsv[0],
            saturation = hsv[1],
            thumbColor = hsvToRgb(hsv[0], hsv[1], hsv[2]).toComposeColor(),
            onChange = { h, s -> emit(h, s, hsv[2].coerceAtLeast(0.05f)) },
            onGestureStart = { interacting = true },
            onGestureEnd = {
                interacting = false
                onCommit(hsvToRgb(hsv[0], hsv[1], hsv[2]))
            },
            modifier = Modifier.fillMaxWidth(0.82f).widthIn(max = 420.dp).padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        GradientSlider(
            value = hsv[2],
            onValueChange = { interacting = true; emit(hsv[0], hsv[1], it) },
            onValueChangeFinished = { interacting = false; onCommit(hsvToRgb(hsv[0], hsv[1], hsv[2])) },
            brush = Brush.horizontalGradient(listOf(Color.Black, pure)),
            label = "Colour level",
            icon = Icons.Filled.Brightness6,
            valueText = "${(hsv[2] * 100).toInt()}%",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
