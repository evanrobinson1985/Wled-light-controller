package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.PropGroup
import com.wledcontroller.app.house.XlLayout
import com.wledcontroller.app.house.XlightsModels
import java.util.UUID

/** A dropdown of the app's controllers (WLED and others). */
@Composable
private fun TargetPicker(targets: List<LightTarget>, chosen: String?, onChoose: (String?) -> Unit, allowNone: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(targets.firstOrNull { it.id == chosen }?.name ?: if (allowNone) "Don't import" else "Choose controller", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("Don't import") }, onClick = { onChoose(null); open = false })
            targets.forEach { t -> DropdownMenuItem(text = { Text(t.name + (t.kind?.let { " · $it" } ?: "")) }, onClick = { onChoose(t.id); open = false }) }
        }
    }
}

/**
 * Brings an xLights layout onto the house map: matches xLights controllers to the app's, picks
 * which props to bring, and returns the updated map. Props arrive unplaced, ready to trace or place.
 */
@Composable
internal fun LayoutImportDialog(layout: XlLayout, map: HouseMap, targets: List<LightTarget>, onDismiss: () -> Unit, onImport: (HouseMap, Int) -> Unit) {
    fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)
    val controllerMap = remember(layout) {
        mutableStateMapOf<String, String?>().apply {
            layout.controllers.forEach { c -> put(c, targets.firstOrNull { same(it.name, c) }?.id ?: targets.firstOrNull()?.id) }
        }
    }
    val needsDefault = layout.models.any { it.controller == null && it.chainedAfter == null }
    var defaultTarget by remember { mutableStateOf(targets.firstOrNull()?.id) }
    val chosen = remember(layout) { mutableStateListOf<String>().apply { addAll(layout.models.map { it.name }) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import xLights layout") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${layout.models.size} props and ${layout.groups.size} groups. Each prop keeps its name, wiring, LED count and parts; afterwards trace or place each one on your photo. Props with the same name as one on your map replace it but keep where it's placed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (layout.controllers.isNotEmpty()) Text("Controllers", style = MaterialTheme.typography.labelLarge)
                layout.controllers.forEach { c ->
                    Column {
                        Text("xLights: $c", fontWeight = FontWeight.SemiBold)
                        TargetPicker(targets, controllerMap[c], { controllerMap[c] = it })
                    }
                }
                if (needsDefault) {
                    Text("Props on a start channel (no controller named)", style = MaterialTheme.typography.labelLarge)
                    TargetPicker(targets, defaultTarget, { defaultTarget = it })
                    Text(
                        "Their first LED is worked out from the channel (3 channels per LED). Check it in each prop afterwards if your controller is split into ports.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Props", style = MaterialTheme.typography.labelLarge)
                layout.models.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(m.name in chosen, { on -> if (on) chosen.add(m.name) else chosen.remove(m.name) })
                        Column(Modifier.weight(1f)) {
                            Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${m.displayAs} · ${m.ledCount} LEDs" + if (m.submodels.isNotEmpty()) " · ${m.submodels.size} parts" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty() && targets.isNotEmpty(), onClick = {
                val starts = XlightsModels.resolveStarts(layout.models, null)
                val runs = map.runs.toMutableList()
                var added = 0
                for (m in layout.models.filter { it.name in chosen }) {
                    val (ctrl, first) = starts[m.name] ?: (null to 0)
                    val deviceId = (if (ctrl != null) controllerMap[ctrl] else defaultTarget) ?: continue
                    val existing = runs.indexOfFirst { same(it.name, m.name) }
                    val old = runs.getOrNull(existing)
                    // A replaced prop keeps where it's placed if its kind of layout hasn't changed.
                    val keepPlace = old != null && (old.model == null) == (m.model == null)
                    val run = LightRun(
                        id = old?.id ?: UUID.randomUUID().toString(),
                        name = m.name, deviceId = deviceId, startLed = first, count = m.ledCount,
                        points = if (keepPlace) old!!.points else emptyList(),
                        prop = m.prop, model = m.model, submodels = m.submodels,
                        spacingCm = if (keepPlace) old!!.spacingCm else null,
                    )
                    if (existing >= 0) runs[existing] = run else runs += run
                    added++
                }
                val groups = map.groups.toMutableList()
                for ((name, members) in layout.groups) {
                    val ids = members.mapNotNull { n -> runs.firstOrNull { same(it.name, n) }?.id }
                    if (ids.isEmpty()) continue
                    val i = groups.indexOfFirst { same(it.name, name) }
                    if (i >= 0) groups[i] = groups[i].copy(runIds = ids) else groups += PropGroup(UUID.randomUUID().toString(), name, ids)
                }
                onImport(map.copy(runs = runs, groups = groups), added)
            }) { Text("Import ${chosen.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
