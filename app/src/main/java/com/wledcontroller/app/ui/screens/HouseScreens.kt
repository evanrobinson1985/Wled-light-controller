@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import com.wledcontroller.app.ui.components.drawSpotlight
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Slider
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.wledcontroller.app.house.HouseEffectParams
import com.wledcontroller.app.house.HouseEffectService
import com.wledcontroller.app.house.HiddenStretch
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropModel
import com.wledcontroller.app.house.StartCorner
import com.wledcontroller.app.house.Submodel
import com.wledcontroller.app.house.formatLedRanges
import com.wledcontroller.app.house.parseLedRanges
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.hiddenFlags
import com.wledcontroller.app.house.HouseRenderer
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PropGroup
import com.wledcontroller.app.house.PropType
import com.wledcontroller.app.house.StandalonePlan
import com.wledcontroller.app.house.ledPositions
import com.wledcontroller.app.house.mirroredCopy
import com.wledcontroller.app.house.fillGaps
import com.wledcontroller.app.house.formatLength
import com.wledcontroller.app.house.tracedLength
import com.wledcontroller.app.house.placedLeds
import com.wledcontroller.app.house.LineTools
import com.wledcontroller.app.house.shaped
import com.wledcontroller.app.house.hooks
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.SectionTitle
import com.wledcontroller.app.ui.components.themeBrush
import com.wledcontroller.app.ui.components.ColorSwatch
import com.wledcontroller.app.ui.components.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.UUID

// ================================================================== shared canvas

/** The house photo, no bigger than about [maxSide] across (full size for zooming in to trace). */
@Composable
internal fun rememberPhoto(path: String?, maxSide: Int = 2048): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, path, maxSide) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(it, o)
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / s > maxSide * 1.4f) s *= 2
                BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }
        }
    }
    return photo
}

/**
 * The house photo with an overlay, that zooms: pinch (or the buttons) to zoom, drag to move
 * round when zoomed in, tap to place a point exactly. [handles] can be dragged into place.
 * [overlay] gets how far it's zoomed in, to keep lines and dots thin. With [fitScreen] it
 * fills the space it's given (keeping the photo's shape) instead of the width.
 */
@Composable
internal fun ZoomableHouseCanvas(
    map: HouseMap,
    photo: ImageBitmap?,
    modifier: Modifier = Modifier,
    fitScreen: Boolean = false,
    handles: List<MapPoint> = emptyList(),
    onMoveHandle: ((Int, MapPoint) -> Unit)? = null,
    onTap: ((MapPoint) -> Unit)? = null,
    onFullScreen: (() -> Unit)? = null,
    overlay: DrawScope.(zoom: Float) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val tap by rememberUpdatedState(onTap)
    val move by rememberUpdatedState(onMoveHandle)
    val currentHandles by rememberUpdatedState(handles)
    fun clamp(p: Offset, s: Float) = Offset(
        p.x.coerceIn(boxSize.width - boxSize.width * s, 0f),
        p.y.coerceIn(boxSize.height - boxSize.height * s, 0f),
    )
    fun zoomBy(f: Float) {
        val c = Offset(boxSize.width / 2, boxSize.height / 2)
        val ns = (scale * f).coerceIn(1f, 10f)
        pan = clamp(c - (c - pan) * (ns / scale), ns); scale = ns
    }
    Box(
        modifier
            .then(if (fitScreen) Modifier else Modifier.fillMaxWidth())
            .aspectRatio(map.aspect.coerceIn(0.4f, 3f))
            .clip(RoundedCornerShape(if (fitScreen) 0.dp else 16.dp))
            .background(Color(0xFF101218))
            .onSizeChanged { boxSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = pan.x; translationY = pan.y
                transformOrigin = TransformOrigin(0f, 0f)
            }
        ) {
            if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = 0.75f, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
            Canvas(Modifier.fillMaxSize()) { overlay(scale) }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                fun toMap(screen: Offset): MapPoint {
                    val c = (screen - pan) / scale
                    return MapPoint((c.x / size.width).coerceIn(0f, 1f), (c.y / size.height).coerceIn(0f, 1f))
                }
                fun hit(screen: Offset): Int? {
                    if (move == null) return null
                    val radius = 22.dp.toPx()
                    var best: Int? = null; var bestD = Float.MAX_VALUE
                    currentHandles.forEachIndexed { i, p ->
                        val d = (Offset(p.x * size.width, p.y * size.height) * scale + pan - screen).getDistance()
                        if (d < radius && d < bestD) { bestD = d; best = i }
                    }
                    return best
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val target = hit(down.position)
                    var dragging = false; var multi = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            multi = true
                            val zoom = event.calculateZoom(); val centroid = event.calculateCentroid()
                            val ns = (scale * zoom).coerceIn(1f, 10f)
                            pan = clamp(centroid - (centroid - pan) * (ns / scale) + event.calculatePan(), ns)
                            scale = ns
                            event.changes.forEach { it.consume() }
                        } else if (!multi) {
                            val ch = pressed.first()
                            if (!dragging && (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                            if (dragging) {
                                if (target != null) { move?.invoke(target, toMap(ch.position)); ch.consume() }
                                else if (scale > 1f) { pan = clamp(pan + (ch.position - ch.previousPosition), scale); ch.consume() }
                            }
                        }
                    }
                    if (!dragging && !multi && target == null) tap?.invoke(toMap(down.position))
                }
            }
        )
        // Zoom buttons, for one hand.
        Column(Modifier.align(Alignment.TopEnd).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            @Composable
            fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
                Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White) {
                    Icon(icon, label, Modifier.padding(6.dp).size(20.dp))
                }
            }
            Btn(Icons.Filled.ZoomIn, "Zoom in") { zoomBy(2f) }
            if (scale > 1f) Btn(Icons.Filled.ZoomOut, "Zoom out") { zoomBy(0.5f) }
            if (scale > 1f) Btn(Icons.Filled.FitScreen, "Whole photo") { scale = 1f; pan = Offset.Zero }
            onFullScreen?.let { Btn(if (fitScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, if (fitScreen) "Exit full screen" else "Full screen", it) }
        }
        if (scale > 1f) Text(
            "${(scale * 10).roundToInt() / 10f}×",
            style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
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

            val fixtureList by container.store.fixtures.collectAsStateWithLifecycle(emptyList())
            val fixtureIds = remember(fixtureList) { fixtureList.map { it.id }.toSet() }
            var view3d by rememberSaveable { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilterChip(selected = view3d, onClick = { view3d = !view3d }, label = { Text("3D") })
            }
            // This frame's colours, with any live text on top, as the lights will show them.
            val overlay = remember(map, leds) { com.wledcontroller.app.house.LiveTextOverlay(map, leds) }
            val frame = IntArray(leds.size) { i -> leds[i].let { l -> HouseRenderer.color(params, l.x, l.y, t, i, l.orderInRun) } }
            params.liveText?.let { lt -> overlay.apply(frame, lt, container.house.liveWords(lt), t) }
            if (view3d) com.wledcontroller.app.ui.components.HousePreview3D(map, leds, { i -> frame[i] })
            else HouseCanvas(map, photo) {
                leds.forEachIndexed { i, led ->
                    if (led.hidden) return@forEachIndexed // Out of sight in the photo.
                    val o = Offset(led.x * size.width, led.y * size.height)
                    val c = frame[i]
                    // DMX spotlights shine straight up in the effect's colour.
                    if (led.deviceId in fixtureIds) drawSpotlight(o, c, 0.5f, 0.5f, 0.8f)
                    else drawPixel(c, o, pixelRadius(pixel, gaps[i]))
                }
            }
            Text(
                if (running) "Live on your lights — keeps running with the screen off." else "Preview. Press Play to send it to your lights.",
                style = MaterialTheme.typography.labelMedium,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionTitle("Effect")
            // Older settings had the shadow as the effect itself; show them the new way.
            val shown = params.normalized()
            // Only the effects that look best when most of the house is one kind of light.
            var lightType by rememberSaveable { mutableStateOf<com.wledcontroller.app.data.LightType?>(null) }
            com.wledcontroller.app.ui.components.LightTypeFilter(lightType, { lightType = it })
            fun fits(e: HouseEffect) = lightType == null || e == shown.effect || e == shown.shadow ||
                lightType in com.wledcontroller.app.data.EffectFit.house(e)
            @Composable
            fun effectChips(list: List<HouseEffect>) = FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { e ->
                    FilterChip(
                        selected = shown.effect == e || shown.shadow == e,
                        onClick = { container.house.params.value = params.pick(e) },
                        label = { Text(e.label) },
                    )
                }
            }
            effectChips(HouseEffect.entries.filter { it.holiday == null && fits(it) })
            // Shadow figures moving about over the effect, by holiday.
            SectionTitle("Holiday shadows")
            Text(
                if (shown.shadow != null) "${shown.shadow.label} over ${shown.effect.label}. Pick any effect above to change what's underneath, or Off to stop the shadows."
                else "Off. Pick one to play its shadows over the effect above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChip(
                selected = shown.shadow == null,
                onClick = { container.house.params.value = shown.copy(shadow = null) },
                label = { Text("Off") },
                leadingIcon = { Icon(Icons.Filled.Block, null, Modifier.size(18.dp)) },
            )
            HouseEffect.entries.filter { it.holiday != null && fits(it) }.groupBy { it.holiday!! }.forEach { (holiday, list) ->
                Text(holiday, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                effectChips(list)
            }

            LiveTextSection(shown, map) { container.house.params.value = it }

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
            fun set(change: (HouseEffectParams) -> HouseEffectParams) { container.house.params.value = change(shown) }
            val label = if (shown.shadow != null) "${shown.effect.label} " else ""
            GradientSlider(shown.speed, { v -> set { it.copy(speed = v) } }, label = "${label}speed".replaceFirstChar(Char::uppercase), valueText = "${(shown.speed * 100).toInt()}%", height = 34.dp)
            Spacer(Modifier.height(10.dp))
            GradientSlider(shown.size, { v -> set { it.copy(size = v) } }, label = "${label}size".replaceFirstChar(Char::uppercase), valueText = "${(shown.size * 100).toInt()}%", height = 34.dp)
            shown.shadow?.let { fx ->
                // The shadow figures: how big, how many and how fast.
                val noun = fx.noun.replaceFirstChar(Char::uppercase)
                Spacer(Modifier.height(10.dp))
                GradientSlider(shown.shadowSize, { v -> set { it.copy(shadowSize = v) } }, label = "$noun size", valueText = "${(shown.shadowSize * 100).toInt()}%", height = 34.dp)
                Spacer(Modifier.height(10.dp))
                GradientSlider(
                    (shown.count - 1) / 7f,
                    { v -> set { it.copy(count = 1 + (v * 7).roundToInt()) } },
                    label = "Number of ${fx.plural}", valueText = "${shown.count}", height = 34.dp,
                )
                Spacer(Modifier.height(10.dp))
                GradientSlider(shown.shadowSpeed, { v -> set { it.copy(shadowSpeed = v) } }, label = "$noun speed", valueText = "${(shown.shadowSpeed * 100).toInt()}%", height = 34.dp)
                TextButton(onClick = { set { it.copy(shadow = null) } }) {
                    Icon(Icons.Filled.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Turn off the ${fx.plural}")
                }
            }
            Spacer(Modifier.height(10.dp))
            GradientSlider(shown.brightness, { v -> set { it.copy(brightness = v) } }, label = "Brightness", valueText = "${(params.brightness * 100).toInt()}%", height = 34.dp)

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
fun HouseMapEditor(onBack: () -> Unit, onScan: () -> Unit, onControllers: () -> Unit = {}, onPower: () -> Unit = {}, onModel3d: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val devices by container.devices.list.collectAsStateWithLifecycle()
    val targets = rememberLightTargets()
    // Full size, for zooming in to trace.
    val photo = rememberPhoto(map.photoPath, maxSide = 4200)
    var bigMap by remember { mutableStateOf(false) }

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

    // Fine-tuning a traced line: its points, hidden stretches, sag and hooks, with undo.
    var adjustingRunId by remember { mutableStateOf<String?>(null) }
    val adjustPoints = remember { mutableStateListOf<MapPoint>() }
    val adjustHidden = remember { mutableStateListOf<HiddenStretch>() }
    var adjustSel by remember { mutableStateOf<Int?>(null) }
    var adjustSag by remember { mutableFloatStateOf(0f) }
    var adjustHooks by remember { mutableIntStateOf(0) }
    val adjustUndo = remember { mutableStateListOf<Pair<List<MapPoint>, List<HiddenStretch>>>() }
    var lastDrag by remember { mutableStateOf(-1 to 0L) }
    fun keepUndo() { adjustUndo += adjustPoints.toList() to adjustHidden.toList(); if (adjustUndo.size > 50) adjustUndo.removeAt(0) }
    var placingLed by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { container.house.importPhoto(uri) }
                .onSuccess { (path, aspect) -> container.store.updateHouseMap { it.copy(photoPath = path, aspect = aspect) } }
                .onFailure { importError = it.message }
        }
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Importing props from xLights: one model (.xmodel) or a whole layout (xlights_rgbeffects.xml).
    val context = LocalContext.current
    var importMenu by remember { mutableStateOf(false) }
    var layoutPending by remember { mutableStateOf<com.wledcontroller.app.house.XlLayout?>(null) }
    var importNote by remember { mutableStateOf<String?>(null) }
    var browsingVendors by remember { mutableStateOf(false) }
    // Saved layouts and light models.
    var libraryMenu by remember { mutableStateOf(false) }
    var showLayouts by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    // A DMX fixture model picked here: set it up, then put it on the map.
    var newFixture by remember { mutableStateOf<com.wledcontroller.app.data.DmxFixture?>(null) }
    val outputs by container.store.outputs.collectAsStateWithLifecycle(emptyList())
    val fixtures by container.store.fixtures.collectAsStateWithLifecycle(emptyList())
    val simActive by container.simulator.active.collectAsStateWithLifecycle()
    /** A prop or DMX fixture model, from a file or a vendor: open it to choose where it goes. */
    fun importModelXml(xml: String, nameOverride: String?, vendor: com.wledcontroller.app.house.VendorModel? = null) {
        importError = null
        if (com.wledcontroller.app.house.XlightsModels.isDmx(xml)) {
            val dmxOut = outputs.filter { it.protocol != com.wledcontroller.app.data.OutputProtocol.DDP }
            if (dmxOut.isEmpty()) { importError = "That's a DMX fixture. Add your E1.31 / Art-Net controller in Settings first, then import it again."; return }
            runCatching { com.wledcontroller.app.house.XlightsModels.parseDmxFixture(xml.byteInputStream()) }
                .onSuccess { f -> newFixture = f.copy(name = (nameOverride ?: f.name).take(40), outputId = dmxOut.first().id, address = nextAddress(fixtures)) }
                .onFailure { importError = it.message ?: "Couldn't read that fixture." }
            return
        }
        runCatching { com.wledcontroller.app.house.XlightsModels.parseXmodel(xml.byteInputStream()) }.onSuccess { m ->
            if (simActive) {
                // In the simulator the prop gets a simulated controller of its own and goes on
                // the house straight away; one tap moves it.
                scope.launch {
                    runCatching {
                        container.simulator.addProp(
                            m.copy(name = (nameOverride ?: m.name)),
                            widthCm = vendor?.width?.let(com.wledcontroller.app.house.VendorCatalog::lengthCm) ?: m.widthCm,
                            heightCm = vendor?.height?.let(com.wledcontroller.app.house.VendorCatalog::lengthCm) ?: m.heightCm,
                        )
                    }.onSuccess { startTracing(it.id) }.onFailure { importError = it.message ?: "Couldn't add that prop." }
                }
                return@onSuccess
            }
            val target = targets.firstOrNull { it.kind?.startsWith("DMX") != true }
            val usedEnd = map.runs.filter { it.deviceId == target?.id }.maxOfOrNull { it.endLed } ?: 0
            // Open it in the run editor to choose the controller, then place it.
            editRun = LightRun(
                UUID.randomUUID().toString(), (nameOverride ?: m.name).take(40), target?.id ?: "", usedEnd, m.ledCount,
                prop = m.prop, model = m.model, submodels = m.submodels,
                // The maker's size, so it goes on the house at its true scale.
                realWidthCm = vendor?.width?.let(com.wledcontroller.app.house.VendorCatalog::lengthCm) ?: m.widthCm,
                realHeightCm = vendor?.height?.let(com.wledcontroller.app.house.VendorCatalog::lengthCm) ?: m.heightCm,
            )
        }.onFailure { importError = it.message ?: "Couldn't read that model." }
    }
    val xmodelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importError = null
            runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
            }.onSuccess { importModelXml(it, null) }.onFailure { importError = it.message ?: "Couldn't read that model." }
        }
    }
    val layoutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importError = null
            runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { com.wledcontroller.app.house.XlightsModels.parseLayout(it) } }
            }.onSuccess { l -> if (l.models.isEmpty()) importError = "No props found in that layout." else layoutPending = l }
                .onFailure { importError = it.message ?: "Couldn't read that layout." }
        }
    }

    fun saveRun(run: LightRun) = scope.launch {
        container.store.updateHouseMap { m ->
            m.copy(runs = if (m.runs.any { it.id == run.id }) m.runs.map { if (it.id == run.id) run else it } else m.runs + run)
        }
    }

    /** A saved model onto the house: like an imported prop, it opens to choose where it goes. */
    fun useModel(m: com.wledcontroller.app.data.SavedModel) {
        importError = null
        if (simActive) {
            scope.launch {
                runCatching { container.simulator.addProp(m.toXlModel()) }
                    .onSuccess { r ->
                        if (m.mirrored || m.upsideDown) saveRun(r.copy(mirrored = m.mirrored, upsideDown = m.upsideDown)).join()
                        startTracing(r.id)
                    }
                    .onFailure { importError = it.message ?: "Couldn't add that model." }
            }
            return
        }
        val target = targets.firstOrNull { it.kind?.startsWith("DMX") != true }
        val usedEnd = map.runs.filter { it.deviceId == target?.id }.maxOfOrNull { it.endLed } ?: 0
        editRun = m.toRun(UUID.randomUUID().toString(), target?.id ?: "", usedEnd)
    }

    val drawingRun = map.runs.firstOrNull { it.id == drawingRunId }
    val adjustingRun = map.runs.firstOrNull { it.id == adjustingRunId }

    fun startAdjusting(run: LightRun) {
        adjustPoints.clear(); adjustPoints.addAll(run.points)
        adjustHidden.clear(); adjustHidden.addAll(run.hidden)
        adjustSag = run.sag; adjustHooks = run.hooksBetween
        adjustSel = null; adjustUndo.clear()
        adjustingRunId = run.id
    }
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
        val host = targets.firstOrNull { it.id == run.deviceId }?.address ?: return
        container.house.identifyLed(host, run.startLed + i)
    }

    fun undoTrace() {
        if (draft.isNotEmpty()) draft.removeAt(draft.lastIndex)
        draftHidden.removeAll { it.after >= draft.size - 1 }
    }
    fun finishTrace() {
        val run = drawingRun ?: return
        saveRun(run.copy(points = draft.toList(), ledPoints = null, hidden = if (run.model != null) emptyList() else draftHidden.toList(), hiddenLeds = emptySet()))
        drawingRunId = null; draft.clear(); draftHidden.clear()
    }
    val canFinishTrace = drawingRun != null &&
        (if (drawingRun.model != null) draft.size == 2 else draft.size >= 2 || (drawingRun.count == 1 && draft.size == 1))

    // The map being traced on: on the page, or full screen.
    val mapCanvas: @Composable (Modifier, Boolean) -> Unit = { mod, fit ->
        val pixel = LocalPixelStyle.current
        ZoomableHouseCanvas(
            map, photo, mod, fitScreen = fit,
            // Points traced so far can be dragged into place.
            handles = if (drawingRun != null) draft.toList() else emptyList(),
            onMoveHandle = if (drawingRun != null) { { i, p -> if (i < draft.size) draft[i] = p } } else null,
            onFullScreen = { bigMap = !bigMap },
            onTap = if (drawingRun != null) { { p ->
            // A model needs just the two corners of its box; one with a real size, just its centre.
            val w = drawingRun.realWidthCm; val h = drawingRun.realHeightCm
            val sized = if (drawingRun.model != null && w != null && h != null) map.boxAround(p, w, h) else null
            if (sized != null) { draft.clear(); draft.addAll(sized) }
            else if (drawingRun.model == null || draft.size < 2) draft.add(p)
            if (drawingRun.model == null && nextHidden && draft.size >= 2) {
                val k = draft.size - 2
                draftHidden.add(HiddenStretch(k)); askHidden = k; nextHidden = false
            }
        } }
        else if (scaling) { { p -> if (scaleDraft.size < 2) scaleDraft.add(p); if (scaleDraft.size == 2) askScale = true } }
            else null,
        ) { z ->
        val stroke = 2.dp.toPx() / z
        // The scale: a dashed yellow line between the two measured points.
        val scalePts = if (scaling) scaleDraft.toList() else listOfNotNull(map.scaleA, map.scaleB)
        if (scalePts.size == 2) drawRun(scalePts, Color(0xFFFFE06B), stroke * 0.8f, dashed = true)
        scalePts.forEach { p -> drawCircle(Color(0xFFFFE06B), 4.dp.toPx() / z, Offset(p.x * size.width, p.y * size.height)) }
        map.runs.forEachIndexed { r, run ->
            if (run.id == drawingRunId) return@forEachIndexed
            val col = RunColors[r % RunColors.size]
            val positions = map.positions(run)
            val placed = positions.filterNotNull()
            if (run.isPlacedPerLed) drawRun(placed, col, stroke, dashed = false)
            else if (run.model != null) drawBox(run.points, col.copy(alpha = 0.5f), stroke)
            else run.shaped(map.aspect).let { shown -> drawTracedRun(shown.points, shown.hidden.map { it.after }.toSet(), col, stroke) }
            val runGaps = neighbourGaps(positions, map.aspect)
            val hiddenLed = run.hiddenFlags(map.aspect, map.cmPerHeight)
            positions.forEachIndexed { i, p ->
                if (p == null) return@forEachIndexed
                val o = Offset(p.x * size.width, p.y * size.height)
                val r = pixelRadius(pixel, runGaps[i], scale = 1f / z)
                // Out-of-sight lights: hollow rings.
                if (hiddenLed.getOrElse(i) { false }) drawCircle(col.copy(alpha = 0.7f), r, o, style = Stroke(1.dp.toPx() / z))
                else drawCircle(col, r, o)
            }
            placed.firstOrNull()?.let { s ->
                drawCircle(Color.White, 6.dp.toPx() / z, Offset(s.x * size.width, s.y * size.height), style = Stroke(stroke))
            }
        }
        if (drawingRun != null) {
            if (drawingRun.model != null) drawBox(draft.toList(), Color.White, stroke)
            else drawTracedRun(draft.toList(), draftHidden.map { it.after }.toSet(), Color.White, stroke)
            val previewRun = drawingRun.copy(points = draft.toList(), ledPoints = null, hidden = draftHidden.toList())
            val preview = map.positions(previewRun)
            val previewHidden = previewRun.hiddenFlags(map.aspect, map.cmPerHeight)
            val previewGaps = neighbourGaps(preview, map.aspect)
            preview.forEachIndexed { i, p ->
                if (p == null) return@forEachIndexed
                val o = Offset(p.x * size.width, p.y * size.height)
                val r = pixelRadius(pixel, previewGaps[i], scale = 1f / z)
                if (previewHidden.getOrElse(i) { false }) drawCircle(Color.White.copy(alpha = 0.6f), r, o, style = Stroke(1.dp.toPx() / z))
                else drawCircle(Color.White, r, o)
            }
            draft.firstOrNull()?.let { s -> drawCircle(Color.Red, 6.dp.toPx() / z, Offset(s.x * size.width, s.y * size.height)) }
        }
        }
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
                            adjustingRun != null -> "Adjust ${adjustingRun.name}"
                            else -> "House map"
                        }
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (drawingRun == null && editingRun == null && adjustingRun == null && !scaling) {
                        Box {
                            IconButton(onClick = { importMenu = true }, enabled = targets.isNotEmpty()) { Icon(Icons.Filled.FileOpen, "Import from xLights") }
                            DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                                DropdownMenuItem(
                                    text = { Column { Text("xLights layout"); Text("xlights_rgbeffects.xml — all your props", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { importMenu = false; layoutPicker.launch(arrayOf("*/*")) },
                                )
                                DropdownMenuItem(
                                    text = { Column { Text("Prop from a vendor"); Text("Boscoyo, Gilbert Engineering, Mattos and more", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { importMenu = false; browsingVendors = true },
                                )
                                DropdownMenuItem(
                                    text = { Column { Text("Prop or fixture model file"); Text(".xmodel from xLights or a prop maker", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { importMenu = false; xmodelPicker.launch(arrayOf("*/*")) },
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { libraryMenu = true }) { Icon(Icons.Filled.Bookmarks, "Saved layouts and models") }
                            DropdownMenu(expanded = libraryMenu, onDismissRequest = { libraryMenu = false }) {
                                DropdownMenuItem(
                                    text = { Column { Text("House layouts"); Text("Save this map, or open a saved one", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { libraryMenu = false; showLayouts = true },
                                )
                                DropdownMenuItem(
                                    text = { Column { Text("My light models"); Text("Props you've kept, to add again", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { libraryMenu = false; showModels = true },
                                )
                                DropdownMenuItem(
                                    text = { Column { Text("Controller map"); Text("Which props are on which controller and output", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { libraryMenu = false; onControllers() },
                                )
                                DropdownMenuItem(
                                    text = { Column { Text("Power planner"); Text("Amps, power supplies and injection points", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { libraryMenu = false; onPower() },
                                )
                                DropdownMenuItem(
                                    text = { Column { Text("3D house model"); Text("Build or import your house in 3D and put your lights on it", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = { libraryMenu = false; onModel3d() },
                                )
                            }
                        }
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
            importNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

            if (adjustingRun != null) {
                val preview = adjustingRun.copy(points = adjustPoints.toList(), hidden = adjustHidden.toList(), sag = adjustSag, hooksBetween = adjustHooks)
                val pixelStyle = LocalPixelStyle.current
                LedEditCanvas(
                    map = map, photo = photo, editingRunId = adjustingRun.id, points = adjustPoints.toList(), selected = adjustSel,
                    onTapEmpty = { adjustSel = null },
                    onTapLed = { i -> adjustSel = i },
                    onMoveLed = { i, p ->
                        // One undo step per drag.
                        val now = System.currentTimeMillis()
                        if (lastDrag.first != i || now - lastDrag.second > 600) keepUndo()
                        lastDrag = i to now
                        adjustPoints[i] = p; adjustSel = i
                    },
                    connect = false,
                    extra = { zoom ->
                        // The line as it really hangs, the hooks, and where its lights land.
                        val shown = preview.shaped(map.aspect)
                        drawTracedRun(shown.points, shown.hidden.map { it.after }.toSet(), Color(0xFFFFB86B), 1.5.dp.toPx() / zoom)
                        if (adjustHooks > 0) preview.hooks().forEach { h ->
                            drawCircle(Color(0xFF6BC8FF), 3.dp.toPx() / zoom, Offset(h.x * size.width, h.y * size.height), style = Stroke(1.dp.toPx() / zoom))
                        }
                        val r = pixelRadius(pixelStyle, scale = 1f / zoom) * 0.7f
                        map.positions(preview).forEach { p -> if (p != null) drawCircle(Color(0xFFFFF3B0), r, Offset(p.x * size.width, p.y * size.height)) }
                    },
                )
                // Average span between hooks, for saying how far the middle drops.
                val hooks = preview.hooks()
                val spanCm = map.cmPerHeight?.let { cm ->
                    hooks.zipWithNext { a, b -> kotlin.math.hypot((b.x - a.x) * map.aspect, b.y - a.y) }.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.times(cm)
                }
                AdjustLineControls(
                    points = adjustPoints.size, selected = adjustSel, sag = adjustSag, hooks = adjustHooks,
                    inches = map.useInches, spanCm = spanCm, canUndo = adjustUndo.isNotEmpty(),
                    onNudge = { dx, dy ->
                        adjustSel?.let { i ->
                            val now = System.currentTimeMillis()
                            if (lastDrag.first != i || now - lastDrag.second > 600) keepUndo()
                            lastDrag = i to now
                            val p = adjustPoints[i]
                            adjustPoints[i] = MapPoint((p.x + dx / map.aspect).coerceIn(0f, 1f), (p.y + dy).coerceIn(0f, 1f))
                        }
                    },
                    onAddAfter = {
                        adjustSel?.takeIf { it < adjustPoints.lastIndex }?.let { i ->
                            keepUndo()
                            val a = adjustPoints[i]; val b = adjustPoints[i + 1]
                            adjustPoints.add(i + 1, MapPoint((a.x + b.x) / 2, (a.y + b.y) / 2))
                            val h = LineTools.hiddenAfterInserting(adjustHidden.toList(), i)
                            adjustHidden.clear(); adjustHidden.addAll(h)
                            adjustSel = i + 1
                        }
                    },
                    onDelete = {
                        adjustSel?.takeIf { adjustPoints.size > 2 }?.let { i ->
                            keepUndo()
                            adjustPoints.removeAt(i)
                            val h = LineTools.hiddenAfterRemoving(adjustHidden.toList(), i)
                            adjustHidden.clear(); adjustHidden.addAll(h)
                            adjustSel = null
                        }
                    },
                    onLevel = {
                        adjustSel?.takeIf { it < adjustPoints.lastIndex }?.let { i ->
                            keepUndo()
                            val l = LineTools.level(adjustPoints.toList(), i, map.aspect)
                            adjustPoints.clear(); adjustPoints.addAll(l)
                        }
                    },
                    onStraighten = {
                        keepUndo()
                        val l = LineTools.straighten(adjustPoints.toList(), map.aspect)
                        adjustPoints.clear(); adjustPoints.addAll(l)
                    },
                    onSag = { adjustSag = it },
                    onHooks = { adjustHooks = it.coerceIn(0, 50) },
                    onUndo = {
                        adjustUndo.removeLastOrNull()?.let { (p, h) ->
                            adjustPoints.clear(); adjustPoints.addAll(p); adjustHidden.clear(); adjustHidden.addAll(h)
                            adjustSel = adjustSel?.takeIf { it < p.size }
                        }
                    },
                    onCancel = { adjustingRunId = null },
                    onSave = {
                        saveRun(adjustingRun.copy(points = adjustPoints.toList(), hidden = adjustHidden.toList(), sag = adjustSag, hooksBetween = adjustHooks))
                        adjustingRunId = null
                    },
                )
                return@Column
            }

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

            if (!bigMap) mapCanvas(Modifier, false)

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

            if (drawingRun != null && drawingRun.model != null) {
                val w = drawingRun.realWidthCm; val h = drawingRun.realHeightCm
                Text(
                    when {
                        w != null && h != null && map.cmPerHeight != null ->
                            "Tap where the middle of the prop goes. It's placed at its real size, ${formatLength(w, true)} × ${formatLength(h, true)}; tap again to move it."
                        w != null && h != null ->
                            "Set the house scale (ruler button) to place this prop at its real size, ${formatLength(w, true)} × ${formatLength(h, true)}. For now, tap two opposite corners."
                        else -> "Tap two opposite corners of the ${drawingRun.model.kind.label.lowercase()} on the photo. Its ${drawingRun.count} lights are laid out inside from how it's wired."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { draft.clear() }, enabled = draft.isNotEmpty()) { Text("Start again") }
                    TextButton(onClick = { drawingRunId = null; draft.clear() }) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(enabled = draft.size == 2, onClick = {
                        saveRun(drawingRun.copy(points = draft.toList(), ledPoints = null, hidden = emptyList(), hiddenLeds = emptySet()))
                        drawingRunId = null; draft.clear()
                    }) { Text("Done") }
                }
                return@Column
            }

            if (drawingRun != null) {
                Text(
                    if (drawingRun.count == 1) "Tap where this light is on the photo, then Done."
                    else "Tap along the lights, starting at the end where LED ${drawingRun.startLed} is (usually the end nearest the controller). Add a point at every corner.",
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
                    Button(enabled = draft.size >= 2 || (drawingRun.count == 1 && draft.size == 1), onClick = {
                        saveRun(drawingRun.copy(points = draft.toList(), ledPoints = null, hidden = draftHidden.toList(), hiddenLeds = emptySet()))
                        drawingRunId = null; draft.clear(); draftHidden.clear()
                    }) { Text("Done") }
                }
                return@Column
            }

            SectionTitle("Light runs") {
                TextButton(onClick = {
                    editRun = LightRun(UUID.randomUUID().toString(), "Run ${map.runs.size + 1}", targets.firstOrNull()?.id ?: "", 0, 0)
                }, enabled = targets.isNotEmpty()) { Icon(Icons.Filled.Add, null); Text("Add") }
            }
            if (map.runs.isEmpty()) {
                Text(
                    "A run is one continuous line of lights on one controller — for example the roofline, or the lights around a window. Add one for each.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            map.runs.forEachIndexed { r, run ->
                val device = targets.firstOrNull { it.id == run.deviceId }
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
                                    "${run.prop.label} · ${device?.name ?: "Missing device"} · LEDs ${run.startLed}–${run.endLed - 1}" +
                                        when {
                                            run.isPlacedPerLed -> " · ${run.placedCount} of ${run.count} placed"
                                            run.points.size < 2 -> " · not traced yet"
                                            else -> ""
                                        } +
                                        (run.spacingCm?.takeIf { !run.isPlacedPerLed }?.let { " · ${formatLength(it, map.useInches)} apart" } ?: "") +
                                        (if (run.sag > 0f && !run.isPlacedPerLed) " · sags ${Math.round(run.sag * 100)}%" else "") +
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
                                Text(
                                    when {
                                        run.model != null -> if (run.points.size < 2) "Place" else "Move"
                                        run.points.size < 2 && !run.isPlacedPerLed -> "Trace"
                                        else -> "Retrace"
                                    }
                                )
                            }
                            if (run.model == null) TextButton(enabled = run.points.size >= 2 && !run.isPlacedPerLed, onClick = { startAdjusting(run) }) { Text("Adjust") }
                            TextButton(enabled = run.points.size >= 2 && !run.isPlacedPerLed, onClick = { saveRun(run.copy(points = run.points.reversed(), hidden = run.hidden.map { it.copy(after = run.points.size - 2 - it.after) })) }) {
                                Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Flip start")
                            }
                            if (run.model != null) {
                                TextButton(onClick = { saveRun(run.copy(mirrored = !run.mirrored)) }) {
                                    Icon(Icons.Filled.Flip, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(if (run.mirrored) "Unmirror" else "Mirror")
                                }
                                TextButton(onClick = {
                                    // The other one of a pair: open it to choose its controller and LEDs, then place it.
                                    val copy = run.mirroredCopy(UUID.randomUUID().toString())
                                    val usedEnd = map.runs.filter { it.deviceId == run.deviceId }.maxOfOrNull { it.endLed } ?: 0
                                    editRun = copy.copy(startLed = usedEnd)
                                }) {
                                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Mirrored copy")
                                }
                            }
                            TextButton(enabled = run.placedCount > 0, onClick = {
                                scope.launch {
                                    val m = com.wledcontroller.app.data.SavedModel.fromRun(run, map)
                                    if (m == null) importNote = "Place ${run.name} on the photo first, so it has a shape to keep."
                                    else runCatching { container.library.saveModel(m) }
                                        .onSuccess { importNote = "Saved ${it.name} to My light models (the bookmark button at the top)." }
                                        .onFailure { importError = "Couldn't save the model: ${it.message}" }
                                }
                            }) {
                                Icon(Icons.Filled.Bookmarks, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Save as model")
                            }
                            TextButton(enabled = device != null, onClick = { device?.let { container.house.identify(run, it.address) } }) {
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

    // The map full screen, to trace hard-to-see lights (turn the phone sideways for the most room).
    if (bigMap) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { bigMap = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                mapCanvas(Modifier.align(Alignment.Center), true)
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                        .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    val run = drawingRun
                    Text(
                        when {
                            run != null && run.model == null -> "${run.name}: tap along the lights (${draft.size} point${if (draft.size == 1) "" else "s"}). Drag a point to fix it."
                            run != null -> "${run.name}: tap its corners. Drag one to fix it."
                            scaling -> "Tap the two ends of something you know the length of."
                            else -> "Pinch to zoom; drag to look round."
                        },
                        color = Color.White, style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (run != null) {
                            TextButton(enabled = draft.isNotEmpty(), onClick = { undoTrace() }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Undo")
                            }
                            if (run.model == null) FilterChip(
                                selected = nextHidden, enabled = draft.isNotEmpty(), onClick = { nextHidden = !nextHidden },
                                label = { Text(if (nextHidden) "Tap where they reappear" else "Out of sight next", color = Color.White) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { bigMap = false }) { Text("Exit full screen") }
                        if (run != null) Button(enabled = canFinishTrace, onClick = { finishTrace(); bigMap = false }) { Text("Done") }
                    }
                }
            }
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
    if (showLayouts) LayoutsDialog(map, onDismiss = { showLayouts = false }) { opened ->
        showLayouts = false
        scope.launch { container.store.updateHouseMap { opened } }
        val known = targets.map { it.id }.toSet()
        val missing = opened.runs.count { it.deviceId !in known }
        importNote = "Opened the layout." + if (missing > 0) " $missing runs are on controllers this phone doesn't have — edit them to choose one." else ""
    }
    if (showModels) ModelsDialog(onDismiss = { showModels = false }) { m ->
        showModels = false
        useModel(m)
    }
    if (browsingVendors) VendorBrowser(dmx = false, onDismiss = { browsingVendors = false }) { xml, m ->
        browsingVendors = false
        importModelXml(xml, m.name, m)
    }
    newFixture?.let { f ->
        FixtureDialog(f, outputs.filter { it.protocol != com.wledcontroller.app.data.OutputProtocol.DDP }, fixtures, onDismiss = { newFixture = null }) { saved ->
            newFixture = null
            scope.launch {
                container.store.upsertFixture(saved)
                // On the map as a single light; one tap places it.
                val run = LightRun(UUID.randomUUID().toString(), saved.name, saved.id, 0, 1, prop = PropType.BUSH)
                saveRun(run).join()
                startTracing(run.id)
            }
        }
    }
    layoutPending?.let { l ->
        LayoutImportDialog(l, map, targets, onDismiss = { layoutPending = null }) { updated, n ->
            scope.launch { container.store.updateHouseMap { updated } }
            layoutPending = null
            importNote = "Imported $n props. Use Trace or Place on each to put it on your photo."
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

/** A model's box, from its two corner points. */
private fun DrawScope.drawBox(points: List<MapPoint>, color: Color, stroke: Float) {
    points.forEach { p -> drawCircle(color, 4.dp.toPx(), Offset(p.x * size.width, p.y * size.height)) }
    if (points.size < 2) return
    val a = points[0]; val b = points[1]
    drawRect(
        color,
        topLeft = Offset(minOf(a.x, b.x) * size.width, minOf(a.y, b.y) * size.height),
        size = androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x) * size.width, kotlin.math.abs(b.y - a.y) * size.height),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
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
    // Layout: traced along a line, or a model laid out from its wiring.
    var modelKind by remember { mutableStateOf(run.model?.kind) }
    var model by remember { mutableStateOf(run.model ?: PropModel(ModelKind.MATRIX)) }
    val customSubs = remember { mutableStateListOf(*run.submodels.toTypedArray()) }
    var addingSub by remember { mutableStateOf(false) }
    val targets = rememberLightTargets()
    val device = targets.firstOrNull { it.id == deviceId }
    val ledCount = device?.ledCount ?: 0
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
    val isFixture = device?.kind?.startsWith("DMX") == true
    // Real size in inches, for placing models at true scale.
    fun inches(cm: Float?) = cm?.let { "%.1f".format(it / 2.54f).removeSuffix(".0") } ?: ""
    var realW by remember { mutableStateOf(inches(run.realWidthCm)) }
    var realH by remember { mutableStateOf(inches(run.realHeightCm)) }
    var mirrored by remember { mutableStateOf(run.mirrored) }
    // Distance in front of the house, in feet (or metres), for the 3D view.
    var depthText by remember { mutableStateOf(if (run.depthCm > 0f) "%.1f".format(if (map.useInches) run.depthCm / 30.48f else run.depthCm / 100f).removeSuffix(".0") else "") }
    var upsideDown by remember { mutableStateOf(run.upsideDown) }
    val activeModel = modelKind?.takeIf { !isFixture }?.let { model.copy(kind = it) }
    val valid = name.isNotBlank() && device != null && s != null && (activeModel != null || (c != null && c > 0)) && (!fixedSpacing || spacingCm != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (run.count == 0) "New light run" else "Edit light run") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Roofline)") }, singleLine = true)
                OutlinedTextField(
                    depthText, { depthText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5) },
                    label = { Text(if (map.useInches) "In front of the house (feet)" else "In front of the house (metres)") },
                    supportingText = { Text("For the 3D view: 0 or blank for lights on the house, more for yard props.") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                Text("Layout", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = modelKind == null, onClick = { modelKind = null }, label = { Text("Traced line") })
                    ModelKind.entries.filter { it != ModelKind.CUSTOM || run.model?.kind == ModelKind.CUSTOM }.forEach { k ->
                        FilterChip(selected = modelKind == k, onClick = {
                            modelKind = k
                            prop = when (k) { ModelKind.MATRIX -> PropType.MATRIX; ModelKind.MEGA_TREE -> PropType.TREE; ModelKind.STAR -> PropType.STAR; ModelKind.FACE, ModelKind.SINGING_FACE -> PropType.BUSH; ModelKind.CUSTOM -> PropType.MATRIX }
                        }, label = { Text(k.label) })
                    }
                }
                if (activeModel != null) {
                    ModelEditor(activeModel) { model = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(realW, { realW = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, label = { Text("Real width (in)") },
                            singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(realH, { realH = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, label = { Text("Real height (in)") },
                            singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Text(
                        "With a real size and the house scale set, it's placed at true size with one tap.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = mirrored, onClick = { mirrored = !mirrored }, label = { Text("Mirror (left ↔ right)") })
                        FilterChip(selected = upsideDown, onClick = { upsideDown = !upsideDown }, label = { Text("Upside down") })
                    }
                }
                Text("Prop type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PropType.entries.forEach { p ->
                        FilterChip(selected = prop == p, onClick = { prop = p }, label = { Text(p.label) })
                    }
                }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(device?.name ?: "Choose controller", Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        targets.forEach { d ->
                            DropdownMenuItem(text = { Text(d.name + (d.kind?.let { " · $it" } ?: "")) }, onClick = { deviceId = d.id; menu = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        start, { start = it.filter(Char::isDigit).take(5) }, label = { Text("First LED") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        activeModel?.ledCount?.toString() ?: count, { count = it.filter(Char::isDigit).take(5) }, label = { Text("LED count") }, singleLine = true,
                        enabled = activeModel == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                if (ledCount > 0) {
                    Text("This controller has $ledCount LEDs (0–${ledCount - 1}).", style = MaterialTheme.typography.labelSmall)
                }

                Text("Parts (submodels)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Text(
                    (activeModel?.submodels()?.takeIf { it.isNotEmpty() }?.let { auto -> "From the model: ${auto.size} (${auto.first().name}…). " } ?: "") +
                        "Add your own to light part of this run on its own in shows, e.g. the left half of the roofline.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                customSubs.forEach { sm ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${sm.name}: LEDs ${formatLedRanges(sm.ranges)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { customSubs.remove(sm) }) { Icon(Icons.Outlined.Delete, "Remove ${sm.name}") }
                    }
                }
                TextButton(onClick = { addingSub = true }) { Icon(Icons.Filled.Add, null); Text("Add part") }

                if (activeModel == null) {
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
            TextButton(enabled = valid, onClick = { onSave(
                    run.copy(
                        name = name.trim(), deviceId = deviceId, startLed = if (isFixture) 0 else s!!,
                        count = if (isFixture) 1 else activeModel?.ledCount ?: c!!, prop = prop,
                        spacingCm = if (fixedSpacing && activeModel == null) spacingCm else null,
                        hidden = if (activeModel == null) hiddenList.toList() else emptyList(),
                        model = activeModel,
                        // Switching to or from a model starts its placement afresh.
                        points = if ((activeModel == null) != (run.model == null)) emptyList() else run.points,
                        ledPoints = if (activeModel != null) null else run.ledPoints,
                        submodels = customSubs.toList(),
                        realWidthCm = realW.toFloatOrNull()?.takeIf { it > 0f && activeModel != null }?.times(2.54f),
                        realHeightCm = realH.toFloatOrNull()?.takeIf { it > 0f && activeModel != null }?.times(2.54f),
                        mirrored = activeModel != null && mirrored,
                        upsideDown = activeModel != null && upsideDown,
                        depthCm = depthText.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 500f)?.let { if (map.useInches) it * 30.48f else it * 100f } ?: 0f,
                    )
                ) }) {
                Text(if (run.count == 0) "Next: trace it" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (addingSub) {
        var subName by remember { mutableStateOf("") }
        var subRanges by remember { mutableStateOf("") }
        val total = activeModel?.ledCount ?: c ?: 0
        val parsed = parseLedRanges(subRanges, total)
        AlertDialog(
            onDismissRequest = { addingSub = false },
            title = { Text("New part") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(subName, { subName = it }, label = { Text("Name (e.g. Left half)") }, singleLine = true)
                    OutlinedTextField(
                        subRanges, { subRanges = it }, label = { Text("LEDs, e.g. 1-75 or 1-10, 20-30") }, singleLine = true,
                        supportingText = { Text("Counted along this run, 1 to $total.") },
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = subName.isNotBlank() && parsed != null, onClick = {
                    customSubs.add(Submodel(subName.trim(), parsed!!)); addingSub = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingSub = false }) { Text("Cancel") } },
        )
    }
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

/** Live text over the whole-house effect: words on the chosen props (matrices by default). */
@Composable
private fun LiveTextSection(params: HouseEffectParams, map: HouseMap, onChange: (HouseEffectParams) -> Unit) {
    val lt = params.liveText
    fun set(v: com.wledcontroller.app.house.LiveText?) = onChange(params.copy(liveText = v))
    SectionTitle("Live text") {
        Switch(checked = lt != null, onCheckedChange = { on ->
            set(if (on) com.wledcontroller.app.house.LiveText(
                runIds = map.runs.filter { it.model?.kind == ModelKind.MATRIX || it.prop == PropType.MATRIX }.map { it.id },
                countdownTo = com.wledcontroller.app.house.LiveText.nextChristmas(java.time.LocalDate.now()).toString(),
            ) else null)
        })
    }
    if (lt == null) {
        Text("Scroll a message, a countdown, the time or the temperature across matrices, over the effect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.wledcontroller.app.house.LiveText.Kind.entries.forEach { k -> FilterChip(selected = lt.kind == k, onClick = { set(lt.copy(kind = k)) }, label = { Text(k.label) }) }
    }
    when (lt.kind) {
        com.wledcontroller.app.house.LiveText.Kind.MESSAGE -> {
            var text by remember { mutableStateOf(lt.message) }
            OutlinedTextField(text, { text = it.take(80); set(lt.copy(message = text)) }, label = { Text("Message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        com.wledcontroller.app.house.LiveText.Kind.COUNTDOWN -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var name by remember { mutableStateOf(lt.countdownName) }
            var day by remember { mutableStateOf(lt.countdownTo) }
            OutlinedTextField(name, { name = it.take(30); set(lt.copy(countdownName = name)) }, label = { Text("Until") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(day, { day = it.take(10); set(lt.copy(countdownTo = day)) }, label = { Text("Date (yyyy-mm-dd)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        com.wledcontroller.app.house.LiveText.Kind.TEMPERATURE -> Text("Uses the show's location (Shows › Schedule).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Unit
    }
    Text("Shows on", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        map.runs.forEach { r ->
            FilterChip(selected = r.id in lt.runIds, onClick = { set(lt.copy(runIds = if (r.id in lt.runIds) lt.runIds - r.id else lt.runIds + r.id)) }, label = { Text(r.name) })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0xFFFFFFFF, 0xFFFF2020, 0xFF20D040, 0xFFFFC040, 0xFF40A0FF).map { it.toInt() }.forEach { c ->
            ColorSwatch(c.toComposeColor(), onClick = { set(lt.copy(color = c)) }, size = 30.dp, selected = lt.color == c)
        }
    }
    GradientSlider(lt.speed, { set(lt.copy(speed = it)) }, label = "Scroll speed", valueText = if (lt.speed < 0.02f) "Still" else "${(lt.speed * 100).toInt()}%", height = 30.dp)
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
    /** Join the points in order with a thin line. */
    connect: Boolean = true,
    /** Drawn under the points; gets how far it's zoomed in. */
    extra: (DrawScope.(Float) -> Unit)? = null,
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
                extra?.invoke(this, scale)
                // Thin line through the run in LED order makes misplaced LEDs obvious.
                val placed = points.withIndex().filter { it.value != null }
                placed.zipWithNext { a, b ->
                    if (connect && b.index == a.index + 1) drawLine(
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

/** Fine-tuning a traced line: nudging points, straightening, and how it hangs. */
@Composable
private fun AdjustLineControls(
    points: Int, selected: Int?, sag: Float, hooks: Int, inches: Boolean, spanCm: Float?, canUndo: Boolean,
    onNudge: (dx: Float, dy: Float) -> Unit,
    onAddAfter: () -> Unit, onDelete: () -> Unit, onLevel: () -> Unit, onStraighten: () -> Unit,
    onSag: (Float) -> Unit, onHooks: (Int) -> Unit, onUndo: () -> Unit, onCancel: () -> Unit, onSave: () -> Unit,
) {
    val step = 0.0012f
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected == null) Text(
            "Pinch to zoom. Drag a point to move it, or tap one to select it and nudge it a little at a time. The start is red.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        ) else Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Point ${selected + 1} of $points", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nudge", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 4.dp))
                    IconButton(onClick = { onNudge(-step, 0f) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left") }
                    IconButton(onClick = { onNudge(0f, -step) }) { Icon(Icons.Filled.KeyboardArrowUp, "Up") }
                    IconButton(onClick = { onNudge(0f, step) }) { Icon(Icons.Filled.KeyboardArrowDown, "Down") }
                    IconButton(onClick = { onNudge(step, 0f) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = selected < points - 1, onClick = onLevel) { Text("Level to next point") }
                    TextButton(enabled = selected < points - 1, onClick = onAddAfter) { Text("Add point after") }
                    TextButton(enabled = points > 2, onClick = onDelete) { Text("Delete point") }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onStraighten) { Text("Make straight") }
            OutlinedButton(enabled = canUndo, onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Undo") }
        }
        Text(
            "Make straight lines up every point on one straight line — for a strip that's straight on the house but was marked a little crooked. Level to next point makes one piece exactly level (or upright).",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Text("Hung from hooks", style = MaterialTheme.typography.titleSmall)
        Text(
            when {
                sag <= 0f -> "No sag: pulled tight."
                spanCm != null -> "Sags ${Math.round(sag * 100)}% of each span — about ${formatLength(sag * spanCm, inches)} in the middle."
                else -> "Sags ${Math.round(sag * 100)}% of each span."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(value = sag, onValueChange = { onSag((Math.round(it * 200) / 200f)) }, valueRange = 0f..0.25f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hooks between points: $hooks", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(enabled = hooks > 0, onClick = { onHooks(hooks - 1) }) { Icon(Icons.Filled.Remove, "Fewer hooks") }
            IconButton(onClick = { onHooks(hooks + 1) }) { Icon(Icons.Filled.Add, "More hooks") }
        }
        Text(
            "Each point you traced is a hook, and the strand droops between them; the lights follow the droop. Hooks spaced evenly along it? Trace just the ends and corners, then add the hooks in between here.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave) { Text("Save") }
        }
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

/** The wiring settings for a matrix, mega tree or star. */
@Composable
private fun ModelEditor(model: PropModel, onChange: (PropModel) -> Unit) {
    @Composable
    fun number(label: String, value: Int, range: IntRange, modifier: Modifier, set: (Int) -> Unit) {
        var text by remember(label) { mutableStateOf(value.toString()) }
        OutlinedTextField(
            text, { t -> text = t.filter(Char::isDigit).take(4); t.toIntOrNull()?.takeIf { it in range }?.let(set) },
            label = { Text(label) }, singleLine = true, modifier = modifier,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (model.kind) {
            ModelKind.MATRIX, ModelKind.MEGA_TREE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                number("Strings", model.strings, 1..512, Modifier.weight(1f)) { onChange(model.copy(strings = it)) }
                number("LEDs per string", model.nodes, 1..2048, Modifier.weight(1f)) { onChange(model.copy(nodes = it)) }
            }
            ModelKind.STAR -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                number("Points", model.points, 3..24, Modifier.weight(1f)) { onChange(model.copy(points = it)) }
                number("LEDs per point", model.nodes, 2..1000, Modifier.weight(1f)) { onChange(model.copy(nodes = it)) }
            }
            ModelKind.CUSTOM -> Text(
                "Imported from xLights: ${model.customW} × ${model.customH} grid, ${model.ledCount} LEDs. Import it again to change it.",
                style = MaterialTheme.typography.bodySmall,
            )
            ModelKind.SINGING_FACE -> {
                number("LEDs per part", model.nodes, 2..500, Modifier.fillMaxWidth()) { onChange(model.copy(nodes = it)) }
                Text(
                    "Wired in this order, the same number of LEDs each: eyes open, eyes closed, outline, then the mouths AI, E, etc, FV, L, MBP, O, rest, U, WQ — as xLights faces are. The Sing effect shows the mouth for each sound of the lyrics.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ModelKind.FACE -> {
                number("LEDs per part", model.nodes, 2..500, Modifier.fillMaxWidth()) { onChange(model.copy(nodes = it)) }
                Text(
                    "Wired in this order: eyes, closed mouth, open mouth, wide-open mouth — the same number of LEDs each. Use the Sing effect to make it sing along to the song.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (model.kind) {
            ModelKind.MATRIX -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = model.vertical, onClick = { onChange(model.copy(vertical = true)) }, label = { Text("Strings run up/down") })
                    FilterChip(selected = !model.vertical, onClick = { onChange(model.copy(vertical = false)) }, label = { Text("Strings run across") })
                }
                Text("First LED is at the", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StartCorner.entries.forEach { c -> FilterChip(selected = model.start == c, onClick = { onChange(model.copy(start = c)) }, label = { Text(c.label) }) }
                }
            }
            ModelKind.MEGA_TREE -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(180, 270, 360).forEach { d -> FilterChip(selected = model.degrees == d, onClick = { onChange(model.copy(degrees = d)) }, label = { Text("$d°") }) }
                    FilterChip(selected = !model.fromBottom, onClick = { onChange(model.copy(fromBottom = false)) }, label = { Text("Strings start at top") })
                    FilterChip(selected = model.fromBottom, onClick = { onChange(model.copy(fromBottom = true)) }, label = { Text("…at bottom") })
                }
            }
            ModelKind.STAR -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Clockwise from the top point", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = model.clockwise, onCheckedChange = { onChange(model.copy(clockwise = it)) })
            }
            ModelKind.FACE, ModelKind.SINGING_FACE, ModelKind.CUSTOM -> Unit
        }
        if (model.kind == ModelKind.MATRIX || model.kind == ModelKind.MEGA_TREE) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zig-zag (each string runs back the other way)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(checked = model.zigzag, onCheckedChange = { onChange(model.copy(zigzag = it)) })
        }
        Text("${model.ledCount} LEDs in all.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** Something a light run can be on: a WLED controller or another pixel controller (E1.31 …). */
internal data class LightTarget(val id: String, val name: String, val address: String, val ledCount: Int, val kind: String?)

@Composable
internal fun rememberLightTargets(): List<LightTarget> {
    val container = LocalAppContainer.current
    val devices by container.devices.list.collectAsStateWithLifecycle()
    val outputs by container.store.outputs.collectAsStateWithLifecycle(emptyList())
    val fixtures by container.store.fixtures.collectAsStateWithLifecycle(emptyList())
    return remember(devices, outputs, fixtures) {
        devices.map { LightTarget(it.id, it.device.name, it.device.host, it.snapshot.value.info?.ledCount ?: 0, null) } +
            outputs.map { LightTarget(it.id, it.name, it.address, it.ledCount, it.protocol.label) } +
            fixtures.map { LightTarget(it.id, it.name, com.wledcontroller.app.data.DmxRegistry.address(it.id), 1, "DMX ${it.type.lowercase()}") }
    }
}
