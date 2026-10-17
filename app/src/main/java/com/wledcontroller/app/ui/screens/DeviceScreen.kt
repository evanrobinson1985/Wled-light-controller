@file:OptIn(ExperimentalMaterial3Api::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.ConnState
import com.wledcontroller.app.data.DeviceController
import com.wledcontroller.app.ui.components.GradientSlider
import kotlinx.coroutines.launch

private data class Tab(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("Colours", Icons.Filled.ColorLens),
    Tab("Effects", Icons.Filled.AutoAwesome),
    Tab("Palettes", Icons.Filled.Palette),
    Tab("Segments", Icons.Filled.ViewWeek),
    Tab("Presets", Icons.Filled.Bookmarks),
)

@Composable
fun DeviceScreen(deviceId: String, onBack: () -> Unit, onOpenWebUi: (String) -> Unit) {
    val container = LocalAppContainer.current
    val controllers by container.devices.list.collectAsStateWithLifecycle()
    val controller = controllers.firstOrNull { it.id == deviceId }
    if (controller == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Device") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        }) { p -> Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Device not found") } }
        return
    }
    DeviceContent(controller, onBack, onOpenWebUi)
}

@Composable
private fun DeviceContent(controller: DeviceController, onBack: () -> Unit, onOpenWebUi: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val snap by controller.snapshot.collectAsStateWithLifecycle()
    val state = snap.state
    val info = snap.info
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    var showReboot by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(controller.device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(snap.conn)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when (snap.conn) {
                                    ConnState.ONLINE -> if (snap.live) "Live sync" else "Connected"
                                    ConnState.CONNECTING -> "Connecting…"
                                    ConnState.OFFLINE -> "Offline"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showTimer = true }, enabled = snap.online) {
                        Icon(
                            Icons.Filled.Bedtime, "Sleep timer",
                            tint = if (state?.nl?.on == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledIconToggleButton(
                        checked = state?.on == true,
                        onCheckedChange = { controller.setPower(it) },
                        enabled = snap.online,
                        colors = IconButtonDefaults.filledIconToggleButtonColors(),
                    ) { Icon(Icons.Filled.PowerSettingsNew, "Power") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Device info") }, leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { menu = false; showInfo = true },
                            )
                            DropdownMenuItem(
                                text = { Text("WLED web interface") }, leadingIcon = { Icon(Icons.Filled.Language, null) },
                                onClick = { menu = false; onOpenWebUi(controller.id) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Send sync to other WLEDs") },
                                leadingIcon = { Checkbox(checked = state?.udpSend == true, onCheckedChange = null) },
                                enabled = state != null,
                                onClick = { state?.let { controller.setSync(!it.udpSend, it.udpRecv) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Receive sync") },
                                leadingIcon = { Checkbox(checked = state?.udpRecv == true, onCheckedChange = null) },
                                enabled = state != null,
                                onClick = { state?.let { controller.setSync(it.udpSend, !it.udpRecv) } },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Use for Quick Settings tile") }, leadingIcon = { Icon(Icons.Outlined.GridView, null) },
                                onClick = { menu = false; scope.launch { container.store.setTileDevice(controller.id) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menu = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Reboot device") }, leadingIcon = { Icon(Icons.Filled.RestartAlt, null) },
                                enabled = snap.online,
                                onClick = { menu = false; showReboot = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from app") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; showRemove = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (snap.conn == ConnState.OFFLINE) {
                Banner(
                    text = "Can't reach ${controller.device.host}. Check the device has power and is on your Wi-Fi.",
                    action = "Retry",
                    onAction = { controller.reconnect() },
                    error = true,
                )
            }
            if (info?.live == true && state?.lor == 0) {
                Banner(
                    text = "Receiving realtime data" + (info.liveMode?.let { " ($it)" } ?: "") + ". Your changes may be overridden.",
                    action = "Take control",
                    onAction = { controller.overrideLive() },
                )
            }
            if (state != null) {
                GradientSlider(
                    value = state.bri / 255f,
                    onValueChange = { controller.setBrightness((it * 255).toInt().coerceAtLeast(1)) },
                    label = "Brightness",
                    icon = Icons.Filled.Brightness6,
                    valueText = "${(state.bri * 100 / 255).coerceAtLeast(1)}%",
                    enabled = snap.online,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> ColorsTab(controller, snap)
                    1 -> EffectsTab(controller, snap)
                    2 -> PalettesTab(controller, snap)
                    3 -> SegmentsTab(controller, snap)
                    else -> PresetsTab(controller, snap)
                }
            }
        }
    }

    if (showInfo) DeviceInfoSheet(info, controller.device.host, onDismiss = { showInfo = false })
    if (showTimer) NightlightDialog(controller, onDismiss = { showTimer = false })
    if (showRename) {
        TextInputDialog("Rename", controller.device.name, "Name", onDismiss = { showRename = false }) { name ->
            scope.launch { container.store.upsertDevice(controller.device.copy(name = name)) }
        }
    }
    if (showReboot) {
        AlertDialog(
            onDismissRequest = { showReboot = false },
            title = { Text("Reboot ${controller.device.name}?") },
            text = { Text("The lights will go dark for a few seconds while WLED restarts.") },
            confirmButton = { TextButton(onClick = { controller.reboot(); showReboot = false }) { Text("Reboot") } },
            dismissButton = { TextButton(onClick = { showReboot = false }) { Text("Cancel") } },
        )
    }
    if (showRemove) {
        AlertDialog(
            onDismissRequest = { showRemove = false },
            title = { Text("Remove ${controller.device.name}?") },
            text = { Text("This only removes it from the app. Settings on the device are not changed.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemove = false
                    val id = controller.id
                    onBack()
                    scope.launch { container.store.removeDevice(id) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit, error: Boolean = false) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
