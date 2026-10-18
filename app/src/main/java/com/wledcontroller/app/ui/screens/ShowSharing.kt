package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.allSubmodels
import com.wledcontroller.app.show.ChannelRange
import com.wledcontroller.app.show.TrackTarget
import com.wledcontroller.app.show.XsqElement
import com.wledcontroller.app.show.XsqSequence

/** Everything on the house map a sequence's model can be matched to, with a name for each. */
private fun targetsOf(map: HouseMap): List<Pair<String, TrackTarget>> =
    listOf("Whole house" to TrackTarget(TrackTarget.Kind.HOUSE)) +
        map.groups.map { "Group: ${it.name}" to TrackTarget(TrackTarget.Kind.GROUP, it.id) } +
        map.runs.map { it.name to TrackTarget(TrackTarget.Kind.PROP, it.id) } +
        map.runs.flatMap { r -> r.allSubmodels().map { "${r.name} / ${it.name}" to TrackTarget.submodel(r.id, it.name) } }

/** The prop, group or part with the same name as an xLights model, if there is one. */
internal fun autoTarget(el: XsqElement, map: HouseMap): TrackTarget? {
    fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)
    val run = map.runs.firstOrNull { same(it.name, el.name) }
    if (el.submodel != null) {
        val sub = run?.allSubmodels()?.firstOrNull { same(it.name, el.submodel) } ?: return null
        return TrackTarget.submodel(run.id, sub.name)
    }
    if (run != null) return TrackTarget(TrackTarget.Kind.PROP, run.id)
    map.groups.firstOrNull { same(it.name, el.name) }?.let { return TrackTarget(TrackTarget.Kind.GROUP, it.id) }
    return null
}

/** Matches each xLights model in a sequence to a prop, group or part on the house map. */
@Composable
internal fun XsqMappingDialog(xsq: XsqSequence, map: HouseMap, onDismiss: () -> Unit, onImport: (Map<String, TrackTarget?>) -> Unit) {
    val options = remember(map) { targetsOf(map) }
    val chosen = remember(xsq) { mutableStateMapOf<String, TrackTarget?>().apply { xsq.elements.forEach { put(it.label, autoTarget(it, map)) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match xLights models") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose where each model's effects go on your house. Models with the same name as a prop or group are matched already; skipped ones are left out." +
                        (xsq.mediaFile?.let { "\n\nMade for \"$it\" — add the song afterwards with the ♪ button." } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                xsq.elements.forEach { el ->
                    var open by remember { mutableStateOf(false) }
                    Column {
                        Text(el.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${el.effectCount} effects" + if (el.layers.size > 1) " on ${el.layers.size} layers" else "", style = MaterialTheme.typography.labelSmall)
                        Box {
                            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    options.firstOrNull { it.second == chosen[el.label] }?.first ?: "Skip",
                                    Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                DropdownMenuItem(text = { Text("Skip") }, onClick = { chosen[el.label] = null; open = false })
                                options.forEach { (label, target) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { chosen[el.label] = target; open = false })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.values.any { it != null }, onClick = { onImport(chosen.toMap()) }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Where each controller's lights ended up in an exported .fseq. */
@Composable
internal fun ExportResultDialog(ranges: List<ChannelRange>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exported") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Saved at 40 frames a second. To play it on Falcon Player or another player, set up each controller with these channels (RGB, 3 per LED):",
                    style = MaterialTheme.typography.bodySmall,
                )
                ranges.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${r.startChannel}–${r.endChannel} · ${r.leds} LEDs", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "Importing it back into this app maps the channels the same way automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
