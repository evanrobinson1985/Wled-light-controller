@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.ConnState
import com.wledcontroller.app.data.DeviceController
import com.wledcontroller.app.data.DeviceGroup
import com.wledcontroller.app.data.Scene
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.SectionTitle
import com.wledcontroller.app.ui.components.toComposeColor
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenDevice: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenHouse: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSimulator: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val controllers by container.devices.list.collectAsStateWithLifecycle()
    val groups by container.store.groups.collectAsStateWithLifecycle(emptyList())
    val scenes by container.store.scenes.collectAsStateWithLifecycle(emptyList())
    val simActive by container.simulator.active.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<DeviceGroup?>(null) }
    var showSaveScene by remember { mutableStateOf(false) }
    var deleteScene by remember { mutableStateOf<Scene?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(if (simActive) "Lights · simulator" else "Lights") },
                actions = {
                    IconButton(onClick = { controllers.forEach { it.setPower(false) } }, enabled = controllers.isNotEmpty()) {
                        Icon(Icons.Filled.PowerSettingsNew, "All off")
                    }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Search, "Find devices") }
                    IconButton(onClick = onOpenSimulator) {
                        Icon(Icons.Filled.Science, "Light show simulator", tint = if (simActive) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add device") },
            )
        },
    ) { padding ->
        if (controllers.isEmpty()) {
            EmptyHome(Modifier.padding(padding), onAdd = { showAdd = true }, onTrySimulator = onOpenSimulator)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (simActive) item {
                    Card(
                        onClick = onOpenSimulator,
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Science, null, Modifier.size(28.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Simulator on", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "These are virtual lights on a virtual house. Your real setup comes back when you leave.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                item {
                    SectionTitle("Scenes") {
                        TextButton(onClick = { showSaveScene = true }) { Text("Save current") }
                    }
                    if (scenes.isEmpty()) {
                        Text(
                            "Capture every light's current look as a one-tap scene.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(scenes, key = { it.id }) { scene ->
                                SceneChip(
                                    scene = scene,
                                    onClick = {
                                        scene.entries.forEach { e -> container.devices.get(e.deviceId)?.applyScene(e.state) }
                                    },
                                    onLongClick = { deleteScene = scene },
                                )
                            }
                        }
                    }
                }

                item {
                    val scheduleOn by container.store.scheduleOn.collectAsStateWithLifecycle(false)
                    val runner by container.showRunner.state.collectAsStateWithLifecycle()
                    if (scheduleOn || runner.running) {
                        Card(
                            onClick = onOpenSchedule,
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, null, Modifier.size(28.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (runner.nowPlaying != null) "Show playing" else "Show schedule on",
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        when {
                                            runner.nowPlaying != null -> runner.nowPlaying + (runner.playlistName?.let { " · $it" } ?: "")
                                            runner.nextStart != null -> "Next show " + runner.nextStart!!.format(
                                                java.time.format.DateTimeFormatter.ofPattern("EEE h:mm a"),
                                            )
                                            else -> "Waiting for a show window"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    val houseRunning by container.house.running.collectAsStateWithLifecycle()
                    Card(onClick = onOpenHouse, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(top = 12.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Home, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Whole house", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (houseRunning) "Effect running across your lights" else "Map your lights and run effects across every controller",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (houseRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item { SectionTitle("Devices") }
                items(controllers, key = { it.id }) { c ->
                    DeviceCard(c, onClick = { onOpenDevice(c.id) })
                }

                item {
                    SectionTitle("Groups") {
                        TextButton(onClick = { editGroup = DeviceGroup(java.util.UUID.randomUUID().toString(), "", emptyList()) }) {
                            Text("New group")
                        }
                    }
                    if (groups.isEmpty()) {
                        Text(
                            "Group lights to control several devices together.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(groups, key = { it.id }) { g ->
                    GroupCard(g, controllers.filter { it.id in g.deviceIds }, onClick = { onOpenGroup(g.id) })
                }
            }
        }
    }

    if (showAdd) AddDeviceSheet(onDismiss = { showAdd = false })
    editGroup?.let { g -> GroupEditorDialog(g, controllers, onDismiss = { editGroup = null }) }
    if (showSaveScene) SaveSceneDialog(controllers, onDismiss = { showSaveScene = false })
    deleteScene?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteScene = null },
            title = { Text("Delete scene?") },
            text = { Text("\"${s.name}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { container.store.removeScene(s.id) }; deleteScene = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteScene = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyHome(modifier: Modifier, onAdd: () -> Unit, onTrySimulator: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lightbulb, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("No lights yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Make sure your phone is on the same Wi-Fi as your WLED controllers, then scan for them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Filled.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("Find devices")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onTrySimulator) {
            Icon(Icons.Filled.Science, null)
            Spacer(Modifier.width(8.dp))
            Text("No lights to hand? Try the simulator")
        }
    }
}

@Composable
fun StatusDot(conn: ConnState, modifier: Modifier = Modifier) {
    val color = when (conn) {
        ConnState.ONLINE -> Color(0xFF4CAF50)
        ConnState.CONNECTING -> Color(0xFFFFC107)
        ConnState.OFFLINE -> Color(0xFFF44336)
    }
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun DeviceCard(controller: DeviceController, onClick: () -> Unit) {
    val snap by controller.snapshot.collectAsStateWithLifecycle()
    val state = snap.state
    val seg = state?.activeSegment
    val on = state?.on == true && snap.online
    val glow = seg?.color(0)?.toComposeColor() ?: MaterialTheme.colorScheme.outline
    val container by animateColorAsState(
        if (on) glow.copy(alpha = 0.16f).compositeOverSurface() else MaterialTheme.colorScheme.surfaceContainer,
        label = "card",
    )

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .shadow(if (on) 12.dp else 0.dp, CircleShape, ambientColor = glow, spotColor = glow)
                        .clip(CircleShape)
                        .background(if (on) glow else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Lightbulb, null, tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(controller.device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(snap.conn)
                        Spacer(Modifier.width(6.dp))
                        val effect = seg?.let { snap.effects.getOrNull(it.fx) }
                        val subtitle = when (snap.conn) {
                            ConnState.OFFLINE -> "Offline · ${controller.device.host}"
                            ConnState.CONNECTING -> "Connecting…"
                            ConnState.ONLINE -> listOfNotNull(
                                if (state?.on == true) "${(state.bri * 100 / 255).coerceAtLeast(1)}%" else "Off",
                                effect,
                                snap.info?.let { "${it.ledCount} LEDs" },
                            ).joinToString(" · ")
                        }
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Switch(checked = on, onCheckedChange = { controller.setPower(it) }, enabled = snap.online)
            }
            if (snap.online && state != null) {
                Spacer(Modifier.height(14.dp))
                GradientSlider(
                    value = state.bri / 255f,
                    onValueChange = { controller.setBrightness((it * 255).toInt().coerceAtLeast(1)) },
                    height = 32.dp,
                )
            }
        }
    }
}

@Composable
private fun Color.compositeOverSurface(): Color {
    val s = MaterialTheme.colorScheme.surfaceContainer
    val a = alpha
    return Color(
        red = red * a + s.red * (1 - a),
        green = green * a + s.green * (1 - a),
        blue = blue * a + s.blue * (1 - a),
    )
}

@Composable
private fun GroupCard(group: DeviceGroup, members: List<DeviceController>, onClick: () -> Unit) {
    val snaps = members.map { it.snapshot.collectAsStateWithLifecycle().value }
    val anyOn = snaps.any { it.online && it.state?.on == true }
    val onlineCount = snaps.count { it.online }
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Workspaces, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${members.size} devices · $onlineCount online",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = anyOn, onCheckedChange = { v -> members.forEach { it.setPower(v) } }, enabled = onlineCount > 0)
        }
    }
}

@Composable
private fun SceneChip(scene: Scene, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (scene.color != 0) scene.color.toComposeColor() else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp), tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(scene.name, style = MaterialTheme.typography.labelLarge)
        }
    }
}
