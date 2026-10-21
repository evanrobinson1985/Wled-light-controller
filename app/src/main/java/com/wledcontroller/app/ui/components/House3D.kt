package com.wledcontroller.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.PlacedLed
import kotlin.math.cos
import kotlin.math.sin

/** Where a point in the house's space lands on screen, and how far away it is. */
internal class Projection(private val yaw: Float, private val pitch: Float, private val zoom: Float, private val w: Float, private val h: Float) {
    private val cy = cos(yaw); private val sy = sin(yaw); private val cp = cos(pitch); private val sp = sin(pitch)

    /** [x] across (photo heights), [y] up, [z] out from the house towards the viewer. Returns screen x, y and depth. */
    fun project(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        // Turn round the vertical axis, then tilt to look down on the yard.
        val x1 = x * cy + z * sy
        val z1 = -x * sy + z * cy
        val y1 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        val dist = 3.2f - z2
        val f = (minOf(w, h * 1.6f) * 0.9f) * zoom / dist.coerceAtLeast(0.3f)
        return Triple(w / 2f + x1 * f, h * 0.55f - y1 * f, dist)
    }
}

/**
 * The display in 3D, like xLights' 3D preview: lights on the house wall, yard props standing
 * out in front at their distance. Drag to turn and tilt, pinch to zoom.
 */
@Composable
fun House3DView(map: HouseMap, leds: List<PlacedLed>, colorOf: (Int) -> Int, modifier: Modifier = Modifier) {
    var yaw by remember { mutableFloatStateOf(-0.45f) }
    var pitch by remember { mutableFloatStateOf(0.25f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    // Real centimetres per picture height, for turning distances in front of the house into the same units.
    val cmPerHeight = map.cmPerHeight ?: 700f
    val grid = Color.White.copy(alpha = 0.12f)
    val wall = Color.White.copy(alpha = 0.22f)
    Box(modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B0D12))) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, pan, z, _ ->
                    yaw = (yaw + pan.x / size.width * 3f).coerceIn(-1.4f, 1.4f)
                    pitch = (pitch + pan.y / size.height * 2f).coerceIn(0f, 1.2f)
                    zoom = (zoom * z).coerceIn(0.4f, 4f)
                }
            }
        ) {
            val proj = Projection(yaw, pitch, zoom, size.width, size.height)
            val half = map.aspect / 2f
            fun at(x: Float, y: Float, z: Float): Offset = proj.project(x, y, z).let { Offset(it.first, it.second) }
            // The ground in front of the house, and the house wall's outline.
            for (k in 0..8) {
                val z = k * 0.25f
                drawLine(grid, at(-half - 0.5f, -0.5f, z), at(half + 0.5f, -0.5f, z), 1f)
            }
            for (k in -4..4) {
                val x = k * (half + 0.5f) / 4f
                drawLine(grid, at(x, -0.5f, 0f), at(x, -0.5f, 2f), 1f)
            }
            val corners = listOf(at(-half, 0.5f, 0f), at(half, 0.5f, 0f), at(half, -0.5f, 0f), at(-half, -0.5f, 0f))
            for (i in corners.indices) drawLine(wall, corners[i], corners[(i + 1) % 4], 1.5f)
            // Lights, farthest first so nearer ones draw over them.
            val pts = leds.indices.mapNotNull { i ->
                val l = leds[i]
                if (l.hidden) return@mapNotNull null
                val depth = (map.runs.getOrNull(l.runIndex)?.depthCm ?: 0f) / cmPerHeight
                val (sx, sy, d) = proj.project(l.x * map.aspect - half, 0.5f - l.y, depth)
                Triple(i, Offset(sx, sy), d)
            }.sortedByDescending { it.third }
            for ((i, o, d) in pts) {
                val c = colorOf(i)
                if (c == 0) { drawCircle(Color(0xFF2A2E38), 1.6f, o); continue }
                val col = Color(0xFF000000.toInt() or c)
                val r = (6f / d).coerceIn(1.5f, 7f)
                drawCircle(col.copy(alpha = 0.25f), r * 2.4f, o)
                drawCircle(col, r, o)
            }
        }
        Text(
            "Drag to turn · pinch to zoom",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}

