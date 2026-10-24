package com.wledcontroller.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.SavedLayout
import com.wledcontroller.app.data.SavedModel
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.formatLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private fun fileName(name: String) = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "untitled" }

/**
 * Saved house layouts: keep the house map under a name, open a saved one in its place, and
 * share layouts as files.
 */
@Composable
fun LayoutsDialog(map: HouseMap, onDismiss: () -> Unit, onOpen: (HouseMap) -> Unit) {
    val container = LocalAppContainer.current
    val library = container.library
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layouts by library.layouts.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmOpen by remember { mutableStateOf<SavedLayout?>(null) }
    var confirmDelete by remember { mutableStateOf<SavedLayout?>(null) }
    var sharing by remember { mutableStateOf<SavedLayout?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val l = sharing
        sharing = null
        if (uri != null && l != null) scope.launch {
            note = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { library.exportLayout(l, it) } }
            }.fold({ "Saved ${l.name} to a file." }, { "Couldn't save the file: ${it.message}" })
        }
    }
    val importer = rememberLauncherForActivityResult(com.wledcontroller.app.ui.components.PickFile()) { uri ->
        if (uri != null) scope.launch {
            note = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { library.importLayout(it) } }
            }.fold({ "Added ${it.name}. Open it to use it." }, { "Couldn't read that file: ${it.message}" })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("House layouts") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Save the house map — every run, prop, group, the scale and the photo — under a name, and open it again any time (a different house, this year's display, a test setup…).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text("Name for the current layout") }, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = name.isNotBlank() && (map.runs.isNotEmpty() || map.photoPath != null),
                        onClick = {
                            val n = name
                            scope.launch {
                                val replacing = layouts.any { it.name.equals(n.trim(), true) }
                                note = runCatching { library.saveLayout(n, map) }
                                    .fold({ (if (replacing) "Updated " else "Saved ") + it.name + "." }, { "Couldn't save: ${it.message}" })
                                name = ""
                            }
                        },
                    ) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Save") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("From file")
                    }
                }
                Text(
                    "Saving under the name of a saved layout updates it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (layouts.isEmpty()) {
                    Text("No saved layouts yet.", style = MaterialTheme.typography.bodyMedium)
                }
                layouts.forEach { l ->
                    Surface(
                        onClick = { confirmOpen = l },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${l.map.runs.size} runs" + (if (l.photo != null) " · photo" else "") +
                                        " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(l.savedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { sharing = l; exporter.launch("${fileName(l.name)} - house layout.zip") }) { Icon(Icons.Filled.Share, "Save to a file") }
                            IconButton(onClick = { confirmDelete = l }) { Icon(Icons.Outlined.Delete, "Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    confirmOpen?.let { l ->
        AlertDialog(
            onDismissRequest = { confirmOpen = null },
            title = { Text("Open ${l.name}?") },
            text = { Text("It replaces the house map you have now. Save the current one first if you want to keep it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = null
                    scope.launch {
                        runCatching { library.openLayout(l) }
                            .onSuccess { onOpen(it) }
                            .onFailure { note = "Couldn't open it: ${it.message}" }
                    }
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { confirmOpen = null }) { Text("Cancel") } },
        )
    }
    confirmDelete?.let { l ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${l.name}?") },
            text = { Text("The saved layout goes; the house map you're using now isn't changed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; scope.launch { library.deleteLayout(l.id) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Saved light models: put one on the house, share it (this app's file, or an xLights model to
 * use in xLights), or add one from a file.
 */
@Composable
fun ModelsDialog(onDismiss: () -> Unit, onUse: (SavedModel) -> Unit) {
    val container = LocalAppContainer.current
    val library = container.library
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val models by library.models.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<SavedModel?>(null) }
    var sharing by remember { mutableStateOf<Pair<SavedModel, Boolean>?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    fun writeTo(uri: android.net.Uri?, text: (SavedModel) -> String) {
        val s = sharing
        sharing = null
        if (uri != null && s != null) scope.launch {
            note = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text(s.first).toByteArray()) } }
            }.fold({ "Saved ${s.first.name} to a file." }, { "Couldn't save the file: ${it.message}" })
        }
    }
    val jsonExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> writeTo(uri, library::modelFile) }
    val xmodelExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> writeTo(uri) { SavedModel.toXmodel(it) } }
    val importer = rememberLauncherForActivityResult(com.wledcontroller.app.ui.components.PickFile()) { uri ->
        if (uri != null) scope.launch {
            note = runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                library.saveModel(library.parseModelFile(text))
            }.fold({ "Added ${it.name}." }, { "Couldn't read that model: ${it.message}" })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My light models") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Props you've kept for reuse. Keep one with \"Save as model\" on any run in the house map; tap one here to put it on the house.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Add from a file")
                }
                Text(
                    "This app's model files, or xLights models (.xmodel).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (models.isEmpty()) Text("No saved models yet.", style = MaterialTheme.typography.bodyMedium)
                models.forEach { m ->
                    Surface(
                        onClick = { onUse(m) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${m.prop.label} · ${m.ledCount} LEDs" +
                                        (if (m.widthCm != null && m.heightCm != null) " · ${formatLength(m.widthCm, true)} × ${formatLength(m.heightCm, true)}" else "") +
                                        (if (m.submodels.isNotEmpty()) " · ${m.submodels.size} parts" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.foundation.layout.Box {
                                IconButton(onClick = { menuFor = m.id }) { Icon(Icons.Filled.Share, "Save to a file") }
                                DropdownMenu(expanded = menuFor == m.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(
                                        text = { Column { Text("Model file"); Text("For this app on another phone", style = MaterialTheme.typography.labelSmall) } },
                                        onClick = { menuFor = null; sharing = m to false; jsonExporter.launch("${fileName(m.name)}.wledmodel.json") },
                                    )
                                    DropdownMenuItem(
                                        text = { Column { Text("xLights model (.xmodel)"); Text("To use in xLights", style = MaterialTheme.typography.labelSmall) } },
                                        onClick = { menuFor = null; sharing = m to true; xmodelExporter.launch("${fileName(m.name)}.xmodel") },
                                    )
                                }
                            }
                            IconButton(onClick = { confirmDelete = m }) { Icon(Icons.Outlined.Delete, "Delete", Modifier.size(22.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${m.name}?") },
            text = { Text("Only the saved model goes; props already on the house stay.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; scope.launch { library.deleteModel(m.id) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
