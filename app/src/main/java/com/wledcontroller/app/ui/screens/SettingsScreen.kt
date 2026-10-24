@file:OptIn(ExperimentalMaterial3Api::class)

package com.wledcontroller.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.show.ShowRunnerService
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenTutorial: () -> Unit = {}, onOpenTest: () -> Unit = {}, onOpenFirmware: () -> Unit = {}, onOpenProjects: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    fun size(bytes: Long) = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
        else -> "${bytes / 1000} KB"
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Saving backup…"; message = null
            runCatching { container.backup.export(uri) }
                .onSuccess { s -> message = "Backed up ${s.devices} controllers, ${s.shows} shows and ${s.files} files (${size(s.bytes)})." }
                .onFailure { message = "Backup failed: ${it.message}" }
            busy = null
        }
    }
    val importer = rememberLauncherForActivityResult(com.wledcontroller.app.ui.components.PickFile()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                onClick = onOpenTutorial,
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tutorial", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "A walk-through of every feature, with shortcuts to try each one. Also on the home screen's ? button.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val projectList by container.projects.list.collectAsStateWithLifecycle()
            val activeProject by container.projects.active.collectAsStateWithLifecycle()
            Card(onClick = onOpenProjects, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Open: ${projectList.firstOrNull { it.id == activeProject }?.name ?: "My house"}. Keep more than one house or layout, each with its own shows, and open xLights show packages (.zip) without touching your own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(onClick = onOpenFirmware, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Controller updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "See every controller's WLED version and update them all to the newest release at once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(onClick = onOpenTest, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Test lights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Test patterns straight on your controllers — chase, alternate, colours, count — to check wiring, colour order and every LED.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "One file with everything: controllers, groups, scenes, favourites, your house map and photo, shows with their songs, imported xLights sequences, playlists and schedules. Save it to Google Drive or your computer, and restore it after a reset or on a new phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exporter.launch("wled-controller-backup-${LocalDate.now()}.zip") }, enabled = busy == null) {
                            Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Back up")
                        }
                        OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, enabled = busy == null) {
                            Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restore")
                        }
                    }
                    AutoBackupSetting(enabled = busy == null) { m -> message = m }
                    busy?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text(it)
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                val alerts by container.health.enabled.collectAsStateWithLifecycle()
                val events by container.health.events.collectAsStateWithLifecycle()
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Controller alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "While a show, the schedule, the jukebox or a whole-house effect is running, every controller is checked every 20 seconds. You get a notification if one stops answering, restarts by itself or has a weak Wi-Fi signal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = alerts, onCheckedChange = { container.health.setEnabled(it) })
                    }
                    if (events.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        events.take(6).forEach { e ->
                            Text(
                                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(e.atMs)) + "  " + e.message,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                val keep by container.store.keepStartupLook.collectAsStateWithLifecycle(true)
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Start up with the last effect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "When a controller is powered on it shows whatever you last chose in the app: the effect with its colours, palette and speed, or the preset or playlist you picked. Saved to the controller a few seconds after you stop changing things.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = keep, onCheckedChange = { on -> scope.launch { container.store.setKeepStartupLook(on) } })
                }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) { RemoteSetting() }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) { LightsDelaySetting() }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) { PixelStyleSetting() }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) { OutputsSetting() }
            }

            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) { FixturesSetting() }
            }

            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?"
            Text("WLED Controller $version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything in the app is replaced with the backup's contents. Your controllers themselves aren't changed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        busy = "Restoring…"; message = null
                        // Stop anything playing that might be reading the files being replaced.
                        ShowRunnerService.stop(context)
                        container.player.stopAll()
                        container.house.stop()
                        runCatching {
                            container.backup.restore(uri).also {
                                // In the simulator, the restored data becomes the virtual house.
                                if (container.simulator.active.value) com.wledcontroller.app.sim.SimHub.load(container.simulator.simFile)
                                // Controllers added since the backup was made stay.
                                else container.store.restoreRememberedDevices()
                            }
                        }
                            .onSuccess { s -> message = "Restored ${s.devices} controllers and ${s.shows} shows (${s.files} files)." }
                            .onFailure { message = "Restore failed: ${it.message}" }
                        busy = null
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

/** The copy kept in Downloads, which is still there after the app is uninstalled. */
@Composable
private fun AutoBackupSetting(enabled: Boolean, onMessage: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val auto = container.autoBackup
    val on by auto.enabled.collectAsStateWithLifecycle()
    val last by auto.lastSaved.collectAsStateWithLifecycle()
    val simActive by container.simulator.active.collectAsStateWithLifecycle()
    var saving by remember { mutableStateOf(false) }
    if (!auto.supported) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Keep a copy in Downloads", fontWeight = FontWeight.SemiBold)
            Text(
                "Saved by itself whenever you leave the app after changing something, to Downloads › ${com.wledcontroller.app.data.AutoBackup.FOLDER}. " +
                    "Updates keep everything anyway; this copy survives uninstalling the app or moving to a new phone — restore it with Restore above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    simActive -> "Paused while the simulator is on (your real setup is put away)."
                    last != null -> "Last saved " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(last!!))
                    else -> "Not saved yet."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Switch(checked = on, onCheckedChange = { auto.setEnabled(it) })
    }
    OutlinedButton(
        enabled = enabled && !saving && !simActive,
        onClick = {
            saving = true
            scope.launch {
                runCatching { auto.saveIfChanged(simulatorOn = false, force = true) }
                    .onSuccess { ok -> onMessage(if (ok) "Saved to Downloads › ${com.wledcontroller.app.data.AutoBackup.FOLDER} › ${com.wledcontroller.app.data.AutoBackup.FILE_NAME}." else "Couldn't save the copy.") }
                    .onFailure { onMessage("Couldn't save the copy: ${it.message}") }
                saving = false
            }
        },
    ) { Text(if (saving) "Saving…" else "Save a copy now") }
}
