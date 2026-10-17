@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.HolidayThemes
import com.wledcontroller.app.house.HouseEffect
import com.wledcontroller.app.house.HouseEffectService
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.HouseRenderer
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.StandalonePlan
import com.wledcontroller.app.house.ledPositions
import com.wledcontroller.app.house.placedLeds
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.SectionTitle
import com.wledcontroller.app.ui.components.themeBrush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// ================================================================== shared canvas

@Composable
private fun rememberPhoto(path: String?): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    return photo
}

/** The house photo (or a dark canvas) with an overlay drawn in the same normalised coordinates. */
@Composable
private fun HouseCanvas(
    map: HouseMap,
    photo: ImageBitmap?,
    modifier: Modifier = Modifier,
    onTap: ((MapPoint) -> Unit)? = null,
    overlay: DrawScope.() -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(map.aspect.coerceIn(0.4f, 3f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101218))
    ) {
        if (photo != null) {
            Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = 0.7f)
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { o -> tap?.invoke(MapPoint(o.x / size.width, o.y / size.height)) }
                }
        ) { overlay() }
    }
}

private val RunColors = listOf(0xFFFFB86B, 0xFF6BC8FF, 0xFFB98CFF, 0xFF7BE07B, 0xFFFF7BA8, 0xFFFFE06B).map { Color(it) }

// ================================================================== player

@Composable
fun HouseScreen(onBack: () -> Unit, onEditMap: () -> Unit, onStandalone: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val running by container.house.running.collectAsStateWithLifecycle()
    val params by container.house.params.collectAsStateWithLifecycle()
    val photo = rememberPhoto(map.photoPath)
    val leds = remember(map) { map.placedLeds() }
    val themes = remember { HolidayThemes.forToday() }

    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        HouseEffectService.start(context)
    }
    fun play() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else HouseEffectService.start(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whole house") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onEditMap) { Icon(Icons.Filled.Edit, "Edit map") } },
            )
        },
        bottomBar = {
            if (leds.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Button(
                        onClick = { if (running) HouseEffectService.stop(context) else play() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (running) "Stop" else "Play on ${leds.map { it.deviceId }.distinct().size} controllers")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (leds.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Home, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Map your house", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Take a photo of your house and trace where each run of lights goes. Effects will then sweep across the real shape of your home, across every controller.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onEditMap) { Text("Start mapping") }
                }
                return@Column
            }

            HouseCanvas(map, photo) {
                val r = 3.5.dp.toPx()
                leds.forEachIndexed { i, led ->
                    val c = Color(HouseRenderer.color(params, led.x, led.y, t, i, led.orderInRun) or (0xFF shl 24))
                    val o = Offset(led.x * size.width, led.y * size.height)
                    drawCircle(c.copy(alpha = 0.35f), r * 2.2f, o)
                    drawCircle(c, r, o)
                }
            }
            Text(
                if (running) "Live on your lights — keeps running with the screen off." else "Preview. Press Play to send it to your lights.",
                style = MaterialTheme.typography.labelMedium,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionTitle("Effect")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HouseEffect.entries.forEach { e ->
                    FilterChip(
                        selected = params.effect == e,
                        onClick = { container.house.params.value = params.copy(effect = e) },
                        label = { Text(e.label) },
                    )
                }
            }

            SectionTitle("Colours")
            val colorSets = remember {
                listOf(
                    "Warm white" to listOf(0xFFFFC07A.toInt(), 0xFFFFD9A8.toInt(), 0xFFFFB060.toInt()),
                    "Cool white" to listOf(0xFFFFFFFF.toInt(), 0xFFCFE6FF.toInt(), 0xFFE8F0FF.toInt()),
                ) + themes.map { it.name to it.colors }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(colorSets, key = { it.first }) { (name, colors) ->
                    val selected = params.colors == colors
                    Surface(
                        onClick = { container.house.params.value = params.copy(colors = colors) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.width(120.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).background(themeBrush(colors, false)))
                            Spacer(Modifier.height(6.dp))
                            Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }

            SectionTitle("Adjust")
            GradientSlider(params.speed, { container.house.params.value = params.copy(speed = it) }, label = "Speed", valueText = "${(params.speed * 100).toInt()}%", height = 34.dp)
            Spacer(Modifier.height(10.dp))
            GradientSlider(params.size, { container.house.params.value = params.copy(size = it) }, label = "Size", valueText = "${(params.size * 100).toInt()}%", height = 34.dp)
            Spacer(Modifier.height(10.dp))
            GradientSlider(params.brightness, { container.house.params.value = params.copy(brightness = it) }, label = "Brightness", valueText = "${(params.brightness * 100).toInt()}%", height = 34.dp)

            Spacer(Modifier.height(16.dp))
            Card(onClick = onStandalone, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Run without the phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Set one controller up as the master so WLED's own 2D effects span the whole house, even when your phone is off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ================================================================== map editor

@Composable
fun HouseMapEditor(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val devices by container.devices.list.collectAsStateWithLifecycle()
    val photo = rememberPhoto(map.photoPath)

    var drawingRunId by remember { mutableStateOf<String?>(null) }
    val draft = remember { mutableStateListOf<MapPoint>() }
    var editRun by remember { mutableStateOf<LightRun?>(null) }
    var deleteRun by remember { mutableStateOf<LightRun?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { container.house.importPhoto(uri) }
                .onSuccess { (path, aspect) -> container.store.updateHouseMap { it.copy(photoPath = path, aspect = aspect) } }
                .onFailure { importError = it.message }
        }
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun saveRun(run: LightRun) = scope.launch {
        container.store.updateHouseMap { m ->
            m.copy(runs = if (m.runs.any { it.id == run.id }) m.runs.map { if (it.id == run.id) run else it } else m.runs + run)
        }
    }

    val drawingRun = map.runs.firstOrNull { it.id == drawingRunId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (drawingRun != null) "Trace ${drawingRun.name}" else "House map") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { pickPhoto() }) { Icon(Icons.Filled.Image, "Choose photo") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (map.photoPath == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Add a photo of your house", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Stand across the street and take a photo that shows all your lights, then trace each run on it. You can also trace on the blank canvas below.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { pickPhoto() }) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(8.dp)); Text("Choose photo") }
                    }
                }
            }
            importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            HouseCanvas(
                map, photo,
                onTap = if (drawingRun != null) { { p -> draft.add(p) } } else null,
            ) {
                val stroke = 2.dp.toPx()
                map.runs.forEachIndexed { r, run ->
                    if (run.id == drawingRunId) return@forEachIndexed
                    val col = RunColors[r % RunColors.size]
                    drawRun(run.points, col, stroke, dashed = false)
                    run.ledPositions(map.aspect).forEach { p -> drawCircle(col, 2.5.dp.toPx(), Offset(p.x * size.width, p.y * size.height)) }
                    run.points.firstOrNull()?.let { s ->
                        drawCircle(Color.White, 6.dp.toPx(), Offset(s.x * size.width, s.y * size.height), style = Stroke(stroke))
                    }
                }
                if (drawingRun != null) {
                    drawRun(draft, Color.White, stroke, dashed = true)
                    val preview = drawingRun.copy(points = draft.toList()).ledPositions(map.aspect)
                    preview.forEach { p -> drawCircle(Color.White, 2.5.dp.toPx(), Offset(p.x * size.width, p.y * size.height)) }
                    draft.firstOrNull()?.let { s -> drawCircle(Color.Red, 6.dp.toPx(), Offset(s.x * size.width, s.y * size.height)) }
                }
            }

            if (drawingRun != null) {
                Text(
                    "Tap along the lights, starting at the end where LED ${drawingRun.startLed} is (usually the end nearest the controller). Add a point at every corner.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (draft.isNotEmpty()) draft.removeAt(draft.lastIndex) }, enabled = draft.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null); Spacer(Modifier.width(6.dp)); Text("Undo")
                    }
                    TextButton(onClick = { drawingRunId = null; draft.clear() }) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(enabled = draft.size >= 2, onClick = {
                        saveRun(drawingRun.copy(points = draft.toList()))
                        drawingRunId = null; draft.clear()
                    }) { Text("Done") }
                }
                return@Column
            }

            SectionTitle("Light runs") {
                TextButton(onClick = {
                    editRun = LightRun(UUID.randomUUID().toString(), "Run ${map.runs.size + 1}", devices.firstOrNull()?.id ?: "", 0, 0)
                }, enabled = devices.isNotEmpty()) { Icon(Icons.Filled.Add, null); Text("Add") }
            }
            if (map.runs.isEmpty()) {
                Text(
                    "A run is one continuous line of lights on one controller — for example the roofline, or the lights around a window. Add one for each.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            map.runs.forEachIndexed { r, run ->
                val device = devices.firstOrNull { it.id == run.deviceId }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(RunColors[r % RunColors.size]))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(run.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${device?.device?.name ?: "Missing device"} · LEDs ${run.startLed}–${run.endLed - 1}" +
                                        if (run.points.size < 2) " · not traced yet" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { editRun = run }) { Icon(Icons.Filled.Edit, "Edit run") }
                            IconButton(onClick = { deleteRun = run }) { Icon(Icons.Outlined.Delete, "Delete run") }
                        }
                        Row {
                            TextButton(onClick = { drawingRunId = run.id; draft.clear() }) { Text(if (run.points.size < 2) "Trace" else "Retrace") }
                            TextButton(enabled = run.points.size >= 2, onClick = { saveRun(run.copy(points = run.points.reversed())) }) {
                                Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Flip start")
                            }
                            TextButton(enabled = device != null, onClick = { device?.let { container.house.identify(run, it.device.host) } }) {
                                Icon(Icons.Outlined.Lightbulb, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Identify")
                            }
                        }
                    }
                }
            }
            Text(
                "Identify lights the run on your house with its first LED red, so you can check the start matches the red dot.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    editRun?.let { run ->
        RunDialog(run, map, onDismiss = { editRun = null }) { updated ->
            val isNew = map.runs.none { it.id == updated.id }
            saveRun(updated)
            editRun = null
            if (isNew) { drawingRunId = updated.id; draft.clear() }
        }
    }
    deleteRun?.let { run ->
        AlertDialog(
            onDismissRequest = { deleteRun = null },
            title = { Text("Delete ${run.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.store.updateHouseMap { m -> m.copy(runs = m.runs.filterNot { it.id == run.id }) } }
                    deleteRun = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteRun = null }) { Text("Cancel") } },
        )
    }
}

private fun DrawScope.drawRun(points: List<MapPoint>, color: Color, stroke: Float, dashed: Boolean) {
    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { i, p ->
        val x = p.x * size.width; val y = p.y * size.height
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path, color.copy(alpha = 0.8f),
        style = Stroke(stroke, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null),
    )
}

@Composable
private fun RunDialog(run: LightRun, map: HouseMap, onDismiss: () -> Unit, onSave: (LightRun) -> Unit) {
    val container = LocalAppContainer.current
    val devices by container.devices.list.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(run.name) }
    var deviceId by remember { mutableStateOf(run.deviceId) }
    var menu by remember { mutableStateOf(false) }
    val device = devices.firstOrNull { it.id == deviceId }
    val ledCount = device?.snapshot?.value?.info?.ledCount ?: 0
    // Default a new run to the LEDs on this controller that no other run uses yet.
    val usedEnd = map.runs.filter { it.deviceId == deviceId && it.id != run.id }.maxOfOrNull { it.endLed } ?: 0
    var start by remember(deviceId) { mutableStateOf(if (run.count > 0 && deviceId == run.deviceId) run.startLed.toString() else usedEnd.toString()) }
    var count by remember(deviceId) {
        mutableStateOf(if (run.count > 0 && deviceId == run.deviceId) run.count.toString() else (ledCount - usedEnd).coerceAtLeast(1).toString())
    }
    val s = start.toIntOrNull(); val c = count.toIntOrNull()
    val valid = name.isNotBlank() && device != null && s != null && c != null && c > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (run.count == 0) "New light run" else "Edit light run") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Roofline)") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(device?.device?.name ?: "Choose controller", Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        devices.forEach { d ->
                            DropdownMenuItem(text = { Text(d.device.name) }, onClick = { deviceId = d.id; menu = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        start, { start = it.filter(Char::isDigit).take(5) }, label = { Text("First LED") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        count, { count = it.filter(Char::isDigit).take(5) }, label = { Text("LED count") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                if (ledCount > 0) {
                    Text("This controller has $ledCount LEDs (0–${ledCount - 1}).", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(run.copy(name = name.trim(), deviceId = deviceId, startLed = s!!, count = c!!)) }) {
                Text(if (run.count == 0) "Next: trace it" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ================================================================== standalone

@Composable
fun StandaloneScreen(onBack: () -> Unit, onOpenDevice: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val all by container.devices.list.collectAsStateWithLifecycle()
    val mapped = all.filter { c -> map.runs.any { it.deviceId == c.id } }
    var masterId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mapped.map { it.id }) {
        if (masterId == null) {
            masterId = (mapped.firstOrNull { it.snapshot.value.info?.arch?.contains("esp32", true) == true } ?: mapped.firstOrNull())?.id
        }
    }
    var plan by remember { mutableStateOf<StandalonePlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val hasBackup = remember(masterId, refresh) { masterId?.let { container.standalone.hasBackup(it) } == true }

    LaunchedEffect(masterId, map) {
        val id = masterId ?: return@LaunchedEffect
        plan = null; error = null
        runCatching { container.standalone.plan(id) }.onSuccess { plan = it }.onFailure { error = it.message }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run without the phone") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "One controller becomes the master: the others are added to it as network outputs, and your house map becomes a 2D layout. WLED's own 2D effects (and presets, timers, playlists) then run across the whole house with your phone off. All controllers must stay powered and on Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (mapped.isEmpty()) {
                Text("Trace some light runs on the house map first.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Text("Master controller", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mapped.forEach { c ->
                    val arch = c.snapshot.value.info?.arch?.uppercase().orEmpty()
                    FilterChip(
                        selected = masterId == c.id,
                        onClick = { masterId = c.id; done = null },
                        label = { Text(c.device.name + if (arch.isNotEmpty()) " · $arch" else "") },
                    )
                }
            }

            when {
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                plan == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val p = plan!!
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("The plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            KeyRow("${p.master.name} (own LEDs)", "0–${p.masterLeds - 1}")
                            p.remotes.forEach { r ->
                                KeyRow("${r.device.name} → ${r.ip.joinToString(".")}", "${r.start}–${r.start + r.len - 1}")
                            }
                            KeyRow("Total", "${p.totalLeds} LEDs")
                            KeyRow("2D layout", "${p.width} × ${p.height} (${p.mappedLeds} LEDs placed)")
                        }
                    }
                    p.warnings.forEach { w ->
                        Text("⚠ $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    done?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(it)
                                TextButton(onClick = { onOpenDevice(p.master.id) }) { Text("Open ${p.master.name}") }
                            }
                        }
                    }
                    Button(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (busy) "Working…" else if (hasBackup) "Update ${p.master.name} with this map" else "Set up ${p.master.name} as master")
                    }
                    if (hasBackup) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching { container.standalone.undo(p.master) }
                                        .onSuccess { done = "Restored ${p.master.name}'s original LED settings. It's rebooting." }
                                        .onFailure { error = "Undo failed: ${it.message}" }
                                    busy = false; refresh++
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Undo — restore original settings") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirm) {
        val p = plan
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Set up whole-house mode?") },
            text = {
                Text(
                    "This changes ${p?.master?.name}'s LED outputs, uploads a 2D LED map and reboots it. " +
                        "Its current settings are saved in this app so you can undo. The other controllers aren't changed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val pl = p
                    if (pl != null) {
                    busy = true
                    scope.launch {
                        runCatching { container.standalone.apply(pl) }
                            .onSuccess {
                                done = "Done! ${pl.master.name} is rebooting. Open it, pick a 2D effect (Effects → 2D filter) and it will play across the whole house."
                            }
                            .onFailure { error = "Setup failed: ${it.message}" }
                        busy = false; refresh++
                    }
                    }
                }) { Text("Set up") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeyRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
