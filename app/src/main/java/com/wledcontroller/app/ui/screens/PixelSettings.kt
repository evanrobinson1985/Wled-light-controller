package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.sim.SimEffects
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.PixelStyle
import com.wledcontroller.app.ui.components.drawPixel
import com.wledcontroller.app.ui.components.pixelRadius
import kotlinx.coroutines.launch

/**
 * Light size and spacing, used by every preview that draws lights (house map, effects, shows,
 * xLights previews, camera scan and the simulator). Changes apply live.
 */
@Composable
internal fun PixelStyleSetting() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val stored by container.store.pixelStyle.collectAsStateWithLifecycle(PixelStyle())
    var local by remember(stored) { mutableStateOf(stored) }
    fun set(style: PixelStyle) { local = style; scope.launch { container.store.setPixelStyle(style) } }

    val sizeRange = PixelStyle.MAX_SIZE - PixelStyle.MIN_SIZE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Light size & spacing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { set(PixelStyle()) }) { Text("Reset") }
        }
        Text(
            "How lights are drawn on the house map, effect and show previews, the camera scan and the simulator. Lights shrink where they're packed closely, so they always keep the spacing apart.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientSlider(
            (local.sizeDp - PixelStyle.MIN_SIZE) / sizeRange,
            { set(local.copy(sizeDp = PixelStyle.MIN_SIZE + it * sizeRange)) },
            label = "Light size", valueText = "%.1f dp".format(local.sizeDp), height = 30.dp,
        )
        GradientSlider(
            local.gapDp / PixelStyle.MAX_GAP,
            { set(local.copy(gapDp = it * PixelStyle.MAX_GAP)) },
            label = "Spacing between lights", valueText = "%.1f dp".format(local.gapDp), height = 30.dp,
        )
        PixelSample("Dense strip (60 per metre)", local, pitchDp = 5f)
        PixelSample("Bullet string", local, pitchDp = 16f)
    }
}

/** A row of rainbow lights [pitchDp] apart, drawn with [style]. */
@Composable
private fun PixelSample(label: String, style: PixelStyle, pitchDp: Float) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF070B1C))) {
            val pitch = pitchDp.dp.toPx()
            val n = (size.width / pitch).toInt()
            // Neighbour distance in "picture heights" for pixelRadius.
            val r = pixelRadius(style, neighbour = pitch / size.height)
            for (i in 0 until n) drawPixel(SimEffects.hsv(i / n.toFloat()), Offset(pitch * (i + 0.5f), size.height / 2), r)
        }
    }
}
