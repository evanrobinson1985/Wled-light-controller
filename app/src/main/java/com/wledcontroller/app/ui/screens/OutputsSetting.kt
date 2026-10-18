@file:OptIn(ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
