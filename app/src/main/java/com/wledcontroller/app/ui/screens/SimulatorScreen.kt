@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.LightProfile
import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.formatLength
import com.wledcontroller.app.house.pivot
import com.wledcontroller.app.house.transformed
import com.wledcontroller.app.house.VendorCatalog
import com.wledcontroller.app.house.XlightsModels
import com.wledcontroller.app.house.placedLeds
import com.wledcontroller.app.sim.SimDevice
import com.wledcontroller.app.sim.SimHub
import com.wledcontroller.app.ui.components.LocalPixelStyle
import com.wledcontroller.app.ui.components.drawPixel
import com.wledcontroller.app.ui.components.neighbourGaps
import com.wledcontroller.app.ui.components.pixelRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Time for live drawing, ticking every frame while shown. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { now = System.currentTimeMillis() } }
    return now
}

private class SimLed(val device: SimDevice, val deviceId: String, val runId: String, val index: Int, val x: Float, val y: Float, val bullet: Boolean, val gap: Float)

/** Every mapped LED of a simulated controller, with what it looks like. */
@Composable
private fun rememberSimLeds(map: HouseMap): List<SimLed> {
    val container = LocalAppContainer.current
    val saved by container.store.devices.collectAsStateWithLifecycle(emptyList())
    val sims by SimHub.devices.collectAsStateWithLifecycle()
    val profiles by container.store.lightProfiles.collectAsStateWithLifecycle(emptyMap())
    return remember(map, saved, sims, profiles) {
        val hostOf = saved.associate { it.id to it.host }
        val placed = map.placedLeds()
        val gaps = neighbourGaps(placed, map.aspect)
        placed.mapIndexedNotNull { i, l ->
            // Lights out of sight in the photo aren't drawn (their controller still runs them).
            if (l.hidden) null else SimHub.find(hostOf[l.deviceId])?.let { d ->
                val bullet = LightProfile.parse(profiles[LightProfile.deviceKey(l.deviceId)]) == LightProfile.BULLETS
                SimLed(d, l.deviceId, map.runs[l.runIndex].id, l.index, l.x, l.y, bullet, gaps[i])
            }
        }
    }
}

/** Glowing lights: a soft halo added to the scene, then the bulb itself. */
private fun DrawScope.glow(color: Int, center: Offset, radius: Float, bullet: Boolean) {
    if (color and 0xFFFFFF == 0) {
        drawCircle(Color.White.copy(alpha = 0.10f), radius * 0.6f, center)
        return
    }
    val c = Color(color or (0xFF shl 24))
    drawCircle(c.copy(alpha = if (bullet) 0.26f else 0.20f), radius * (if (bullet) 3f else 2.5f), center, blendMode = BlendMode.Plus)
    drawCircle(c.copy(alpha = 0.5f), radius * 1.5f, center, blendMode = BlendMode.Plus)
    drawCircle(c, radius, center)
}

/**
 * The virtual house with every simulated light drawn live. While [arranging], tapping a prop
 * picks it ([onSelectRun]) and the picked one ([selectedRunId]) follows drags, pinches and taps.
 */
@Composable
fun SimHouseView(
    modifier: Modifier = Modifier,
    bulbSize: Float = 1f,
    onTapDevice: ((String) -> Unit)? = null,
    arranging: Boolean = false,
    selectedRunId: String? = null,
    onSelectRun: (String) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val stored by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    // The prop being moved, ahead of the saved map so its lights follow the finger.
    var live by remember { mutableStateOf<LightRun?>(null) }
    suspend fun save(run: LightRun) = container.store.updateHouseMap { m -> m.copy(runs = m.runs.map { if (it.id == run.id) run else it }) }
    LaunchedEffect(selectedRunId) { live?.let { save(it) }; live = null }
    // Saved once the fingers rest.
    LaunchedEffect(live) {
        val l = live ?: return@LaunchedEffect
        delay(350); save(l)
        // Saved: go back to the map, so changes made elsewhere (like mirroring) show.
        if (live === l) live = null
    }
    val map = live?.let { l -> stored.copy(runs = stored.runs.map { if (it.id == l.id) l else it }) } ?: stored
    val selectedStored by rememberUpdatedState(stored.runs.firstOrNull { it.id == selectedRunId })
    val select by rememberUpdatedState(onSelectRun)
    val photo = rememberPhoto(map.photoPath)
    val leds = rememberSimLeds(map)
    val now = rememberNow()
    val tap by rememberUpdatedState(onTapDevice)
    val pixel = LocalPixelStyle.current
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(map.aspect.coerceIn(0.4f, 3f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070B1C))
    ) {
        if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Canvas(
            Modifier.fillMaxSize().pointerInput(leds, arranging) {
                detectTapGestures { o ->
                    val near = leds.minByOrNull { l ->
                        val dx = l.x * size.width - o.x; val dy = l.y * size.height - o.y
                        dx * dx + dy * dy
                    }?.takeIf { l ->
                        val dx = l.x * size.width - o.x; val dy = l.y * size.height - o.y
                        dx * dx + dy * dy < (40.dp.toPx() * 40.dp.toPx())
                    }
                    if (!arranging) { near?.let { tap?.invoke(it.deviceId) }; return@detectTapGestures }
                    val picked = live ?: selectedStored
                    // A tap on another prop picks it; anywhere else sends the picked one there.
                    if (near != null && near.runId != picked?.id) select(near.runId)
                    else if (picked != null && near == null) {
                        val c = picked.pivot() ?: return@detectTapGestures
                        live = picked.transformed(o.x / size.width - c.x, o.y / size.height - c.y)
                    }
                }
            }.then(
                if (!arranging) Modifier
                else Modifier.pointerInput(selectedRunId) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val run = live ?: selectedStored ?: return@detectTransformGestures
                        live = run.transformed(pan.x / size.width, pan.y / size.height, zoom)
                    }
                }
            )
        ) {
            val frames = HashMap<SimDevice, IntArray>()
            for (l in leds) {
                val f = frames.getOrPut(l.device) { l.device.frame(now) }
                val r = pixelRadius(pixel, l.gap, scale = bulbSize)
                glow(f.getOrElse(l.index) { 0 }, Offset(l.x * size.width, l.y * size.height), r, l.bullet)
            }
            if (arranging) {
                // A dashed frame round the picked prop.
                val mine = leds.filter { it.runId == selectedRunId }
                if (mine.isNotEmpty()) {
                    val pad = 8.dp.toPx()
                    val left = mine.minOf { it.x } * size.width - pad; val top = mine.minOf { it.y } * size.height - pad
                    val right = mine.maxOf { it.x } * size.width + pad; val bottom = mine.maxOf { it.y } * size.height + pad
                    drawRect(
                        Color.White, Offset(left, top), Size(right - left, bottom - top),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                }
            }
        }
        if (leds.isEmpty()) {
            Text(
                "No simulated lights are on the map yet.",
                Modifier.align(Alignment.Center).padding(16.dp),
                color = Color.White, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * One controller's LEDs laid out on the bench, at the chosen light size; long
 * strips wrap onto more rows.
 */
@Composable
private fun SimStrip(device: SimDevice, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val pixel = LocalPixelStyle.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pitchDp = pixel.rowPitchDp
        val perRow = (maxWidth.value / pitchDp).toInt().coerceAtLeast(1)
        val n = device.localLeds
        val rows = ((n + perRow - 1) / perRow).coerceAtLeast(1)
        val rowDp = maxOf(pitchDp, pixel.sizeDp, 4f)
        Canvas(Modifier.fillMaxWidth().height((rows * rowDp + 6).dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF0B0D14))) {
            val f = device.frame(now)
            val count = n.coerceAtMost(f.size)
            val pitch = pitchDp.dp.toPx(); val rowH = rowDp.dp.toPx(); val top = 3.dp.toPx()
            val r = pixel.sizeDp.dp.toPx() / 2f
            for (i in 0 until count) {
                val o = Offset((i % perRow) * pitch + pitch / 2, top + (i / perRow) * rowH + rowH / 2)
                drawPixel(f[i], o, r, glow = 0.6f)
            }
        }
    }
}

/** A small live view of the virtual house that floats over the other screens. */
@Composable
fun BoxScope.SimMiniView(onOpen: () -> Unit, onClose: () -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF070B1C),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .navigationBarsPadding()
            .padding(start = 12.dp, bottom = 96.dp)
            .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .width(176.dp)
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    dx += drag.x; dy += drag.y
                }
            },
    ) {
        Box {
            SimHouseView(bulbSize = 0.6f)
            Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { onOpen() } })
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Hide live view", Modifier.size(14.dp), tint = Color.White) }
        }
    }
}

// ================================================================== screen

@Composable
fun SimulatorScreen(
    onBack: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenHouse: () -> Unit,
    onOpenShows: () -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val container = LocalAppContainer.current
    val sim = container.simulator
    val active by sim.active.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var tuning by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var addingProp by remember { mutableStateOf(false) }
    var arranging by remember { mutableStateOf(false) }
    var selectedRun by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<SavedDevice?>(null) }
    val miniView by sim.miniView.collectAsStateWithLifecycle()

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = label; error = null
            runCatching { block() }.onFailure { error = it.message ?: it.toString() }
            busy = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (active) "Simulator" else "Light show simulator") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (active) {
                        IconButton(onClick = { tuning = true }) { Icon(Icons.Filled.Tune, "Light size") }
                        IconButton(onClick = { sim.setMiniView(!miniView) }) {
                            Icon(
                                Icons.Filled.PictureInPicture, if (miniView) "Hide floating live view" else "Show floating live view",
                                tint = if (miniView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Reset the virtual house") }, onClick = { menu = false; confirmReset = true })
                                DropdownMenuItem(
                                    text = { Text("Leave simulator") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                                    onClick = { menu = false; run("Putting your lights back…") { sim.leave() } },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text(it)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            if (!active) Intro(enabled = busy == null, onStart = { run("Building the virtual house…") { sim.enter() } })
            else ActiveSimulator(
                onOpenDevice = onOpenDevice, onOpenGroup = onOpenGroup, onOpenHouse = onOpenHouse, onOpenShows = onOpenShows,
                onOpenShow = onOpenShow, onOpenSchedule = onOpenSchedule, onOpenMap = onOpenMap,
                onAdd = { adding = true }, onAddProp = { addingProp = true }, onRemove = { removing = it },
                arranging = arranging, selectedRunId = selectedRun, onSelectRun = { selectedRun = it },
                onArrange = { on -> arranging = on; if (!on) selectedRun = null; note = null },
                onLeave = { run("Putting your lights back…") { sim.leave() } },
                enabled = busy == null,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (tuning) {
        // No scrim, so the house above stays bright while you adjust.
        ModalBottomSheet(onDismissRequest = { tuning = false }, scrimColor = Color.Transparent) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) { PixelStyleSetting() }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset the virtual house?") },
            text = { Text("Everything you've changed in the simulator goes back to the demo. Your real lights and setup aren't affected.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; run("Rebuilding the demo…") { sim.reset() } }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    if (adding) AddSimControllerDialog(onDismiss = { adding = false }) { name, leds, profile, rgbw ->
        adding = false
        run("Adding $name…") { sim.addController(name, leds, profile, rgbw) }
    }
    if (addingProp) VendorBrowser(dmx = false, onDismiss = { addingProp = false }) { xml, m ->
        addingProp = false
        note = null
        run("Adding ${m.name}…") {
            if (XlightsModels.isDmx(xml)) throw IllegalStateException("DMX fixtures can't be simulated yet.")
            val prop = withContext(Dispatchers.Default) { XlightsModels.parseXmodel(xml.byteInputStream()) }
            val run = sim.addProp(
                prop.copy(name = m.name),
                widthCm = VendorCatalog.lengthCm(m.width) ?: prop.widthCm,
                heightCm = VendorCatalog.lengthCm(m.height) ?: prop.heightCm,
            )
            note = "${run.name} is on the house with its own simulated controller (${run.count} LEDs)" +
                (if (run.realWidthCm != null) ", at its real size" else "") + "."
            // Picked, ready to be dragged into place.
            arranging = true; selectedRun = run.id
        }
    }
    removing?.let { d ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${d.name}?") },
            text = { Text("The simulated controller and its lights on the map are removed from the virtual house.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    run("Removing…") {
                        sim.removeController(d.id)
                        container.store.updateHouseMap { m -> m.copy(runs = m.runs.filterNot { it.deviceId == d.id }) }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Intro(enabled: Boolean, onStart: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Science, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Try everything on a virtual house", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "The simulator lights up a virtual house with four simulated WLED controllers: a roofline strip, bullet lights round the windows and door, a mega tree, and arches and bushes in the yard. They behave like the real thing, so you can try every part of the app and watch the result live:",
                style = MaterialTheme.typography.bodyMedium,
            )
            listOf(
                "Colours, effects, palettes, segments, presets and playlists",
                "Holiday themes, light types, LED setup, nightlight and the power limiter",
                "Groups and scenes across all four controllers",
                "Whole-house effects, the house map, props and standalone mode",
                "Shows in the sequencer, the show schedule and xLights imports",
            ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            Text(
                "Your real controllers and setup are saved first and come back exactly as they were when you leave. Nothing is sent to your real lights while the simulator is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start the simulator")
            }
        }
    }
}

@Composable
private fun ActiveSimulator(
    onOpenDevice: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenHouse: () -> Unit,
    onOpenShows: () -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenMap: () -> Unit,
    onAdd: () -> Unit,
    onAddProp: () -> Unit,
    arranging: Boolean,
    selectedRunId: String?,
    onSelectRun: (String) -> Unit,
    onArrange: (Boolean) -> Unit,
    onRemove: (SavedDevice) -> Unit,
    onLeave: () -> Unit,
    enabled: Boolean,
) {
    val container = LocalAppContainer.current
    val saved by container.store.devices.collectAsStateWithLifecycle(emptyList())
    val groups by container.store.groups.collectAsStateWithLifecycle(emptyList())
    val shows by container.sequences.all.collectAsStateWithLifecycle()
    SimHub.version.collectAsStateWithLifecycle().value // Refresh summaries when a controller changes.

    SimHouseView(
        Modifier.heightIn(max = 420.dp), onTapDevice = onOpenDevice,
        arranging = arranging, selectedRunId = selectedRunId, onSelectRun = onSelectRun,
    )
    if (arranging) {
        val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
        val picked = map.runs.firstOrNull { it.id == selectedRunId }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(picked?.name ?: "Tap a prop to pick it", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (picked == null) "Then drag it to move it, or pinch to make it bigger or smaller."
                        else "Drag to move it, pinch to resize, or tap where it should go. Tap another prop to pick that one." +
                            (picked.realWidthCm?.let { w -> picked.realHeightCm?.let { h -> " Now ${formatLength(w, map.useInches)} × ${formatLength(h, map.useInches)}." } } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(onClick = { onArrange(false) }) { Text("Done") }
            }
            if (picked?.model != null) {
                val scope = rememberCoroutineScope()
                Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        scope.launch { container.store.updateHouseMap { m -> m.copy(runs = m.runs.map { if (it.id == picked.id) it.copy(mirrored = !it.mirrored) else it }) } }
                    }) { Icon(Icons.Filled.Flip, null); Spacer(Modifier.width(4.dp)); Text(if (picked.mirrored) "Unmirror" else "Mirror") }
                    TextButton(onClick = {
                        scope.launch { onSelectRun(container.simulator.addMirroredCopy(picked).id) }
                    }) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Mirrored copy") }
                }
            }
        }
    } else Text(
        "Tap a light to open its controller. Whatever you do in the app shows up here live — and in the floating view on other screens.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shows.firstOrNull()?.let { s ->
            FilledTonalButton(onClick = { onOpenShow(s.id) }) { Icon(Icons.Filled.Movie, null); Spacer(Modifier.width(6.dp)); Text("Play ${s.name}") }
        }
        OutlinedButton(onClick = onOpenHouse) { Icon(Icons.Filled.Home, null); Spacer(Modifier.width(6.dp)); Text("Whole-house effects") }
        OutlinedButton(onClick = onOpenShows) { Icon(Icons.Filled.Movie, null); Spacer(Modifier.width(6.dp)); Text("Shows") }
        OutlinedButton(onClick = onOpenSchedule) { Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(6.dp)); Text("Schedule") }
        OutlinedButton(onClick = { onArrange(true) }, enabled = !arranging) { Icon(Icons.Filled.OpenWith, null); Spacer(Modifier.width(6.dp)); Text("Arrange props") }
        OutlinedButton(onClick = onOpenMap) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit map") }
        groups.forEach { g ->
            OutlinedButton(onClick = { onOpenGroup(g.id) }) { Icon(Icons.Filled.Workspaces, null); Spacer(Modifier.width(6.dp)); Text(g.name) }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text("Simulated controllers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd, enabled = enabled) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add") }
    }
    FilledTonalButton(onClick = onAddProp, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Storefront, null); Spacer(Modifier.width(8.dp)); Text("Add a prop from a vendor")
    }
    Text(
        "Arches, stars, snowflakes, trees and more from Boscoyo, Gilbert Engineering, Mattos and others: each gets its own simulated controller and goes on the house at its real size.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val simulated = saved.mapNotNull { d -> SimHub.find(d.host)?.let { d to it } }
    simulated.forEach { (d, device) -> key(d.id) {
        val controller = container.devices.get(d.id)
        val snap = controller?.snapshot?.collectAsStateWithLifecycle()?.value
        Card(
            onClick = { onOpenDevice(d.id) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${device.localLeds} LEDs · ${device.summary()} · ${device.powerMa} mA",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onRemove(d) }, enabled = enabled) { Icon(Icons.Outlined.Delete, "Remove ${d.name}") }
                    Switch(checked = snap?.state?.on == true, onCheckedChange = { controller?.setPower(it) })
                }
                Box(Modifier.padding(end = 8.dp)) { SimStrip(device) }
            }
        }
    } }

    OutlinedButton(onClick = onLeave, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("Leave simulator")
    }
    Text(
        "Leaving puts your real controllers and setup back. The virtual house is kept, so you can carry on next time.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AddSimControllerDialog(onDismiss: () -> Unit, onAdd: (String, Int, LightProfile, Boolean) -> Unit) {
    var name by remember { mutableStateOf("New lights") }
    var leds by remember { mutableFloatStateOf(100f) }
    var profile by remember { mutableStateOf(LightProfile.STRIP) }
    var rgbw by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a simulated controller") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text("${leds.roundToInt()} LEDs", style = MaterialTheme.typography.labelLarge)
                Slider(leds, { leds = it }, valueRange = 10f..600f)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LightProfile.entries.forEach { p -> FilterChip(selected = profile == p, onClick = { profile = p }, label = { Text(p.label) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RGBW (white channel)", Modifier.weight(1f))
                    Switch(checked = rgbw, onCheckedChange = { rgbw = it })
                }
                Text(
                    "Then trace it on the house in Edit map to see it on the virtual house.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim().ifBlank { "New lights" }, leds.roundToInt(), profile, rgbw) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
