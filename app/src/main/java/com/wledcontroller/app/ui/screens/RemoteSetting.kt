package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.ConnState
import com.wledcontroller.app.data.DeviceController
import com.wledcontroller.app.data.MqttLink
import com.wledcontroller.app.data.MqttSettings
import com.wledcontroller.app.data.RemoteSetup
import com.wledcontroller.app.data.Route
import kotlinx.coroutines.launch

/**
 * Settings › Control from anywhere: how the app reaches the lights away from home. Each
 * controller is tried at home first, then at its remote address (VPN), then through the broker.
 */
@Composable
internal fun RemoteSetting() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val saved by container.store.mqtt.collectAsStateWithLifecycle(null)
    val controllers by container.devices.list.collectAsStateWithLifecycle()
    // Edited here, saved with the button.
    var draft by remember { mutableStateOf<MqttSettings?>(null) }
    LaunchedEffect(saved) { if (draft == null) draft = saved ?: MqttSettings() }
    val s = draft ?: return
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Control from anywhere", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "At home the app talks to your lights over Wi-Fi. When it can't reach a controller that way, it tries the controller's remote address (a VPN), then the MQTT broker below, and goes back to Wi-Fi when you're home.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("VPN — most secure, everything works", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        Text(
            "Install Tailscale (free) on your phone and on something always on at home (a Raspberry Pi, a PC, or a router that supports it) and set that up as a \"subnet router\" for your home network. Then your controllers' home addresses work from anywhere and nothing else is needed. With your router's own VPN (WireGuard / OpenVPN) it's the same. If a controller has a different address from outside, set it in that controller's menu › Remote access.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MQTT broker — no VPN needed", style = MaterialTheme.typography.titleSmall)
                Text(
                    "On/off, brightness, colours, effects, palettes and presets. Shows and whole-house effects need Wi-Fi or a VPN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.enabled, onCheckedChange = { draft = s.copy(enabled = it) })
        }
        if (s.enabled) {
            OutlinedTextField(s.host, { draft = s.copy(host = it.trim()) }, label = { Text("Broker address (e.g. broker.emqx.io)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    s.port.toString(), { v -> v.filter(Char::isDigit).take(5).toIntOrNull()?.let { draft = s.copy(port = it) } },
                    label = { Text("App port") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("Encrypted (TLS)", style = MaterialTheme.typography.bodySmall)
                Switch(checked = s.tls, onCheckedChange = { draft = s.copy(tls = it, port = if (it && s.port == 1883) 8883 else if (!it && s.port == 8883) 1883 else s.port) })
            }
            OutlinedTextField(
                s.wledPort.toString(), { v -> v.filter(Char::isDigit).take(5).toIntOrNull()?.let { draft = s.copy(wledPort = it) } },
                label = { Text("Controllers' port (unencrypted, usually 1883)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(s.user, { draft = s.copy(user = it.trim()) }, label = { Text("User") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    s.password, { draft = s.copy(password = it) }, label = { Text("Password") }, singleLine = true, modifier = Modifier.weight(1f),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            Text(
                "Topics start with ${s.prefix}/ — random, so nobody else on the broker stumbles on your lights. WLED can't encrypt MQTT, so its link to the broker is plain: use a broker account with a user and password rather than an open public broker if you can.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Shows in Home Assistant", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Adds a \"Light show\" device to Home Assistant on the same broker: an on/off switch, a choice of playlist, schedule or jukebox, Next song and Stop buttons, and what's playing. Share the switch with Google Home or Alexa in Home Assistant to say \"Hey Google, turn on the light show\". Works while the app is running — keep a schedule on (Shows › Schedule) so Android keeps it going.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.homeAssistant, onCheckedChange = { draft = s.copy(homeAssistant = it) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = busy == null && draft != saved, onClick = {
                scope.launch { container.store.setMqtt(s); message = "Saved." }
            }) { Text("Save") }
            if (s.enabled) {
                OutlinedButton(enabled = busy == null && s.host.isNotBlank(), onClick = {
                    scope.launch {
                        busy = "Connecting to ${s.host}…"; message = null
                        message = runCatching { MqttLink.test(s) }.fold(
                            { "Connected to the broker." },
                            { "Couldn't connect: ${it.message ?: it.javaClass.simpleName}" },
                        )
                        busy = null
                    }
                }) { Text("Test") }
            }
        }
        if (s.enabled) {
            val home = controllers.filter { it.snapshot.value.online && it.snapshot.value.route == Route.LOCAL && !com.wledcontroller.app.sim.SimHub.isSimulated(it.device.host) }
            OutlinedButton(
                enabled = busy == null && s.host.isNotBlank() && home.isNotEmpty(),
                onClick = {
                    scope.launch {
                        busy = "Setting up controllers…"; message = null
                        container.store.setMqtt(s)
                        val results = home.map { c ->
                            val topic = c.device.mqttTopic?.takeIf { it.startsWith(s.prefix + "/") } ?: RemoteSetup.topicFor(s, c.device)
                            runCatching {
                                RemoteSetup.configure(container.api, c.apiHost, s, topic)
                                container.store.upsertDevice(c.device.copy(mqttTopic = topic))
                            }.fold({ "${c.device.name}: ready" }, { "${c.device.name}: ${it.message ?: "failed"}" })
                        }
                        message = results.joinToString("\n") + "\nThey restart to connect to the broker."
                        busy = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (home.isEmpty()) "Set up controllers (be at home to do this)" else "Set up ${home.size} controller${if (home.size == 1) "" else "s"} for MQTT") }
        }
        busy?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(it)
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** One controller's ways in from afar: its remote address and MQTT topic. */
@Composable
internal fun RemoteAccessDialog(controller: DeviceController, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val snap by controller.snapshot.collectAsStateWithLifecycle()
    val mqtt by container.store.mqtt.collectAsStateWithLifecycle(null)
    val d = controller.device
    var remote by remember { mutableStateOf(d.remoteHost.orEmpty()) }
    var topic by remember { mutableStateOf(d.mqttTopic.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val atHome = snap.conn == ConnState.ONLINE && snap.route == Route.LOCAL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remote access") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (snap.conn) {
                        ConnState.ONLINE -> "Connected now through: ${snap.route.label}."
                        ConnState.CONNECTING -> "Connecting…"
                        ConnState.OFFLINE -> "Not reachable right now."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Home address: ${d.host}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    remote, { remote = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Remote address (optional)") },
                    placeholder = { Text("100.x.y.z or lights.example.com:8080") },
                )
                Text(
                    "Used when the home address doesn't answer: a Tailscale / VPN address, or a forwarded hostname. Not needed with a Tailscale subnet router.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    topic, { topic = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("MQTT topic") }, enabled = mqtt?.enabled == true,
                )
                val s = mqtt
                if (s?.usable == true) {
                    OutlinedButton(
                        enabled = atHome && !busy,
                        onClick = {
                            scope.launch {
                                busy = true; message = null
                                val t = topic.ifBlank { RemoteSetup.topicFor(s, d) }
                                message = runCatching {
                                    RemoteSetup.configure(container.api, controller.apiHost, s, t)
                                    topic = t
                                    container.store.upsertDevice(controller.device.copy(remoteHost = remote.ifBlank { null }, mqttTopic = t))
                                }.fold({ "Sent. ${d.name} restarts and connects to the broker." }, { "Couldn't set it up: ${it.message}" })
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (atHome) "Send the broker settings to ${d.name}" else "Be at home to send it the broker settings") }
                } else {
                    Text(
                        "Set up an MQTT broker in Settings › Control from anywhere to use one.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    container.store.upsertDevice(controller.device.copy(remoteHost = remote.ifBlank { null }, mqttTopic = topic.ifBlank { null }))
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
