@file:OptIn(ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.DmxChannel
import com.wledcontroller.app.data.DmxFixture
import com.wledcontroller.app.data.DmxRegistry
import com.wledcontroller.app.data.DmxRole
import com.wledcontroller.app.data.OutputProtocol
import com.wledcontroller.app.data.PixelOutput
import com.wledcontroller.app.house.DdpSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Non-WLED pixel controllers the app streams shows and house effects to. */
@Composable
internal fun OutputsSetting() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val outputs by container.store.outputs.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<PixelOutput?>(null) }
    var testing by remember { mutableStateOf<String?>(null) }

    fun test(o: PixelOutput) = scope.launch {
        testing = o.id
        // Red, green, blue, white for a second each, then off.
        withContext(Dispatchers.IO) {
            runCatching {
                DdpSender().use { s ->
                    for (rgb in listOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF, 0)) {
                        val buf = ByteArray(o.ledCount * 3) { i -> ((rgb shr (16 - (i % 3) * 8)) and 0xFF).toByte() }
                        repeat(10) { s.send(o.address, buf); delay(100) }
                    }
                }
            }
        }
        testing = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Other controllers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = PixelOutput(name = "Controller ${outputs.size + 1}", host = "") }) { Icon(Icons.Filled.Add, null); Text("Add") }
        }
        Text(
            "Falcon, Kulp, ESPixelStick, Falcon Player and other E1.31 (sACN), Art-Net or DDP controllers. Put their lights on the house map like any controller; shows and whole-house effects stream to them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        outputs.forEach { o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${o.protocol.label} · ${o.host} · ${o.ledCount} LEDs" +
                            if (o.protocol != OutputProtocol.DDP) " · universes ${o.startUniverse}–${o.startUniverse + o.universes - 1}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { test(o) }, enabled = testing == null) { Text(if (testing == o.id) "Testing…" else "Test") }
                IconButton(onClick = { editing = o }) { Icon(Icons.Outlined.Edit, "Edit ${o.name}") }
                IconButton(onClick = { scope.launch { container.store.removeOutput(o.id) } }) { Icon(Icons.Outlined.Delete, "Remove ${o.name}") }
            }
        }
    }

    editing?.let { o -> OutputDialog(o, onDismiss = { editing = null }) { saved -> scope.launch { container.store.upsertOutput(saved) }; editing = null } }
}

@Composable
private fun OutputDialog(output: PixelOutput, onDismiss: () -> Unit, onSave: (PixelOutput) -> Unit) {
    var name by remember { mutableStateOf(output.name) }
    var host by remember { mutableStateOf(output.host) }
    var protocol by remember { mutableStateOf(output.protocol) }
    var universe by remember { mutableStateOf(output.startUniverse.toString()) }
    var perUniverse by remember { mutableStateOf(output.channelsPerUniverse.toString()) }
    var leds by remember { mutableStateOf(output.ledCount.toString()) }
    val u = universe.toIntOrNull()?.takeIf { it in 0..63999 }
    val c = perUniverse.toIntOrNull()?.takeIf { it in 3..512 }
    val n = leds.toIntOrNull()?.takeIf { it in 1..20000 }
    val valid = name.isNotBlank() && host.isNotBlank() && n != null && (protocol == OutputProtocol.DDP || (u != null && c != null))
    fun numberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) = @Composable {
        OutlinedTextField(
            value, { onChange(it.filter(Char::isDigit).take(6)) }, label = { Text(label) }, singleLine = true, modifier = modifier,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Other controller") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(host, { host = it.trim() }, label = { Text("IP address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutputProtocol.entries.forEach { p -> FilterChip(selected = protocol == p, onClick = { protocol = p }, label = { Text(p.label) }) }
                }
                numberField(leds, { leds = it }, "LEDs")()
                if (protocol != OutputProtocol.DDP) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        numberField(universe, { universe = it }, "First universe", Modifier.weight(1f))()
                        numberField(perUniverse, { perUniverse = it }, "Channels / universe", Modifier.weight(1f))()
                    }
                    Text(
                        "Match the controller's input settings. 510 channels (170 pixels) per universe is the usual choice. Data is sent straight to the IP address (unicast).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(output.copy(name = name.trim(), host = host, protocol = protocol, startUniverse = u ?: 1, channelsPerUniverse = c ?: 510, ledCount = n!!))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** DMX lights: pars, washes, strobes and moving heads on E1.31 / Art-Net outputs. */
@Composable
internal fun FixturesSetting() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val fixtures by container.store.fixtures.collectAsStateWithLifecycle(emptyList())
    val outputs by container.store.outputs.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<DmxFixture?>(null) }
    var browsing by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf<String?>(null) }
    val dmxOutputs = outputs.filter { it.protocol != OutputProtocol.DDP }

    fun test(f: DmxFixture) = scope.launch {
        testing = f.id
        // White, red, green, blue; moving heads also sweep left to right and back.
        withContext(Dispatchers.IO) {
            runCatching {
                DdpSender().use { s ->
                    val steps = listOf(0xFFFFFF to 0.3f, 0xFF0000 to 0.4f, 0x00FF00 to 0.6f, 0x0000FF to 0.7f, 0xFFFFFF to 0.5f, 0 to 0.5f)
                    for ((rgb, pan) in steps) {
                        repeat(8) { s.send(DmxRegistry.address(f.id), DmxRegistry.frame(rgb, pan, 0.5f)); delay(100) }
                    }
                }
            }
        }
        testing = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DMX fixtures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { browsing = true }, enabled = dmxOutputs.isNotEmpty()) { Text("Library") }
            TextButton(onClick = {
                editing = DmxFixture(name = "Par ${fixtures.size + 1}", outputId = dmxOutputs.first().id, channels = DmxFixture.PRESETS.first().second)
            }, enabled = dmxOutputs.isNotEmpty()) { Icon(Icons.Filled.Add, null); Text("Add") }
        }
        Text(
            if (dmxOutputs.isEmpty()) "Add an E1.31 or Art-Net controller above first (your DMX interface or node, e.g. a Falcon or an Enttec ODE) — fixtures are sent through it."
            else "Pars, washes, strobes and moving heads. Put each on the house map (one tap) and shows colour them like any light; moving heads also follow the pan and tilt set on effects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        fixtures.forEach { f ->
            val out = outputs.firstOrNull { it.id == f.outputId }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${f.type} · ${f.channels.size} ch · ${out?.name ?: "missing controller"} · universe ${f.universe}, address ${f.address}–${f.address + f.channels.size - 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { test(f) }, enabled = testing == null && out != null) { Text(if (testing == f.id) "Testing…" else "Test") }
                IconButton(onClick = { editing = f }) { Icon(Icons.Outlined.Edit, "Edit ${f.name}") }
                IconButton(onClick = { scope.launch { container.store.removeFixture(f.id) } }) { Icon(Icons.Outlined.Delete, "Remove ${f.name}") }
            }
        }
    }

    if (browsing) VendorBrowser(dmx = true, onDismiss = { browsing = false }) { xml, m ->
        browsing = false
        val parsed = runCatching { com.wledcontroller.app.house.XlightsModels.parseDmxFixture(xml.byteInputStream()) }.getOrNull()
        if (parsed != null) editing = parsed.copy(name = m.name.take(40), outputId = dmxOutputs.firstOrNull()?.id ?: "", address = nextAddress(fixtures))
    }
    editing?.let { f ->
        FixtureDialog(f, dmxOutputs, fixtures, onDismiss = { editing = null }) { saved -> scope.launch { container.store.upsertFixture(saved) }; editing = null }
    }
}

/** The first free address after the fixtures already set up (in their first universe). */
internal fun nextAddress(fixtures: List<DmxFixture>): Int =
    (fixtures.maxOfOrNull { it.address + it.channels.size } ?: 1).coerceAtMost(512)

/** Name, where it's patched (controller, universe, address) and what each channel does. */
@Composable
internal fun FixtureDialog(
    fixture: DmxFixture,
    outputs: List<PixelOutput>,
    others: List<DmxFixture>,
    onDismiss: () -> Unit,
    onSave: (DmxFixture) -> Unit,
) {
    var name by remember { mutableStateOf(fixture.name) }
    var outputId by remember { mutableStateOf(fixture.outputId.ifBlank { outputs.firstOrNull()?.id ?: "" }) }
    var universe by remember { mutableStateOf(fixture.universe.toString()) }
    var address by remember { mutableStateOf(fixture.address.toString()) }
    val channels = remember { androidx.compose.runtime.mutableStateListOf(*fixture.channels.toTypedArray()) }
    var presetMenu by remember { mutableStateOf(false) }
    var outputMenu by remember { mutableStateOf(false) }
    val u = universe.toIntOrNull()?.takeIf { it in 0..63999 }
    val a = address.toIntOrNull()?.takeIf { it in 1..512 }
    val fits = a != null && a + channels.size - 1 <= 512
    val clash = others.filter { it.id != fixture.id && it.outputId == outputId && it.universe == u && a != null }
        .firstOrNull { o -> o.address <= a!! + channels.size - 1 && a <= o.address + o.channels.size - 1 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DMX fixture") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.OutlinedButton(onClick = { outputMenu = true }) {
                        Text("Through: " + (outputs.firstOrNull { it.id == outputId }?.name ?: "choose controller"))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = outputMenu, onDismissRequest = { outputMenu = false }) {
                        outputs.forEach { o ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text("${o.name} · ${o.protocol.label}") }, onClick = { outputId = o.id; outputMenu = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(universe, { universe = it.filter(Char::isDigit).take(5) }, label = { Text("Universe") }, singleLine = true,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(address, { address = it.filter(Char::isDigit).take(3) }, label = { Text("Address") }, singleLine = true,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                when {
                    !fits -> Text("Doesn't fit: ${channels.size} channels from that address run past 512.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    clash != null -> Text("Overlaps ${clash.name} (${clash.address}–${clash.address + clash.channels.size - 1}).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    else -> Text("Uses channels $a–${a!! + channels.size - 1}. Set the same address on the fixture.", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Channels (${channels.size})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { presetMenu = true }) { Text("Presets") }
                        androidx.compose.material3.DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                            DmxFixture.PRESETS.forEach { (label, chs) ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { channels.clear(); channels.addAll(chs); presetMenu = false })
                            }
                        }
                    }
                }
                channels.forEachIndexed { i, c -> ChannelRow(i, c, onChange = { channels[i] = it }, onRemove = { channels.removeAt(i) }) }
                TextButton(onClick = { channels.add(DmxChannel(DmxRole.NONE)) }, enabled = channels.size < 512) { Icon(Icons.Filled.Add, null); Text("Add channel") }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && outputId.isNotBlank() && u != null && fits && clash == null && channels.isNotEmpty(), onClick = {
                onSave(fixture.copy(name = name.trim(), outputId = outputId, universe = u!!, address = a!!, channels = channels.toList()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChannelRow(index: Int, channel: DmxChannel, onChange: (DmxChannel) -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${index + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            androidx.compose.material3.OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(channel.role.label + if (channel.name.isNotBlank()) " · ${channel.name}" else "", maxLines = 1)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DmxRole.entries.forEach { r ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(r.label) }, onClick = {
                        onChange(channel.copy(role = r, value = if (r == DmxRole.SHUTTER && channel.value == 0) 255 else channel.value)); open = false
                    })
                }
            }
        }
        if (channel.role == DmxRole.FIXED || channel.role == DmxRole.SHUTTER) {
            var text by remember(index) { mutableStateOf(channel.value.toString()) }
            OutlinedTextField(
                text, { t -> text = t.filter(Char::isDigit).take(3); t.toIntOrNull()?.takeIf { it in 0..255 }?.let { onChange(channel.copy(value = it)) } },
                singleLine = true, modifier = Modifier.width(72.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Remove channel ${index + 1}") }
    }
}
