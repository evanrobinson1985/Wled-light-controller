package com.wledcontroller.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PlacedLed
import com.wledcontroller.app.house.model3d.Camera
import com.wledcontroller.app.house.model3d.HouseGlView
import com.wledcontroller.app.house.model3d.Led3D
import com.wledcontroller.app.house.model3d.Mesh
import com.wledcontroller.app.house.model3d.P3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The textured house model with lights on it. [onTap] gets the spot on the model touched (null: missed it). */
@Composable
fun HouseModelView(
    mesh: Mesh,
    textures: List<File>,
    lights: List<P3?>,
    colors: IntArray,
    modifier: Modifier = Modifier,
    brightness: Float = 0.35f,
    fixedCamera: Camera? = null,
    onTap: ((P3?) -> Unit)? = null,
    /** Painting on the model: each spot touched (null: off the model), and true at the end of a stroke. */
    onPaint: ((P3?, Boolean) -> Unit)? = null,
) {
    // Finding the spot tapped on a big scan takes a moment: done off the screen's thread.
    val tapScope = androidx.compose.runtime.rememberCoroutineScope()
    AndroidView(
        factory = { HouseGlView(it) },
        modifier = modifier,
        update = { v ->
            v.setModel(mesh, textures)
            v.brightness = brightness
            v.fixedCamera = fixedCamera
            v.setLights(lights, colors)
            v.onPaint = onPaint?.let { paint ->
                { x: Float, y: Float, end: Boolean ->
                    val aspect = v.width.toFloat() / v.height.coerceAtLeast(1)
                    val cam = v.camera(aspect)
                    val dir = cam.ray(x, y, aspect)
                    // Back to the drawing thread's caller (the UI) with where it touched.
                    val hit = if (end) null else mesh.raycast(cam.eye, dir)?.let { cam.eye + dir * it }
                    paint(hit, end)
                }
            }
            v.onTap = onTap?.let { tap ->
                { x: Float, y: Float ->
                    val aspect = v.width.toFloat() / v.height.coerceAtLeast(1)
                    val cam = v.camera(aspect)
                    val dir = cam.ray(x, y, aspect)
                    tapScope.launch {
                        val hit = withContext(Dispatchers.Default) { mesh.raycast(cam.eye, dir) }
                        tap(hit?.let { cam.eye + dir * it })
                    }
                }
            }
        },
    )
}

/**
 * The display in 3D on the house model when there is one (and it's switched on), otherwise
 * the simple 3D view with the lights on a flat wall.
 */
@Composable
fun HousePreview3D(map: HouseMap, leds: List<PlacedLed>, colorOf: (Int) -> Int, modifier: Modifier = Modifier) {
    val model = map.model3d?.takeIf { it.show }
    if (model == null) { House3DView(map, leds, colorOf, modifier); return }
    val container = LocalAppContainer.current
    val loaded by produceState<Pair<Mesh, List<File>>?>(null, model.dir, model.sharpened, model.edits) {
        value = runCatching { container.models.mesh(model) to container.models.textures(model) }.getOrNull()
    }
    val mesh = loaded
    val positions by produceState<List<P3?>?>(null, mesh, model, map.runs, map.aspect, leds.size) {
        value = mesh?.let { m -> withContext(Dispatchers.Default) { Led3D.positions(map, leds, model, m.first) } }
    }
    Box(modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B0D12)), contentAlignment = Alignment.Center) {
        val pos = positions
        if (mesh == null || pos == null) {
            if (mesh == null && loaded == null) CircularProgressIndicator(Modifier.size(28.dp))
            return@Box
        }
        val colors = IntArray(leds.size) { colorOf(it) }
        HouseModelView(mesh.first, mesh.second, pos, colors, Modifier.fillMaxSize())
        if (model.lineup == null && map.runs.none { it.points3d != null })
            Text(
                "Line the model up with your house photo (House map › 3D house model) to put your lights on it.",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
    }
}

/**
 * Marks four corners on a photo by dragging them into place: top-left, top-right,
 * bottom-right, bottom-left. [initial] starts them somewhere else than a box in the middle.
 */
@Composable
fun CornerPicker(path: String, aspect: Float, title: String, initial: List<MapPoint>?, onDone: (List<MapPoint>) -> Unit, onCancel: () -> Unit) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / s > 2000) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        }
    }
    val pts = remember { mutableStateListOf<MapPoint>().apply { addAll(initial?.takeIf { it.size == 4 } ?: listOf(MapPoint(0.25f, 0.3f), MapPoint(0.75f, 0.3f), MapPoint(0.75f, 0.75f), MapPoint(0.25f, 0.75f))) } }
    var dragging by remember { mutableStateOf<Int?>(null) }
    val labels = listOf("Top left", "Top right", "Bottom right", "Bottom left")
    val colors = listOf(Color(0xFFFF5252), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFFFD740))
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0D12)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                "Drag each dot onto its corner of the wall: where the wall meets the ground and the roof's edge.",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(0.2f, 5f))) {
                    image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o ->
                                    dragging = pts.indices.minByOrNull { i -> (Offset(pts[i].x * size.width, pts[i].y * size.height) - o).getDistance() }
                                },
                                onDragEnd = { dragging = null },
                                onDragCancel = { dragging = null },
                            ) { change, drag ->
                                change.consume()
                                val i = dragging ?: return@detectDragGestures
                                pts[i] = MapPoint((pts[i].x + drag.x / size.width).coerceIn(0f, 1f), (pts[i].y + drag.y / size.height).coerceIn(0f, 1f))
                            }
                        },
                    ) {
                        val o = pts.map { Offset(it.x * size.width, it.y * size.height) }
                        for (i in 0 until 4) drawLine(Color.White.copy(alpha = 0.85f), o[i], o[(i + 1) % 4], 2.5f)
                        o.forEachIndexed { i, p ->
                            drawCircle(colors[i], if (dragging == i) 22f else 14f, p, style = Stroke(4f))
                            drawCircle(colors[i], 3.5f, p)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { i, l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(10.dp)) { drawCircle(colors[i]) }
                        Text(" $l", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = { onDone(pts.toList()) }) { Text("Done") }
            }
        }
    }
}
