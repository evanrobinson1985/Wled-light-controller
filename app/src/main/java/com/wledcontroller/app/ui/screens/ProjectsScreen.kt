package com.wledcontroller.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.data.ProjectInfo
import com.wledcontroller.app.data.XlightsPackage
import com.wledcontroller.app.LocalAppContainer
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Projects: switch between houses or layouts, start a new one, or open an xLights package as one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(onBack: () -> Unit, onOpenMap: () -> Unit) {
    val container = LocalAppContainer.current
    val projects = container.projects
    val list by projects.list.collectAsStateWithLifecycle()
    val active by projects.active.collectAsStateWithLifecycle()
    val simActive by container.simulator.active.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<Naming?>(null) }
    var deleting by remember { mutableStateOf<ProjectInfo?>(null) }
    var peeked by remember { mutableStateOf<XlightsPackage.Peek?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "Unpacking…"; message = null
            runCatching { container.xlightsPackage.peek(uri) }
                .onSuccess { peeked = it }
                .onFailure { message = "Couldn't open that package: ${it.message}" }
            busy = null
        }
    }

    fun run(label: String, block: suspend () -> Unit) = scope.launch {
        busy = label; message = null
        runCatching { block() }.onFailure { message = "That didn't work: ${it.message}" }
        busy = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Each project has its own house photo, props, groups, shows, songs and settings. Switching puts the open one away exactly as it is, so you can always come back to it. Your controllers are in every project.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (simActive) Text(
                "Leave the simulator first to switch or open projects.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            val enabled = busy == null && !simActive

            list.forEach { p ->
                val open = p.id == active
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            val detail = listOfNotNull(
                                if (open) "Open now" else null,
                                p.note.ifBlank { null },
                                if (p.createdAt > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(p.createdAt)) else null,
                            ).joinToString(" · ")
                            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!open) TextButton(enabled = enabled, onClick = { run("Opening ${p.name}…") { projects.switchTo(p.id) } }) { Text("Open") }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; naming = Naming.Rename(p) })
                                if (!open) DropdownMenuItem(text = { Text("Delete") }, enabled = enabled, onClick = { menu = false; deleting = p })
                            }
                        }
                    }
                }
            }

            Button(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FolderZip, null)
                Text("  Open xLights package (.zip)")
            }
            Text(
                "A zipped xLights show folder: the layout, controllers, house picture, sequences, songs, pictures and videos. It opens as a new project with the props placed on the picture just as in xLights.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { naming = Naming.New }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null)
                Text("  New empty project")
            }

            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(it)
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (message?.startsWith("Opened") == true) OutlinedButton(onClick = onOpenMap) { Text("See the house map") }
        }
    }

    naming?.let { n ->
        var name by remember(n) { mutableStateOf(if (n is Naming.Rename) n.project.name else "") }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(if (n is Naming.Rename) "Rename project" else "New empty project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (n is Naming.New) Text("Starts with no house photo, props or shows. The open project is kept as it is.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    naming = null
                    when (n) {
                        is Naming.Rename -> projects.rename(n.project.id, name.trim())
                        Naming.New -> run("Starting $name…") { projects.create(name.trim()) }
                    }
                }) { Text(if (n is Naming.Rename) "Rename" else "Create") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Its house photo, props, shows and songs are deleted from this phone. This can't be undone.") },
            confirmButton = { TextButton(onClick = { deleting = null; projects.delete(p.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    peeked?.let { pk ->
        var name by remember(pk) { mutableStateOf(pk.name) }
        AlertDialog(
            onDismissRequest = { peeked = null },
            title = { Text("Open xLights package") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${pk.props} props" + (if (pk.shows.isNotEmpty()) " and ${pk.shows.size} show${if (pk.shows.size == 1) "" else "s"}: ${pk.shows.joinToString()}" else "") +
                            ".\n\nIt opens as a new project; the one open now is kept as it is, and you can switch back here any time. Bringing in videos can take a minute.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    peeked = null
                    scope.launch {
                        busy = "Opening…"; message = null
                        runCatching { container.xlightsPackage.open(name.trim()) { line -> busy = "$line…" } }
                            .onSuccess { r ->
                                message = buildString {
                                    append("Opened with ${r.props} props, ${r.groups} groups and ${r.shows} show${if (r.shows == 1) "" else "s"}.")
                                    if (r.unassigned > 0) append(" ${r.unassigned} props are on xLights controllers that aren't yours yet: choose their controller on the house map (Controllers).")
                                    if (r.skippedEffects > 0) append(" ${r.skippedEffects} effects have no match here yet and were left out.")
                                    if (r.missing.isNotEmpty()) append(" Not in the zip: ${r.missing.take(5).joinToString()}${if (r.missing.size > 5) " …" else ""}.")
                                }
                            }
                            .onFailure { message = "Couldn't open it: ${it.message}. The project you had open is back." }
                        busy = null
                    }
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { peeked = null }) { Text("Cancel") } },
        )
    }

}

private sealed interface Naming {
    data object New : Naming
    data class Rename(val project: ProjectInfo) : Naming
}
