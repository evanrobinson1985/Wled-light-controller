@file:OptIn(ExperimentalMaterial3Api::class)

package com.wledcontroller.app.ui.screens

import com.wledcontroller.app.ui.components.LocalPixelStyle
import com.wledcontroller.app.ui.components.drawPixel
import com.wledcontroller.app.ui.components.neighbourGaps
import com.wledcontroller.app.ui.components.pixelRadius
import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.house.LedScanner
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ScanProgress
import com.wledcontroller.app.house.ScanTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

private enum class ScanStep { SETUP, SCANNING, DONE }

/**
 * Maps lights automatically: with the phone propped up facing the house, each LED is lit in turn
 * and located by the camera. Results land in the house map for fine-tuning in the editor.
 */
@Composable
fun CameraScanScreen(onBack: () -> Unit, onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by container.devices.list.collectAsStateWithLifecycle()
    val scanner = remember { LedScanner() }

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) permission.launch(Manifest.permission.CAMERA) }

    val selected = remember { mutableStateListOf<String>() }
    LaunchedEffect(devices.map { it.id }) {
        if (selected.isEmpty()) selected.addAll(devices.filter { it.snapshot.value.online }.map { it.id })
    }
    var careful by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(ScanStep.SETUP) }
    var progress by remember { mutableStateOf<ScanProgress?>(null) }
    var targets by remember { mutableStateOf<List<ScanTarget>>(emptyList()) }
    var results by remember { mutableStateOf<List<List<MapPoint?>>>(emptyList()) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }

    val activity = context as? Activity
    fun lockOrientation(lock: Boolean) {
        activity?.requestedOrientation = if (lock) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(Unit) { onDispose { lockOrientation(false); scanJob?.cancel() } }

    val settle = if (careful) 350L else 180L
    val chosen = devices.filter { it.id in selected }
    val totalLeds = chosen.sumOf { it.snapshot.value.info?.ledCount ?: 0 }
    // Each LED: on until seen, then off until it's seen to go dark.
    val estimateSec = (totalLeds * (settle * 2 + 120) / 1000 + chosen.size * 2 + 5).toInt()

    fun startScan() {
        val t = chosen.mapNotNull { c ->
            val n = c.snapshot.value.info?.ledCount ?: return@mapNotNull null
            if (n <= 0) null else ScanTarget(c.id, c.device.host, c.device.name, 0, n)
        }
        if (t.isEmpty()) { error = "Couldn't read the LED count of the selected controllers. Make sure they're online."; return }
        targets = t; error = null; step = ScanStep.SCANNING; progress = null
        startedAt = System.currentTimeMillis()
        lockOrientation(true)
        // Stop any whole-house effect so it doesn't fight the scan.
        container.house.stop()
        scanJob = scope.launch {
            try {
                photo = scanner.capturePhoto()
                // Darker exposure makes single LEDs stand out from the house.
                camera?.let { cam ->
                    val ex = cam.cameraInfo.exposureState
                    if (ex.isExposureCompensationSupported) cam.cameraControl.setExposureCompensationIndex(ex.exposureCompensationRange.lower / 2)
                }
                // Every other controller is held dark too, so its effects can't be taken for LEDs.
                val others = devices.filter { it.id !in selected && it.snapshot.value.online }
                    .associate { it.device.host to (it.snapshot.value.info?.ledCount ?: 0) }
                    .filterValues { it > 0 }
                results = scanner.scan(t, settle, others) { p -> progress = p }
                step = ScanStep.DONE
            } catch (e: CancellationException) {
                step = ScanStep.SETUP
                throw e
            } catch (e: Exception) {
                error = "Scan failed: ${e.message}"
                step = ScanStep.SETUP
            } finally {
                camera?.cameraControl?.setExposureCompensationIndex(0)
                lockOrientation(false)
            }
        }
    }

    fun save() {
        val bmp = photo ?: return
        scope.launch {
            val (path, aspect) = container.house.importBitmap(bmp)
            val scannedIds = targets.map { it.deviceId }.toSet()
            container.store.updateHouseMap { m ->
                val kept = m.runs.filterNot { it.deviceId in scannedIds }
                val newRuns = targets.mapIndexed { i, t ->
                    LightRun(
                        id = UUID.randomUUID().toString(),
                        name = t.name,
                        deviceId = t.deviceId,
                        startLed = t.startLed,
                        count = t.count,
                        ledPoints = results.getOrElse(i) { List(t.count) { null } },
                    )
                }
                m.copy(photoPath = path, aspect = aspect, runs = kept + newRuns)
            }
            onFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera scan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val preview: @Composable (Modifier) -> Unit = { m ->
                Box(m.background(Color.Black)) {
                    if (hasCamera) {
                        CameraPreview(scanner, onCamera = { camera = it }, modifier = Modifier.fillMaxSize())
                        val found = if (step == ScanStep.DONE) results else progress?.found ?: emptyList()
                        FoundOverlay(found, scanner.frameAspect, Modifier.fillMaxSize())
                    } else {
                        Text(
                            "Camera permission is needed to scan.",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                }
            }
            val panel: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (step) {
                        ScanStep.SETUP -> {
                            Text(
                                "Prop your phone up so all the lights are in view, and keep it completely still. Works best at dusk or after dark.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text("Controllers to scan", style = MaterialTheme.typography.titleSmall)
                            devices.forEach { c ->
                                val snap = c.snapshot.value
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = c.id in selected,
                                        onCheckedChange = { if (it) selected.add(c.id) else selected.remove(c.id) },
                                        enabled = snap.online,
                                    )
                                    Text(c.device.name + (snap.info?.let { " · ${it.ledCount} LEDs" } ?: if (!snap.online) " · offline" else ""))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !careful, onClick = { careful = false }, label = { Text("Normal") })
                                FilterChip(selected = careful, onClick = { careful = true }, label = { Text("Careful (slower Wi-Fi)") })
                            }
                            if (totalLeds > 0) {
                                Text(
                                    "About ${estimateSec / 60} min ${estimateSec % 60} s for $totalLeds LEDs.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                onClick = { startScan() },
                                enabled = hasCamera && chosen.isNotEmpty() && camera != null,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text("Start scan") }
                            Text(
                                "All your lights go off, then each LED flashes white on its own until the camera sees it light up and go dark again, so lamps, TVs and other lights in the picture are ignored. Turn off any lights you can and keep the phone still. Your lights go back to normal when the scan ends.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ScanStep.SCANNING -> {
                            val p = progress
                            Text("Scanning…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (p == null) {
                                Text("Taking the background photo…")
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            } else {
                                val t = targets[p.targetIndex]
                                Text("${t.name} — LED ${p.ledIndex} of ${p.ledCount}")
                                LinearProgressIndicator(progress = { p.ledIndex.toFloat() / p.ledCount }, modifier = Modifier.fillMaxWidth())
                                val foundNow = p.found.sumOf { l -> l.count { it != null } }
                                Text("$foundNow lights found so far", style = MaterialTheme.typography.labelMedium)
                                if (p.targetCount > 1) Text("Controller ${p.targetIndex + 1} of ${p.targetCount}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text("Keep the phone still.", color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(onClick = { scanJob?.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                        }
                        ScanStep.DONE -> {
                            Text("Scan complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            targets.forEachIndexed { i, t ->
                                val r = results.getOrElse(i) { emptyList() }
                                val n = r.count { it != null }
                                Text("${t.name}: found $n of ${t.count}")
                            }
                            Text(
                                "Next you can move any light that's slightly off, fill in missed ones, or remove stray ones in the map editor. This photo becomes the map background; runs on controllers you didn't scan are kept but may need retracing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save & fine-tune") }
                            OutlinedButton(onClick = { step = ScanStep.SETUP }, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
                        }
                    }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(1f).fillMaxHeight())
                    panel(Modifier.width(320.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.fillMaxWidth().weight(1f))
                    panel(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

/** Live camera preview plus an analyser feeding upright frames to [scanner]. */
@Composable
private fun CameraPreview(scanner: LedScanner, onCamera: (Camera) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                keepScreenOn = true
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val ratio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                val preview = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
                    .build()
                preview.setSurfaceProvider(view.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(ratio)
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        val rot = image.imageInfo.rotationDegrees
                        scanner.frameAspect = if (rot % 180 == 0) image.width.toFloat() / image.height else image.height.toFloat() / image.width
                        if (scanner.wantsFrames || scanner.wantsPhoto) {
                            val bmp = image.toBitmap()
                            val upright = if (rot != 0) {
                                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
                            } else bmp
                            scanner.onFrame(upright)
                        }
                    } finally {
                        image.close()
                    }
                }
                runCatching {
                    provider.unbindAll()
                    onCamera(provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}

/** Draws found LEDs over the preview, matching the FIT_CENTER letterboxing of the camera frame. */
@Composable
private fun FoundOverlay(found: List<List<MapPoint?>>, frameAspect: Float, modifier: Modifier) {
    val colors = listOf(Color(0xFF6BFF8A), Color(0xFF6BC8FF), Color(0xFFFFB86B), Color(0xFFFF7BA8))
    val pixel = LocalPixelStyle.current
    Canvas(modifier) {
        val viewAspect = size.width / size.height
        val (cw, ch) = if (viewAspect > frameAspect) size.height * frameAspect to size.height else size.width to size.width / frameAspect
        val ox = (size.width - cw) / 2; val oy = (size.height - ch) / 2
        found.forEachIndexed { t, list ->
            val col = colors[t % colors.size]
            val gaps = neighbourGaps(list, frameAspect)
            list.forEachIndexed { i, p ->
                if (p != null) {
                    val o = Offset(ox + p.x * cw, oy + p.y * ch)
                    val r = pixelRadius(pixel, gaps[i], pictureHeight = ch)
                    drawCircle(col, r, o)
                    drawCircle(Color.Black.copy(alpha = 0.6f), r + 1.5.dp.toPx(), o, style = Stroke(1.dp.toPx()))
                }
            }
        }
    }
}
