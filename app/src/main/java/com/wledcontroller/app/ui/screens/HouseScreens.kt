@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import com.wledcontroller.app.ui.components.LocalPixelStyle
import com.wledcontroller.app.ui.components.drawPixel
import com.wledcontroller.app.ui.components.neighbourGaps
import com.wledcontroller.app.ui.components.pixelRadius
import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.wledcontroller.app.house.HiddenStretch
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.hiddenFlags
import com.wledcontroller.app.house.HouseRenderer
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PropGroup
import com.wledcontroller.app.house.PropType
import com.wledcontroller.app.house.StandalonePlan
import com.wledcontroller.app.house.ledPositions
import com.wledcontroller.app.house.fillGaps
import com.wledcontroller.app.house.formatLength
import com.wledcontroller.app.house.tracedLength
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
internal fun rememberPhoto(path: String?): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    return photo
}

/** The house photo (or a dark canvas) with an overlay drawn in the same normalised coordinates. */
@Composable
internal fun HouseCanvas(
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
fun HouseScreen(onBack: () -> Unit, onEditMap: () -> Unit, onStandalone: () -> Unit, onShows: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val running by container.house.running.collectAsStateWithLifecycle()
    val params by container.house.params.collectAsStateWithLifecycle()
    val photo = rememberPhoto(map.photoPath)
    val leds = remember(map) { map.placedLeds() }
    val gaps = remember(leds, map.aspect) { neighbourGaps(leds, map.aspect) }
    val pixel = LocalPixelStyle.current
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
                leds.forEachIndexed { i, led ->
                    if (led.hidden) return@forEachIndexed // Out of sight in the photo.
                    val o = Offset(led.x * size.width, led.y * size.height)
                    drawPixel(HouseRenderer.color(params, led.x, led.y, t, i, led.orderInRun), o, pixelRadius(pixel, gaps[i]))
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
            Card(onClick = onShows, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Shows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Choreograph your props on a timeline — like xLights, on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
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
fun HouseMapEditor(onBack: () -> Unit, onScan: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val devices by container.devices.list.collectAsStateWithLifecycle()
    val photo = rememberPhoto(map.photoPath)

    var drawingRunId by remember { mutableStateOf<String?>(null) }
    val draft = remember { mutableStateListOf<MapPoint>() }
    // Hidden stretches while tracing: the next tapped point ends one when [nextHidden] is on.
    val draftHidden = remember { mutableStateListOf<HiddenStretch>() }
    var nextHidden by remember { mutableStateOf(false) }
    var askHidden by remember { mutableStateOf<Int?>(null) }
    fun startTracing(id: String) { drawingRunId = id; draft.clear(); draftHidden.clear(); nextHidden = false }
    var editRun by remember { mutableStateOf<LightRun?>(null) }
    var deleteRun by remember { mutableStateOf<LightRun?>(null) }
    var editGroup by remember { mutableStateOf<PropGroup?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    // Setting the scale: tap two points a known distance apart.
    var scaling by remember { mutableStateOf(false) }
    val scaleDraft = remember { mutableStateListOf<MapPoint>() }
    var askScale by remember { mutableStateOf(false) }

    // Per-LED editing state.
    var editingRunId by remember { mutableStateOf<String?>(null) }
    val editPoints = remember { mutableStateListOf<MapPoint?>() }
    val editHidden = remember { mutableStateListOf<Int>() }
    var selectedLed by remember { mutableStateOf<Int?>(null) }
    var placingLed by remember { mutableStateOf<Int?>(null) }

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
    val editingRun = map.runs.firstOrNull { it.id == editingRunId }

    fun startEditing(run: LightRun) {
        editPoints.clear()
        editPoints.addAll(map.positions(run).let { pos -> List(run.count) { pos.getOrNull(it) } })
        editHidden.clear()
        editHidden.addAll(if (run.ledPoints != null) run.hiddenLeds else run.hiddenFlags(map.aspect, map.cmPerHeight).withIndex().filter { it.value }.map { it.index })
        // Hidden LEDs have no spot to drag in the photo.
        editHidden.forEach { if (it in editPoints.indices) editPoints[it] = null }
        selectedLed = null; placingLed = null
        editingRunId = run.id
    }
    fun isMissing(i: Int) = editPoints[i] == null && i !in editHidden
    fun nextMissing(from: Int = 0): Int? = (from until editPoints.size).firstOrNull { isMissing(it) }
        ?: (0 until from.coerceAtMost(editPoints.size)).firstOrNull { isMissing(it) }
    fun lightLed(i: Int) {
        val run = editingRun ?: return
        val host = devices.firstOrNull { it.id == run.deviceId }?.device?.host ?: return
        container.house.identifyLed(host, run.startLed + i)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            drawingRun != null -> "Trace ${drawingRun.name}"
                            scaling -> "Set the scale"
                            editingRun != null -> "Edit ${editingRun.name}"
                            else -> "House map"
                        }
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (drawingRun == null && editingRun == null && !scaling) {
                        IconButton(onClick = { scaling = true; scaleDraft.clear() }) { Icon(Icons.Filled.Straighten, "Set house scale") }
                        IconButton(onClick = onScan) { Icon(Icons.Filled.PhotoCamera, "Scan with camera") }
                        IconButton(onClick = { pickPhoto() }) { Icon(Icons.Filled.Image, "Choose photo") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (map.photoPath == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Map your lights", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Easiest: scan with the camera — each light flashes and the app finds it. Or choose a photo of your house and trace each run by hand (you can also trace on the blank canvas below).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onScan) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Camera scan") }
                            FilledTonalButton(onClick = { pickPhoto() }) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(8.dp)); Text("Photo") }
                        }
                    }
                }
            }
            importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (editingRun != null) {
                LedEditCanvas(
                    map = map, photo = photo, editingRunId = editingRun.id, points = editPoints, selected = selectedLed,
                    onTapEmpty = { p ->
                        val i = placingLed
                        if (i != null && i < editPoints.size) {
                            editPoints[i] = p
                            lightLed(i)
                            placingLed = nextMissing(i + 1)
                        } else selectedLed = null
                    },
                    onTapLed = { i -> selectedLed = i; placingLed = null; lightLed(i) },
                    onMoveLed = { i, p -> editPoints[i] = p; selectedLed = i },
                )
                LedEditControls(
                    points = editPoints, hidden = editHidden.toSet(), selected = selectedLed, placing = placingLed,
                    onRemove = { i -> editPoints[i] = null; selectedLed = null },
                    onHide = { i ->
                        editPoints[i] = null
                        if (i !in editHidden) editHidden.add(i)
                        selectedLed = null
                        if (placingLed == i) { placingLed = nextMissing(i + 1)?.takeIf { it != i }; placingLed?.let { lightLed(it) } }
                    },
                    onUnhide = { i -> editHidden.remove(i); placingLed = i; selectedLed = null; lightLed(i) },
                    onFillGaps = {
                        // Never guess across a hidden stretch: fill each visible part on its own.
                        val out = editPoints.toMutableList()
                        var from = 0
                        for (i in 0..out.size) {
                            if (i == out.size || i in editHidden) {
                                editPoints.subList(from, i).toList().fillGaps().forEachIndexed { k, p -> out[from + k] = p }
                                from = i + 1
                            }
                        }
                        editPoints.clear(); editPoints.addAll(out)
                    },
                    onPlaceMissing = { placingLed = nextMissing(); selectedLed = null; placingLed?.let { lightLed(it) } },
                    onStopPlacing = { placingLed = null },
                    onSkip = { i -> placingLed = nextMissing(i + 1)?.takeIf { it != i }; placingLed?.let { lightLed(it) } },
                    onAddAtEnd = { editPoints.add(null); placingLed = editPoints.lastIndex; selectedLed = null; lightLed(editPoints.lastIndex) },
                    onRemoveLast = {
                        if (editPoints.size > 1) {
                            editPoints.removeAt(editPoints.lastIndex); editHidden.removeAll { it >= editPoints.size }
                            selectedLed = null; placingLed = null
                        }
                    },
                    onCancel = { editingRunId = null },
                    onDone = {
                        saveRun(editingRun.copy(count = editPoints.size, ledPoints = editPoints.toList(), hiddenLeds = editHidden.toSet(), hidden = emptyList()))
                        editingRunId = null
                    },
                )
                return@Column
            }

            val pixel = LocalPixelStyle.current
            HouseCanvas(
                map, photo,
                onTap = if (drawingRun != null) { { p ->
                    draft.add(p)
                    if (nextHidden && draft.size >= 2) {
                        val k = draft.size - 2
                        draftHidden.add(HiddenStretch(k)); askHidden = k; nextHidden = false
                    }
                } }
                else if (scaling) { { p -> if (scaleDraft.size < 2) scaleDraft.add(p); if (scaleDraft.size == 2) askScale = true } }
                else null,
            ) {
                val stroke = 2.dp.toPx()
                // The scale: a dashed yellow line between the two measured points.
                val scalePts = if (scaling) scaleDraft.toList() else listOfNotNull(map.scaleA, map.scaleB)
                if (scalePts.size == 2) drawRun(scalePts, Color(0xFFFFE06B), stroke * 0.8f, dashed = true)
                scalePts.forEach { p -> drawCircle(Color(0xFFFFE06B), 4.dp.toPx(), Offset(p.x * size.width, p.y * size.height)) }
                map.runs.forEachIndexed { r, run ->
                    if (run.id == drawingRunId) return@forEachIndexed
                    val col = RunColors[r % RunColors.size]
                    val positions = map.positions(run)
                    val placed = positions.filterNotNull()
                    if (run.isPlacedPerLed) drawRun(placed, col, stroke, dashed = false)
                    else drawTracedRun(run.points, run.hidden.map { it.after }.toSet(), col, stroke)
                    val runGaps = neighbourGaps(positions, map.aspect)
                    val hiddenLed = run.hiddenFlags(map.aspect, map.cmPerHeight)
                    positions.forEachIndexed { i, p ->
                        if (p == null) return@forEachIndexed
                        val o = Offset(p.x * size.width, p.y * size.height)
                        val r = pixelRadius(pixel, runGaps[i])
                        // Out-of-sight lights: hollow rings.
                        if (hiddenLed.getOrElse(i) { false }) drawCircle(col.copy(alpha = 0.7f), r, o, style = Stroke(1.dp.toPx()))
                        else drawCircle(col, r, o)
                    }
                    placed.firstOrNull()?.let { s ->
                        drawCircle(Color.White, 6.dp.toPx(), Offset(s.x * size.width, s.y * size.height), style = Stroke(stroke))
                    }
                }
                if (drawingRun != null) {
                    drawTracedRun(draft.toList(), draftHidden.map { it.after }.toSet(), Color.White, stroke)
                    val previewRun = drawingRun.copy(points = draft.toList(), ledPoints = null, hidden = draftHidden.toList())
                    val preview = map.positions(previewRun)
                    val previewHidden = previewRun.hiddenFlags(map.aspect, map.cmPerHeight)
                    val previewGaps = neighbourGaps(preview, map.aspect)
                    preview.forEachIndexed { i, p ->
                        if (p == null) return@forEachIndexed
                        val o = Offset(p.x * size.width, p.y * size.height)
                        val r = pixelRadius(pixel, previewGaps[i])
                        if (previewHidden.getOrElse(i) { false }) drawCircle(Color.White.copy(alpha = 0.6f), r, o, style = Stroke(1.dp.toPx()))
                        else drawCircle(Color.White, r, o)
                    }
                    draft.firstOrNull()?.let { s -> drawCircle(Color.Red, 6.dp.toPx(), Offset(s.x * size.width, s.y * size.height)) }
                }
            }

            if (scaling) {
                Text(
                    "Tap two points on the photo that you know the real distance between — the two ends of a wall, the garage door or a window. Longer distances give a more accurate scale.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scaleDraft.clear() }, enabled = scaleDraft.isNotEmpty()) { Text("Start again") }
                    TextButton(onClick = { scaling = false; scaleDraft.clear() }) { Text("Cancel") }
                }
                return@Column
            }

            ScaleSummary(map, onSet = { scaling = true; scaleDraft.clear() })

            if (drawingRun != null) {
                Text(
                    "Tap along the lights, starting at the end where LED ${drawingRun.startLed} is (usually the end nearest the controller). Add a point at every corner.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                FilterChip(
                    selected = nextHidden,
                    enabled = draft.isNotEmpty(),
                    onClick = { nextHidden = !nextHidden },
                    label = { Text(if (nextHidden) "Tap where the lights come back into view" else "Lights go out of sight here") },
                    leadingIcon = { Icon(Icons.Filled.VisibilityOff, null, Modifier.size(18.dp)) },
                )
                Text(
                    "Lights wrap round a corner or go behind something? Tap the last point you can see, turn this on, then tap where they reappear. You'll be asked how long the hidden part is.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (draft.isNotEmpty()) draft.removeAt(draft.lastIndex)
                        draftHidden.removeAll { it.after >= draft.size - 1 }
                    }, enabled = draft.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null); Spacer(Modifier.width(6.dp)); Text("Undo")
                    }
                    TextButton(onClick = { drawingRunId = null; draft.clear(); draftHidden.clear() }) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(enabled = draft.size >= 2, onClick = {
                        saveRun(drawingRun.copy(points = draft.toList(), ledPoints = null, hidden = draftHidden.toList(), hiddenLeds = emptySet()))
                        drawingRunId = null; draft.clear(); draftHidden.clear()
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
                                    "${run.prop.label} · ${device?.device?.name ?: "Missing device"} · LEDs ${run.startLed}–${run.endLed - 1}" +
                                        when {
                                            run.isPlacedPerLed -> " · ${run.placedCount} of ${run.count} placed"
                                            run.points.size < 2 -> " · not traced yet"
                                            else -> ""
                                        } +
                                        (run.spacingCm?.takeIf { !run.isPlacedPerLed }?.let { " · ${formatLength(it, map.useInches)} apart" } ?: "") +
                                        run.hiddenSummary(map),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { editRun = run }) { Icon(Icons.Filled.Edit, "Edit run") }
                            IconButton(onClick = { deleteRun = run }) { Icon(Icons.Outlined.Delete, "Delete run") }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(enabled = run.placedCount > 0, onClick = { startEditing(run) }) { Text("Edit LEDs") }
                            TextButton(onClick = { startTracing(run.id) }) {
                                Text(if (run.points.size < 2 && !run.isPlacedPerLed) "Trace" else "Retrace")
                            }
                            TextButton(enabled = run.points.size >= 2 && !run.isPlacedPerLed, onClick = { saveRun(run.copy(points = run.points.reversed(), hidden = run.hidden.map { it.copy(after = run.points.size - 2 - it.after) })) }) {
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

            SectionTitle("Prop groups") {
                TextButton(onClick = { editGroup = PropGroup(UUID.randomUUID().toString(), "", emptyList()) }, enabled = map.runs.isNotEmpty()) {
                    Icon(Icons.Filled.Add, null); Text("Add")
                }
            }
            if (map.groups.isEmpty()) {
                Text(
                    "Group props to animate them together in shows — e.g. \"All arches\" or \"Roof\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            map.groups.forEach { g ->
                Surface(
                    onClick = { editGroup = g },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(g.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            map.runs.filter { it.id in g.runIds }.joinToString { it.name }.ifEmpty { "No props" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editRun?.let { run ->
        RunDialog(run, map, onDismiss = { editRun = null }) { updated ->
            val isNew = map.runs.none { it.id == updated.id }
            saveRun(updated)
            editRun = null
            if (isNew) startTracing(updated.id)
        }
    }
    val askingAbout = draftHidden.firstOrNull { it.after == askHidden }
    if (askingAbout != null) {
        HiddenStretchDialog(askingAbout, onDismiss = { askHidden = null }) { updated ->
            val i = draftHidden.indexOf(askingAbout)
            if (i >= 0) draftHidden[i] = updated
            askHidden = null
        }
    }
    if (askScale && scaleDraft.size == 2) {
        ScaleDialog(
            inches = map.useInches,
            current = map.scaleCm,
            onDismiss = { askScale = false; scaleDraft.clear() },
        ) { cm, inches ->
            val a = scaleDraft[0]; val b = scaleDraft[1]
            scope.launch { container.store.updateHouseMap { it.copy(scaleA = a, scaleB = b, scaleCm = cm, useInches = inches) } }
            askScale = false; scaling = false; scaleDraft.clear()
        }
    }
    editGroup?.let { g ->
        GroupDialog(
            group = g, runs = map.runs, onDismiss = { editGroup = null },
            onDelete = {
                scope.launch { container.store.updateHouseMap { m -> m.copy(groups = m.groups.filterNot { it.id == g.id }) } }
                editGroup = null
            },
        ) { updated ->
            scope.launch {
                container.store.updateHouseMap { m ->
                    m.copy(groups = if (m.groups.any { it.id == updated.id }) m.groups.map { if (it.id == updated.id) updated else it } else m.groups + updated)
                }
            }
            editGroup = null
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

/** A traced run: solid where the lights can be seen, a faint dashed line across hidden stretches. */
private fun DrawScope.drawTracedRun(points: List<MapPoint>, hiddenAfter: Set<Int>, color: Color, stroke: Float) {
    points.zipWithNext().forEachIndexed { k, (a, b) ->
        val hidden = k in hiddenAfter
        drawLine(
            color.copy(alpha = if (hidden) 0.45f else 0.8f),
            Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height),
            strokeWidth = stroke,
            pathEffect = if (hidden) PathEffect.dashPathEffect(floatArrayOf(6f, 10f)) else null,
        )
    }
}

/** " · 6 ft hidden" etc. for the run list. */
private fun LightRun.hiddenSummary(map: HouseMap): String {
    if (ledPoints != null) return if (hiddenLeds.isEmpty()) "" else " · ${hiddenLeds.size} hidden"
    if (hidden.isEmpty()) return ""
    val parts = hidden.map { h ->
        h.lengthCm?.let { formatLength(it, true) } ?: h.leds?.let { "$it LEDs" } ?: "?"
    }
    return " · hidden: " + parts.joinToString(", ")
}

/** How much of the run is out of sight: a length in feet and inches, a number of LEDs, or both. */
@Composable
private fun HiddenStretchDialog(stretch: HiddenStretch, onDismiss: () -> Unit, onSave: (HiddenStretch) -> Unit) {
    val totalIn = stretch.lengthCm?.let { Math.round(it / 2.54f) }
    var feet by remember { mutableStateOf(totalIn?.let { (it / 12).toString() } ?: "") }
    var inches by remember { mutableStateOf(totalIn?.let { (it % 12).takeIf { r -> r > 0 }?.toString() } ?: "") }
    var leds by remember { mutableStateOf(stretch.leds?.toString() ?: "") }
    val lengthIn = (feet.toIntOrNull() ?: 0) * 12 + (inches.replace(',', '.').toFloatOrNull() ?: 0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden stretch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "How long is the part you can't see in the photo (e.g. round the porch)? Measure along the lights. Or, if you know it, how many LEDs are hidden.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        feet, { feet = it.filter(Char::isDigit).take(4) }, label = { Text("Feet") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        inches, { inches = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5) }, label = { Text("Inches") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    leds, { leds = it.filter(Char::isDigit).take(5) }, label = { Text("Hidden LEDs (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    "Leave both empty and the straight line on the photo is used as a guess.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(stretch.copy(lengthCm = if (lengthIn > 0f) lengthIn * 2.54f else null, leds = leds.toIntOrNull()?.takeIf { it > 0 }))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
    var prop by remember { mutableStateOf(run.prop) }
    val device = devices.firstOrNull { it.id == deviceId }
    val ledCount = device?.snapshot?.value?.info?.ledCount ?: 0
    // Default a new run to the LEDs on this controller that no other run uses yet.
    val usedEnd = map.runs.filter { it.deviceId == deviceId && it.id != run.id }.maxOfOrNull { it.endLed } ?: 0
    var start by remember(deviceId) { mutableStateOf(if (run.count > 0 && deviceId == run.deviceId) run.startLed.toString() else usedEnd.toString()) }
    var count by remember(deviceId) {
        mutableStateOf(if (run.count > 0 && deviceId == run.deviceId) run.count.toString() else (ledCount - usedEnd).coerceAtLeast(1).toString())
    }
    val s = start.toIntOrNull(); val c = count.toIntOrNull()

    // LED spacing: spread along the traced line, or a fixed real distance apart.
    var fixedSpacing by remember { mutableStateOf(run.spacingCm != null) }
    val hiddenList = remember { mutableStateListOf(*run.hidden.toTypedArray()) }
    var editingHidden by remember { mutableStateOf<HiddenStretch?>(null) }
    var spacingInches by remember { mutableStateOf(map.useInches) }
    fun shown(cm: Float, inches: Boolean) = "%.2f".format(if (inches) cm / 2.54f else cm).trimEnd('0').trimEnd('.')
    var spacingText by remember { mutableStateOf(run.spacingCm?.let { shown(it, map.useInches) } ?: "") }
    val spacingCm = spacingText.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }?.let { if (spacingInches) it * 2.54f else it }
    val valid = name.isNotBlank() && device != null && s != null && c != null && c > 0 && (!fixedSpacing || spacingCm != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (run.count == 0) "New light run" else "Edit light run") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Roofline)") }, singleLine = true)
                Text("Prop type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PropType.entries.forEach { p ->
                        FilterChip(selected = prop == p, onClick = { prop = p }, label = { Text(p.label) })
                    }
                }
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

                Text("LED spacing", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !fixedSpacing, onClick = { fixedSpacing = false }, label = { Text("Spread along line") })
                    FilterChip(selected = fixedSpacing, onClick = { fixedSpacing = true }, label = { Text("Fixed spacing") })
                }
                if (fixedSpacing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            spacingText, { spacingText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(7) },
                            label = { Text("Centre to centre") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                        )
                        FilterChip(selected = spacingInches, onClick = {
                            if (!spacingInches) { spacingCm?.let { spacingText = shown(it, true) }; spacingInches = true }
                        }, label = { Text("in") })
                        FilterChip(selected = !spacingInches, onClick = {
                            if (spacingInches) { spacingCm?.let { spacingText = shown(it, false) }; spacingInches = false }
                        }, label = { Text("cm") })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Bullets 4 in" to 10.16f, "C9 12 in" to 30.48f, "Strip 30/m" to 100f / 30f,
                            "Strip 60/m" to 100f / 60f, "Strip 144/m" to 100f / 144f,
                        ).forEach { (label, cm) ->
                            AssistChip(onClick = { spacingText = shown(cm, spacingInches) }, label = { Text(label) })
                        }
                    }
                    val scale = map.cmPerHeight
                    val note = when {
                        scale == null -> "Set the house scale on the map (the ruler button) so the app knows how big your house is. Until then this run's lights spread along the line."
                        run.points.size >= 2 && c != null && spacingCm != null -> {
                            val line = run.tracedLength(map.aspect) * scale
                            val span = spacingCm * (c - 1).coerceAtLeast(0)
                            "Traced line: ${formatLength(line, true)}. $c LEDs at this spacing span ${formatLength(span, true)}" +
                                if (span > line * 1.02f) " — they carry on past the end of the line." else "."
                        }
                        else -> "Lights are placed this far apart from the start of the traced line."
                    }
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (hiddenList.isNotEmpty()) {
                    Text("Hidden stretches", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    hiddenList.forEach { h ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Between points ${h.after + 1} and ${h.after + 2}: " + listOfNotNull(
                                    h.lengthCm?.let { formatLength(it, true) }, h.leds?.let { "$it LEDs" },
                                ).ifEmpty { listOf("straight-line guess") }.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { editingHidden = h }) { Icon(Icons.Filled.Edit, "Edit hidden stretch") }
                            IconButton(onClick = { hiddenList.remove(h) }) { Icon(Icons.Outlined.Delete, "Show this stretch") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(run.copy(name = name.trim(), deviceId = deviceId, startLed = s!!, count = c!!, prop = prop, spacingCm = if (fixedSpacing) spacingCm else null, hidden = hiddenList.toList())) }) {
                Text(if (run.count == 0) "Next: trace it" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    editingHidden?.let { h ->
        HiddenStretchDialog(h, onDismiss = { editingHidden = null }) { updated ->
            val i = hiddenList.indexOf(h)
            if (i >= 0) hiddenList[i] = updated
            editingHidden = null
        }
    }
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

// ================================================================== per-LED editing

/**
 * The map with one run's LEDs editable: pinch to zoom, drag with two fingers (or one, when
 * zoomed and not on an LED) to pan, drag an LED to move it, tap an LED to select it.
 */
@Composable
private fun LedEditCanvas(
    map: HouseMap,
    photo: ImageBitmap?,
    editingRunId: String,
    points: List<MapPoint?>,
    selected: Int?,
    onTapEmpty: (MapPoint) -> Unit,
    onTapLed: (Int) -> Unit,
    onMoveLed: (Int, MapPoint) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val tapEmpty by rememberUpdatedState(onTapEmpty)
    val tapLed by rememberUpdatedState(onTapLed)
    val moveLed by rememberUpdatedState(onMoveLed)
    val currentPoints by rememberUpdatedState(points)
    val others = remember(map, editingRunId) {
        map.runs.filter { it.id != editingRunId }.flatMap { map.positions(it).filterNotNull() }
    }
    val pixel = LocalPixelStyle.current
    val gaps = remember(points, map.aspect) { neighbourGaps(points, map.aspect) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(map.aspect.coerceIn(0.4f, 3f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101218))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = pan.x; translationY = pan.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = 0.75f)
            Canvas(Modifier.fillMaxSize()) {
                val r = pixelRadius(pixel, scale = 1f / scale)
                others.forEach { p -> drawCircle(Color.White.copy(alpha = 0.25f), r * 0.8f, Offset(p.x * size.width, p.y * size.height)) }
                // Thin line through the run in LED order makes misplaced LEDs obvious.
                val placed = points.withIndex().filter { it.value != null }
                placed.zipWithNext { a, b ->
                    if (b.index == a.index + 1) drawLine(
                        Color(0xFFFFB86B).copy(alpha = 0.6f),
                        Offset(a.value!!.x * size.width, a.value!!.y * size.height),
                        Offset(b.value!!.x * size.width, b.value!!.y * size.height),
                        strokeWidth = 1.dp.toPx() / scale,
                    )
                }
                placed.forEach { (i, p) ->
                    val o = Offset(p!!.x * size.width, p.y * size.height)
                    val col = when {
                        i == selected -> Color.White
                        i == 0 -> Color.Red
                        else -> Color(0xFFFFB86B)
                    }
                    val ri = pixelRadius(pixel, gaps.getOrElse(i) { com.wledcontroller.app.ui.components.NO_NEIGHBOUR }, scale = 1f / scale)
                    drawCircle(col, if (i == selected) ri * 1.6f else ri, o)
                    if (i == selected) drawCircle(Color.White, maxOf(ri * 3f, 6.dp.toPx() / scale), o, style = Stroke(1.5.dp.toPx() / scale))
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun clampPan(p: Offset) = Offset(
                        p.x.coerceIn(size.width - size.width * scale, 0f),
                        p.y.coerceIn(size.height - size.height * scale, 0f),
                    )
                    fun toMap(screen: Offset): MapPoint {
                        val c = (screen - pan) / scale
                        return MapPoint((c.x / size.width).coerceIn(0f, 1f), (c.y / size.height).coerceIn(0f, 1f))
                    }
                    fun hit(screen: Offset): Int? {
                        val radius = 24.dp.toPx()
                        var best: Int? = null
                        var bestD = Float.MAX_VALUE
                        currentPoints.forEachIndexed { i, p ->
                            if (p == null) return@forEachIndexed
                            val s = Offset(p.x * size.width, p.y * size.height) * scale + pan
                            val d = (s - screen).getDistance()
                            if (d < radius && d < bestD) { bestD = d; best = i }
                        }
                        return best
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val target = hit(down.position)
                        var dragging = false
                        var multi = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                multi = true
                                val zoom = event.calculateZoom()
                                val centroid = event.calculateCentroid()
                                val newScale = (scale * zoom).coerceIn(1f, 8f)
                                pan = clampPan(centroid - (centroid - pan) * (newScale / scale) + event.calculatePan())
                                scale = newScale
                                event.changes.forEach { it.consume() }
                            } else if (!multi) {
                                val ch = pressed.first()
                                if (!dragging && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                                if (dragging) {
                                    if (target != null) moveLed(target, toMap(ch.position))
                                    else if (scale > 1f) pan = clampPan(pan + (ch.position - ch.previousPosition))
                                    ch.consume()
                                }
                            }
                        }
                        if (!dragging && !multi) {
                            if (target != null) tapLed(target) else tapEmpty(toMap(down.position))
                        }
                    }
                }
        )
    }
}

@Composable
private fun LedEditControls(
    points: List<MapPoint?>,
    hidden: Set<Int>,
    selected: Int?,
    placing: Int?,
    onRemove: (Int) -> Unit,
    onHide: (Int) -> Unit,
    onUnhide: (Int) -> Unit,
    onFillGaps: () -> Unit,
    onPlaceMissing: () -> Unit,
    onStopPlacing: () -> Unit,
    onSkip: (Int) -> Unit,
    onAddAtEnd: () -> Unit,
    onRemoveLast: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val placed = points.count { it != null }
    val hiddenCount = hidden.count { it in points.indices }
    val missing = points.size - placed - hiddenCount
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$placed of ${points.size} LEDs placed" + (if (hiddenCount > 0) " · $hiddenCount hidden" else "") + if (missing > 0) " · $missing missing" else "",
            style = MaterialTheme.typography.titleSmall,
        )
        when {
            placing != null -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Tap where LED ${placing + 1} is", fontWeight = FontWeight.SemiBold)
                        Text("It's lit up on your house now. Out of sight from the photo? Mark it hidden.", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { onHide(placing) }) { Text("Hidden") }
                            TextButton(onClick = { onSkip(placing) }) { Text("Skip") }
                            TextButton(onClick = onStopPlacing) { Text("Stop placing") }
                        }
                    }
                }
            }
            selected != null -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("LED ${selected + 1} — drag it to move, or", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onHide(selected) }) { Text("Hidden") }
                        TextButton(onClick = { onRemove(selected) }) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Remove")
                        }
                    }
                }
            }
            else -> Text(
                "Pinch to zoom. Drag a light to move it; tap one to select it (it lights up on your house).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onFillGaps, enabled = missing > 0) { Text("Fill gaps") }
            OutlinedButton(onClick = onPlaceMissing, enabled = missing > 0 && placing == null) { Text("Place missing") }
            OutlinedButton(onClick = onAddAtEnd) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text("Add LED") }
            OutlinedButton(onClick = onRemoveLast, enabled = points.size > 1) { Text("Remove last LED") }
        }
        if (hiddenCount > 0) {
            val ranges = hidden.filter { it in points.indices }.sorted().fold(mutableListOf<IntRange>()) { acc, i ->
                val last = acc.lastOrNull()
                if (last != null && last.last == i - 1) acc[acc.lastIndex] = last.first..i else acc += i..i
                acc
            }
            Text(
                "Hidden: " + ranges.joinToString { r -> if (r.first == r.last) "LED ${r.first + 1}" else "LEDs ${r.first + 1}–${r.last + 1}" } +
                    ". They're not shown on the photo, but effects still flow through them.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { hidden.filter { it in points.indices }.minOrNull()?.let(onUnhide) }) { Text("Place a hidden LED after all") }
        }
        Text(
            "Fill gaps guesses missed lights between found ones. Place missing walks you through each one. Add / Remove last LED changes how many lights this run has.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone) { Text("Save") }
        }
    }
}

@Composable
private fun GroupDialog(
    group: PropGroup,
    runs: List<LightRun>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (PropGroup) -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(group.runIds) } }
    val isNew = group.name.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New prop group" else "Edit group") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. All arches)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                runs.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = r.id in chosen,
                            onCheckedChange = { if (it) chosen.add(r.id) else chosen.remove(r.id) },
                        )
                        Text("${r.name} · ${r.prop.label}")
                    }
                }
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete group", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && chosen.isNotEmpty(), onClick = {
                onSave(group.copy(name = name.trim(), runIds = runs.map { it.id }.filter { it in chosen }))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The map's scale, or a prompt to set one. */
@Composable
private fun ScaleSummary(map: HouseMap, onSet: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Straighten, null, Modifier.size(18.dp), tint = Color(0xFFFFE06B))
        Spacer(Modifier.width(8.dp))
        Text(
            map.scaleCm?.takeIf { map.cmPerHeight != null }?.let { "Scale: ${formatLength(it, true)} between the yellow marks" }
                ?: "No scale yet — set one so runs can use their real LED spacing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSet) { Text(if (map.cmPerHeight != null) "Change" else "Set scale") }
    }
}

/** Asks for the real distance between the two points just tapped, in feet and inches. */
@Composable
private fun ScaleDialog(inches: Boolean, current: Float?, onDismiss: () -> Unit, onSave: (cm: Float, inches: Boolean) -> Unit) {
    val currentIn = current?.let { Math.round(it / 2.54f) }
    var feet by remember { mutableStateOf(currentIn?.let { (it / 12).toString() } ?: "") }
    var extraIn by remember { mutableStateOf(currentIn?.let { (it % 12).takeIf { r -> r > 0 }?.toString() } ?: "") }
    val ft = feet.toIntOrNull() ?: 0
    val inch = extraIn.replace(',', '.').toFloatOrNull() ?: 0f
    val totalIn = ft * 12 + inch
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How far apart are those points?") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    feet, { feet = it.filter(Char::isDigit).take(4) },
                    label = { Text("Feet") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    extraIn, { extraIn = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5) },
                    label = { Text("Inches") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = totalIn > 0f, onClick = { onSave(totalIn * 2.54f, true) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
