@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.DeviceController
import com.wledcontroller.app.data.DeviceGroup
import com.wledcontroller.app.data.FoundDevice
import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.data.Scene
import com.wledcontroller.app.data.SceneEntry
import com.wledcontroller.app.data.WledApi
import com.wledcontroller.app.data.WledInfo
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.KeyValueRow
import com.wledcontroller.app.ui.components.parseHex
import com.wledcontroller.app.ui.components.rgbHex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// ------------------------------------------------------------------ Add / discover devices

@Composable
fun AddDeviceSheet(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val saved by container.store.devices.collectAsStateWithLifecycle(emptyList())
    val found = remember { mutableStateListOf<FoundDevice>() }
    var scanning by remember { mutableStateOf(true) }
    var manualHost by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val job = launch {
            container.discovery.discover().collect { d ->
                if (found.none { it.host == d.host }) found.add(d)
            }
        }
        delay(20_000)
        scanning = false
        job.cancel()
    }

    fun add(name: String, host: String) {
        scope.launch {
            container.store.upsertDevice(SavedDevice(UUID.randomUUID().toString(), name, WledApi.normalizeHost(host)))
        }
    }

    val savedHosts = saved.map { WledApi.normalizeHost(it.host) }.toSet()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Add lights", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("On this network", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(found, key = { it.host }) { d ->
                    val added = WledApi.normalizeHost(d.host) in savedHosts
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(d.name) },
                        supportingContent = { Text("${d.host} · ${d.ledCount} LEDs · v${d.version}") },
                        trailingContent = {
                            if (added) Icon(Icons.Filled.CheckCircle, "Added", tint = MaterialTheme.colorScheme.primary)
                            else TextButton(onClick = { add(d.name, d.host) }) { Text("Add") }
                        },
                    )
                }
            }
            if (!scanning && found.isEmpty()) {
                Text(
                    "No WLED devices found. Check that your phone is on the same Wi-Fi, or enter the IP address below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (found.size > 1 && found.any { WledApi.normalizeHost(it.host) !in savedHosts }) {
                TextButton(onClick = { found.filter { WledApi.normalizeHost(it.host) !in savedHosts }.forEach { add(it.name, it.host) } }) {
                    Text("Add all")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Add by address", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            val err = manualError
            OutlinedTextField(
                value = manualHost,
                onValueChange = { manualHost = it; manualError = null },
                label = { Text("IP or hostname (e.g. 192.168.1.50)") },
                singleLine = true,
                isError = err != null,
                supportingText = if (err != null) { { Text(err) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                if (manualError != null) {
                    TextButton(onClick = { add(manualHost.trim(), manualHost); onDismiss() }) { Text("Add anyway") }
                }
                Button(
                    enabled = manualHost.isNotBlank() && !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            val info = container.api.probe(manualHost)
                            checking = false
                            if (info != null) {
                                add(info.name, manualHost)
                                onDismiss()
                            } else {
                                manualError = "No WLED device answered at that address"
                            }
                        }
                    },
                ) { Text(if (checking) "Checking…" else "Connect") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ------------------------------------------------------------------ Groups & scenes

@Composable
fun GroupEditorDialog(group: DeviceGroup, devices: List<DeviceController>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(group.name) }
    val selected = remember { group.deviceIds.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group.name.isEmpty()) "New group" else "Edit group") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                devices.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = d.id in selected, onCheckedChange = { if (it) selected.add(d.id) else selected.remove(d.id) })
                        Text(d.device.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = {
                    scope.launch { container.store.upsertGroup(group.copy(name = name.trim(), deviceIds = selected.toList())) }
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SaveSceneDialog(devices: List<DeviceController>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    val online = devices.filter { it.snapshot.value.online }
    val selected = remember { online.map { it.id }.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save scene") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Stores colours, effects, palettes, brightness and segments of the selected lights.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Scene name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                if (online.isEmpty()) Text("No devices are online.")
                online.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = d.id in selected, onCheckedChange = { if (it) selected.add(d.id) else selected.remove(d.id) })
                        Text(d.device.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = {
                    val chosen = online.filter { it.id in selected }
                    val entries = chosen.map { SceneEntry(it.id, it.captureState().toString()) }
                    val color = chosen.firstNotNullOfOrNull { it.snapshot.value.state?.activeSegment?.color(0)?.rgb } ?: 0
                    scope.launch { container.store.addScene(Scene(UUID.randomUUID().toString(), name.trim(), entries, color)) }
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ Device dialogs

@Composable
fun TextInputDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HexColorDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(rgbHex(initial)) }
    val parsed = parseHex(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hex colour") },
        text = {
            OutlinedTextField(
                text, { text = it.take(7) },
                label = { Text("RRGGBB") },
                prefix = { Text("#") },
                singleLine = true,
                isError = parsed == null,
            )
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let(onConfirm); onDismiss() }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val NightlightModes = listOf("Instant", "Fade", "Color fade", "Sunrise")

@Composable
fun NightlightDialog(controller: DeviceController, onDismiss: () -> Unit) {
    val snap by controller.snapshot.collectAsStateWithLifecycle()
    val nl = snap.state?.nl
    var minutes by remember { mutableIntStateOf(nl?.dur?.coerceIn(1, 255) ?: 30) }
    var mode by remember { mutableIntStateOf(nl?.mode ?: 1) }
    var target by remember { mutableIntStateOf(nl?.tbri ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (nl?.on == true) {
                    val rem = nl.rem
                    Text(
                        if (rem >= 0) "Running · ${rem / 60}m ${rem % 60}s left" else "Running",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                GradientSlider(
                    value = (minutes - 1) / 254f,
                    onValueChange = { minutes = 1 + (it * 254).toInt() },
                    label = "Duration",
                    valueText = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min",
                )
                Spacer(Modifier.height(16.dp))
                GradientSlider(
                    value = target / 255f,
                    onValueChange = { target = (it * 255).toInt() },
                    label = "End brightness",
                    valueText = if (target == 0) "Off" else "${target * 100 / 255}%",
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NightlightModes.forEachIndexed { i, label ->
                        FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (mode) {
                        0 -> "Switches to the end brightness when the timer finishes."
                        1 -> "Gradually dims to the end brightness."
                        2 -> "Dims while fading to the secondary colour."
                        else -> "Brightens and warms up like a sunrise."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { controller.setNightlight(true, minutes, mode, target); onDismiss() }) { Text("Start") }
        },
        dismissButton = {
            Row {
                if (nl?.on == true) {
                    TextButton(onClick = { controller.setNightlight(false, minutes, mode, target); onDismiss() }) { Text("Stop timer") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
fun SavePresetDialog(controller: DeviceController, onDismiss: () -> Unit) {
    val snap by controller.snapshot.collectAsStateWithLifecycle()
    val used = snap.presets?.map { it.id }?.toSet() ?: emptySet()
    val nextFree = (1..250).firstOrNull { it !in used } ?: 250
    var name by remember { mutableStateOf("") }
    var idText by remember { mutableStateOf(nextFree.toString()) }
    var includeBri by remember { mutableStateOf(true) }
    var segBounds by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val id = idText.toIntOrNull()?.takeIf { it in 1..250 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    idText, { idText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Slot (1–250)") }, singleLine = true, isError = id == null,
                    supportingText = { if (id != null && id in used) Text("Overwrites an existing preset") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeBri, { includeBri = it }); Text("Include brightness")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(segBounds, { segBounds = it }); Text("Save segment bounds")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && id != null, onClick = {
                controller.savePreset(id!!, name.trim(), includeBri, segBounds)
                scope.launch { delay(700); controller.loadPresets() }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeviceInfoSheet(info: WledInfo?, host: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text("Device info", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            if (info == null) {
                Text("Not connected.")
            } else {
                KeyValueRow("Name", info.name)
                KeyValueRow("Firmware", "${info.brand} ${info.version}".trim())
                KeyValueRow("Hardware", info.arch.uppercase())
                KeyValueRow("Address", host)
                KeyValueRow("MAC", info.mac.chunked(2).joinToString(":").uppercase())
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                KeyValueRow("LEDs", "${info.ledCount}" + (if (info.hasWhite) " · RGBW" else "") + (if (info.hasCct) " · CCT" else ""))
                KeyValueRow("Frame rate", "${info.fps} fps")
                KeyValueRow(
                    "Est. current",
                    if (info.maxPowerMa > 0) "${info.powerMa} mA of ${info.maxPowerMa} mA" else "${info.powerMa} mA",
                )
                KeyValueRow("Est. power", String.format("%.1f W", info.powerMa * 5 / 1000f))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                KeyValueRow("Wi-Fi signal", "${info.wifiSignal}% (${info.rssi} dBm)")
                KeyValueRow("Wi-Fi channel", "${info.wifiChannel}")
                KeyValueRow("Uptime", formatUptime(info.uptimeSec))
                KeyValueRow("Free memory", "${info.freeHeap / 1024} KB")
                KeyValueRow("Sync port", "${info.udpPort}")
                if (info.live) KeyValueRow("Realtime source", listOfNotNull(info.liveMode, info.liveIp).joinToString(" · "))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatUptime(sec: Int): String {
    val d = sec / 86400; val h = sec % 86400 / 3600; val m = sec % 3600 / 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (d > 0 || h > 0) append("${h}h ")
        append("${m}m")
    }
}
