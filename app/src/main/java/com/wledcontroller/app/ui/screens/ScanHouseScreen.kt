package com.wledcontroller.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.house.model3d.ArScanView
import com.wledcontroller.app.house.model3d.ArScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scanning the house with the phone: walk along it with the camera on it, and the app builds a
 * coloured 3D model as you go (what's been scanned shows green-tinted over the camera's view).
 */
@Composable
fun ScanHouseScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val scanner = remember { ArScanner(context.applicationContext) }
    val view = remember { ArScanView(context, scanner) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var problem by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf<String?>(null) }
    var showTips by remember { mutableStateOf(true) }
    val status by scanner.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(granted, attempt) {
        if (!granted) return@LaunchedEffect
        val r = scanner.open(context as Activity)
        when (r) {
            null -> { problem = null; ready = true; scanner.resume(); view.onResume() }
            "install" -> problem = "Installing Google Play Services for AR… come back here when it's done."
            else -> problem = r
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> if (ready) { scanner.resume(); view.onResume() } else if (granted && problem != null) attempt++
                Lifecycle.Event.ON_PAUSE -> { view.onPause(); scanner.pause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view.onPause(); scanner.close() }
    }

    fun finish() {
        scanner.recording = false; recording = false
        scope.launch {
            finishing = "Building your 3D house…"
            runCatching {
                val mesh = withContext(Dispatchers.Default) { scanner.volume.extract() }
                finishing = "Saving…"
                val m = container.models.saveScanned(mesh)
                val old = container.store.houseMap.first().model3d
                container.store.updateHouseMap { it.copy(model3d = m, runs = it.runs.map { r -> r.copy(points3d = null) }) }
                if (old != null) container.models.delete(old)
            }.onSuccess { finishing = null; onDone() }
                .onFailure { finishing = null; problem = it.message ?: "Couldn't build the model." }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted && ready) AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

        // Top: what to do now.
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text("Scan your house", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text(
                when {
                    !granted -> "The scanner needs the camera."
                    problem != null -> problem!!
                    else -> status.hint
                },
                style = MaterialTheme.typography.bodyMedium, color = Color.White,
            )
            if (status.views > 0) Text(
                "${status.views} views · about ${status.areaM2.toInt()} m² of surface",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f),
            )
            if (!granted) TextButton(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Allow the camera") }
        }

        // Bottom: start, pause, finish.
        if (granted && ready && problem == null) Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(enabled = status.tracking || recording, onClick = { recording = !recording; scanner.recording = recording }) {
                Text(if (recording) "Pause" else if (status.views == 0) "Start" else "Carry on")
            }
            OutlinedButton(
                enabled = status.views > 3 && finishing == null,
                onClick = { finish() },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            ) { Text("Finish") }
        }

        finishing?.let {
            Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Text(it, color = Color.White, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }

    if (showTips && granted) AlertDialog(
        onDismissRequest = { showTips = false },
        title = { Text("How to scan") },
        text = {
            Text(
                "• Scan in daylight.\n" +
                    "• Stand 2–4 m (6–12 ft) from the house. Depth works up to about 5 m.\n" +
                    "• Start at one corner. Press Start and walk slowly along the house, sweeping the phone up to the roof's edge and down to the ground as you go.\n" +
                    "• Go round to the sides, porches and anything set back, and step in under porch roofs.\n" +
                    "• Scanned parts show green-tinted over the camera. Go back over gaps.\n" +
                    "• The top of the roof can't be seen from the ground; its edges can, and that's where lights go.\n" +
                    "• Press Finish when you're done. A whole house takes a few minutes.",
            )
        },
        confirmButton = { TextButton(onClick = { showTips = false }) { Text("Got it") } },
    )
}
