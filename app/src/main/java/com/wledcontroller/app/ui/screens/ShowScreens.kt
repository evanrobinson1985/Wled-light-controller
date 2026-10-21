@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.flow.first
import androidx.compose.material3.AssistChip
import com.wledcontroller.app.house.allSubmodels
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.horizontalScroll
import com.wledcontroller.app.ui.components.LocalPixelStyle
import com.wledcontroller.app.ui.components.drawPixel
import com.wledcontroller.app.ui.components.drawSpotlight
import com.wledcontroller.app.ui.components.neighbourGaps
import com.wledcontroller.app.ui.components.pixelRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.HolidayThemes
import com.wledcontroller.app.house.Direction
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.show.AudioAnalyzer
import com.wledcontroller.app.show.AudioEnvelope
import com.wledcontroller.app.show.Clip
import com.wledcontroller.app.show.ValueCurve
import com.wledcontroller.app.show.Transition
import com.wledcontroller.app.show.CurveShape
import com.wledcontroller.app.show.BlendMode
import com.wledcontroller.app.show.ClipClipboard
import com.wledcontroller.app.show.SeqEffect
import com.wledcontroller.app.show.LyricSync
import com.wledcontroller.app.show.RenderStyle
import com.wledcontroller.app.show.Sequence
import com.wledcontroller.app.show.SequenceRenderer
import com.wledcontroller.app.show.Track
import com.wledcontroller.app.show.TrackTarget
import com.wledcontroller.app.show.formatTime
import com.wledcontroller.app.ui.components.ColorPickerPanel
import com.wledcontroller.app.ui.components.ColorSwatch
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.QuickColors
import com.wledcontroller.app.ui.components.SectionTitle
import com.wledcontroller.app.ui.components.themeBrush
import com.wledcontroller.app.ui.components.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ================================================================== show list

@Composable
fun ShowsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onEditMap: () -> Unit, onSchedule: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val shows by container.sequences.all.collectAsStateWithLifecycle()
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Sequence?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val controllers by container.devices.list.collectAsStateWithLifecycle()
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            importError = null
            runCatching {
                container.sequences.importFseq(context, uri, controllers.map { it.id to (it.snapshot.value.info?.ledCount ?: 0) })
            }.onSuccess { onOpen(it.id) }.onFailure { importError = it.message }
        }
    }
    var importMenu by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    if (checking) {
        val outputs by container.store.outputs.collectAsStateWithLifecycle(emptyList())
        val fixtures by container.store.fixtures.collectAsStateWithLifecycle(emptyList())
        val issues = remember(map, shows, controllers, outputs, fixtures) {
            val known = controllers.associate { it.id to it.snapshot.value.info?.ledCount } +
                outputs.associate { it.id to null } + fixtures.associate { it.id to null }
            com.wledcontroller.app.show.SetupCheck.run(map, known, shows)
        }
        SetupCheckDialog(issues, onDismiss = { checking = false }, onEditMap = { checking = false; onEditMap() })
    }
    // An xLights .xsq waiting for its models to be matched to the house.
    var xsqPending by remember { mutableStateOf<Pair<com.wledcontroller.app.show.XsqSequence, String>?>(null) }
    val xsqImporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            importError = null
            runCatching {
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.removeSuffix(".xsq") ?: "xLights sequence"
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { com.wledcontroller.app.show.XsqImport.parse(it) }
                } to name
            }.onSuccess { (x, name) ->
                if (x.elements.isEmpty()) importError = "That sequence has no model effects to import." else xsqPending = x to name
            }.onFailure { importError = it.message ?: "Couldn't read that sequence." }
        }
    }
    // Exporting a show to xLights.
    var exportingXsq by remember { mutableStateOf<Sequence?>(null) }
    var xsqNote by remember { mutableStateOf<String?>(null) }
    val xsqExporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/xml")
    ) { uri ->
        val s = exportingXsq
        exportingXsq = null
        if (uri != null && s != null) scope.launch {
            runCatching {
                val xml = com.wledcontroller.app.show.XsqExport.export(s, map)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(xml.toByteArray()) }
                }
            }.onSuccess {
                xsqNote = "Saved. In xLights, open it with your layout: props, groups and parts with the same names get the effects" +
                    (if (s.audioFile != null) "; point it at the song when xLights asks." else ".")
            }.onFailure { importError = "Export failed: ${it.message}" }
        }
    }
    xsqNote?.let { n ->
        AlertDialog(onDismissRequest = { xsqNote = null }, title = { Text("Exported to xLights") }, text = { Text(n) },
            confirmButton = { TextButton(onClick = { xsqNote = null }) { Text("OK") } })
    }
    // Exporting a show to .fseq.
    var exporting by remember { mutableStateOf<Sequence?>(null) }
    var exported by remember { mutableStateOf<List<com.wledcontroller.app.show.ChannelRange>?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val s = exporting
        if (uri != null && s != null) scope.launch {
            exportBusy = true; importError = null
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { out ->
                        val fseq = s.fseqFile
                        if (fseq != null) { java.io.File(fseq).inputStream().use { it.copyTo(out) }; emptyList() }
                        else {
                            val layout = SequenceRenderer.prepare(map, s.tracks)
                            val env = s.audioFile?.let { runCatching { AudioAnalyzer.envelope(it) }.getOrNull() }
                            com.wledcontroller.app.show.FseqExport.export(s, layout, container.store.devices.first(), env, out, container.store.outputs.first())
                        }
                    }
                }
            }.onSuccess { exported = it }.onFailure { importError = "Export failed: ${it.message}" }
            exportBusy = false
        }
        exporting = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shows") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { importMenu = true }) { Icon(Icons.Filled.FileOpen, "Import from xLights") }
                        DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                            DropdownMenuItem(
                                text = { Column { Text("xLights sequence (.xsq)"); Text("Effects you can edit", style = MaterialTheme.typography.labelSmall) } },
                                onClick = { importMenu = false; xsqImporter.launch(arrayOf("*/*")) },
                            )
                            DropdownMenuItem(
                                text = { Column { Text("Rendered sequence (.fseq)"); Text("Plays exactly as made", style = MaterialTheme.typography.labelSmall) } },
                                onClick = { importMenu = false; importer.launch(arrayOf("*/*")) },
                            )
                        }
                    }
                    IconButton(onClick = { checking = true }) { Icon(Icons.Filled.FactCheck, "Check for problems") }
                    TextButton(onClick = onSchedule) {
                        Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(6.dp)); Text("Schedule")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New show") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (map.runs.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Map your lights first", fontWeight = FontWeight.SemiBold)
                            Text("Shows animate the props on your house map.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onEditMap) { Text("Open house map") }
                        }
                    }
                }
            }
            importError?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            if (exportBusy) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("Exporting…")
                }
            }
            if (shows.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Movie, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("No shows yet", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "A show is a timeline: put effects on your props and groups, and they play back in sync across every controller. You can also import sequences made in xLights (.fseq) with the import button above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            items(shows, key = { it.id }) { s ->
                Card(onClick = { onOpen(s.id) }, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (s.fseqFile != null) "${formatTime(s.durationMs)} · xLights sequence" + (if (s.audioFile != null) " · with song" else "")
                                else "${formatTime(s.durationMs)} · ${s.tracks.size} tracks · ${s.tracks.sumOf { it.clips.size }} effects",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Show options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                    menu = false
                                    scope.launch { container.sequences.save(s.copy(id = UUID.randomUUID().toString(), name = s.name + " copy")) }
                                })
                                DropdownMenuItem(text = { Text("Export .fseq") }, onClick = {
                                    menu = false; exporting = s
                                    exporter.launch(s.name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "show" } + ".fseq")
                                })
                                if (s.fseqFile == null) DropdownMenuItem(
                                    text = { Column { Text("Export to xLights (.xsq)"); Text("Open and edit it in xLights", style = MaterialTheme.typography.labelSmall) } },
                                    onClick = {
                                        menu = false; exportingXsq = s
                                        xsqExporter.launch(s.name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "show" } + ".xsq")
                                    },
                                )
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = s })
                            }
                        }
                    }
                }
            }
        }
    }

    xsqPending?.let { (x, name) ->
        XsqMappingDialog(x, map, onDismiss = { xsqPending = null }) { targets ->
            xsqPending = null
            scope.launch {
                val (seq, skipped) = com.wledcontroller.app.show.XsqImport.toSequence(x, name, targets)
                container.sequences.save(seq)
                if (skipped > 0) importError = "$skipped xLights effects have no match here yet and were left out."
                onOpen(seq.id)
            }
        }
    }
    exported?.let { r -> ExportResultDialog(r) { exported = null } }

    if (creating) {
        NewShowDialog(onDismiss = { creating = false }) { name, ms ->
            creating = false
            val seq = Sequence(
                id = UUID.randomUUID().toString(),
                name = name,
                durationMs = ms,
                tracks = listOf(Track(UUID.randomUUID().toString(), "Whole house", TrackTarget(TrackTarget.Kind.HOUSE))),
            )
            scope.launch { container.sequences.save(seq); onOpen(seq.id) }
        }
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${s.name}\"?") },
            confirmButton = { TextButton(onClick = { scope.launch { container.sequences.delete(s.id) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NewShowDialog(onDismiss: () -> Unit, onCreate: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("3") }
    var seconds by remember { mutableStateOf("00") }
    val ms = ((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)) * 1000L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New show") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Length (match your song)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Min") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    Text(":")
                    OutlinedTextField(
                        seconds, { seconds = it.filter(Char::isDigit).take(2) }, label = { Text("Sec") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && ms in 5_000..3_600_000, onClick = { onCreate(name.trim(), ms) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ================================================================== editor

@Composable
fun SequenceEditorScreen(sequenceId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val all by container.sequences.all.collectAsStateWithLifecycle()
    val stored = all.firstOrNull { it.id == sequenceId }
    if (stored == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Show") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        }) { p -> Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…") } }
        return
    }
    if (stored.fseqFile != null) FseqShowEditor(stored, onBack) else SequenceEditor(stored, onBack)
}

@Composable
private fun SequenceEditor(initial: Sequence, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val density = LocalDensity.current
    val player = container.player
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val photo = rememberPhoto(map.photoPath)

    var seq by remember(initial.id) { mutableStateOf(initial) }
    // Autosave shortly after each change.
    LaunchedEffect(seq) {
        if (seq === initial) return@LaunchedEffect
        delay(600)
        container.sequences.save(seq)
    }

    val targets = seq.tracks.map { it.target }
    val layout = remember(map, targets) { SequenceRenderer.prepare(map, seq.tracks) }
    DisposableEffect(Unit) {
        player.seek(0)
        onDispose { player.stopAll(); player.loadAudio(null) }
    }
    var envelope by remember(seq.audioFile) { mutableStateOf<AudioEnvelope?>(null) }
    LaunchedEffect(seq.audioFile) {
        val path = seq.audioFile
        withContext(Dispatchers.IO) { player.loadAudio(path) }
        envelope = path?.let { withContext(Dispatchers.Default) { runCatching { AudioAnalyzer.envelope(it) }.getOrNull() } }
    }
    LaunchedEffect(seq) { player.sequence = seq }
    LaunchedEffect(layout) { player.layout = layout }

    val pos by player.position.collectAsStateWithLifecycle()
    val playing by player.playing.collectAsStateWithLifecycle()
    val live by player.live.collectAsStateWithLifecycle()
    // In the simulator the "lights" are virtual, so the show goes to them from the start: pressing
    // Play moves the spotlights and lights the house rather than only previewing here.
    val simActive by LocalAppContainer.current.simulator.active.collectAsStateWithLifecycle()
    LaunchedEffect(simActive) { if (simActive && !player.live.value) player.setLive(true) }
    val view = LocalView.current
    DisposableEffect(live, playing) {
        view.keepScreenOn = live || playing
        onDispose { view.keepScreenOn = false }
    }

    var selectedTrack by remember { mutableStateOf(seq.tracks.firstOrNull()?.id) }
    var selectedClip by remember { mutableStateOf<String?>(null) }
    /** Extra clips selected with a long-press, moved / copied / deleted together. */
    var multi by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pxPerSec by remember { mutableFloatStateOf(with(density) { 36.dp.toPx() }) }
    var scrollX by remember { mutableFloatStateOf(0f) }
    var addingTrack by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    // Tapping along with the lyrics: the words, and when each line started.
    var tapLyrics by remember { mutableStateOf<String?>(null) }
    val tapStarts = remember { mutableStateListOf<Long>() }
    fun finishTapping() {
        val words = tapLyrics ?: return
        if (tapStarts.isNotEmpty()) seq = seq.copy(lyrics = LyricSync.breakdown(words, 0, seq.durationMs, tapStarts.toList()))
        tapLyrics = null; tapStarts.clear(); player.pause()
    }

    // Undo history: a snapshot is recorded once the show has been still for a moment, so a whole
    // drag is one step.
    val history = remember(initial.id) { mutableListOf(initial) }
    var historyIndex by remember(initial.id) { mutableIntStateOf(0) }
    LaunchedEffect(seq) {
        delay(350)
        if (seq != history[historyIndex]) {
            while (history.size > historyIndex + 1) history.removeAt(history.lastIndex)
            history += seq
            if (history.size > 100) history.removeAt(0)
            historyIndex = history.lastIndex
        }
    }
    fun undo() { if (historyIndex > 0) { historyIndex--; seq = history[historyIndex] } }
    fun redo() { if (historyIndex < history.lastIndex) { historyIndex++; seq = history[historyIndex] } }

    fun updateTrack(id: String, f: (Track) -> Track) { seq = seq.copy(tracks = seq.tracks.map { if (it.id == id) f(it) else it }) }
    fun allSelected(): Set<String> = multi + listOfNotNull(selectedClip)
    fun selectedPairs(): List<Pair<String, Clip>> = seq.tracks.flatMap { t -> t.clips.filter { it.id in allSelected() }.map { t.id to it } }
    /** A dragged clip: when it's part of a multi-selection, the others move by the same amount. */
    fun dragClip(trackId: String, clip: Clip) {
        val old = seq.tracks.firstOrNull { it.id == trackId }?.clips?.firstOrNull { it.id == clip.id }
        val group = allSelected()
        if (old != null && clip.id in group && group.size > 1 && clip.durationMs == old.durationMs) {
            val others = selectedPairs().map { it.second }
            val delta = (clip.startMs - old.startMs)
                .coerceAtLeast(-(others.minOfOrNull { it.startMs } ?: 0L))
                .coerceAtMost(seq.durationMs - (others.maxOfOrNull { it.endMs } ?: seq.durationMs))
            seq = seq.copy(tracks = seq.tracks.map { t ->
                t.copy(clips = t.clips.map { c -> if (c.id in group) c.copy(startMs = c.startMs + delta) else c })
            })
        } else {
            updateTrack(trackId) { t -> t.copy(clips = t.clips.map { if (it.id == clip.id) clip else it }) }
        }
    }
    fun deleteSelected() {
        val group = allSelected()
        seq = seq.copy(tracks = seq.tracks.map { t -> t.copy(clips = t.clips.filterNot { it.id in group }) })
        selectedClip = null; multi = emptySet()
    }
    fun paste() {
        val items = ClipClipboard.items
        if (items.isEmpty()) return
        val fallback = selectedTrack ?: seq.tracks.firstOrNull()?.id ?: return
        val base = (pos / 100) * 100
        val pasted = mutableSetOf<String>()
        var tracks = seq.tracks
        items.forEach { (trackId, c) ->
            // Same track when pasting within a show, otherwise onto the selected track.
            val target = if (tracks.any { it.id == trackId }) trackId else fallback
            val start = (base + c.startMs).coerceAtMost(seq.durationMs - 200)
            val nc = c.copy(id = UUID.randomUUID().toString(), startMs = start, durationMs = c.durationMs.coerceAtMost(seq.durationMs - start))
            pasted += nc.id
            tracks = tracks.map { t -> if (t.id == target) t.copy(clips = t.clips + nc) else t }
        }
        seq = seq.copy(tracks = tracks)
        selectedClip = pasted.firstOrNull(); multi = pasted
    }
    fun updateClip(trackId: String, clip: Clip) = updateTrack(trackId) { t -> t.copy(clips = t.clips.map { if (it.id == clip.id) clip else it }) }
    fun addClip() {
        val trackId = selectedTrack ?: seq.tracks.firstOrNull()?.id ?: return
        val start = (pos / 100) * 100
        val dur = minOf(4000L, seq.durationMs - start).coerceAtLeast(500L)
        val clip = Clip(UUID.randomUUID().toString(), start.coerceAtMost(seq.durationMs - 500), dur, effect = SeqEffect.COLOR_WAVE)
        updateTrack(trackId) { it.copy(clips = it.clips + clip) }
        selectedTrack = trackId; selectedClip = clip.id
    }

    val selected = seq.tracks.firstOrNull { it.id == selectedTrack }?.clips?.firstOrNull { it.id == selectedClip }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seq.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { undo() }, enabled = historyIndex > 0) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                    IconButton(onClick = { redo() }, enabled = historyIndex < history.lastIndex) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                    IconButton(onClick = { showMusic = true }) {
                        Icon(Icons.Filled.MusicNote, "Music", tint = if (seq.audioFile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Live", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = live, onCheckedChange = { player.setLive(it) })
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val preview: @Composable (Modifier) -> Unit = { m ->
                Box(m, contentAlignment = Alignment.Center) {
                    // Colours, and where moving heads point, at this moment of the show.
                    val motion = remember(layout) { FloatArray(layout.leds.size * 2) }
                    val colors = remember(pos, seq, layout) { SequenceRenderer.render(seq, layout, pos, audio = envelope, motion = motion) }
                    val pixel = LocalPixelStyle.current
                    val gaps = remember(layout, map.aspect) { neighbourGaps(layout.leds, map.aspect) }
                    val fixtures by LocalAppContainer.current.store.fixtures.collectAsStateWithLifecycle(emptyList())
                    val fixtureIds = remember(fixtures) { fixtures.map { it.id }.toSet() }
                    var view3d by remember { mutableStateOf(false) }
                    if (view3d) com.wledcontroller.app.ui.components.House3DView(map, layout.leds, { colors.getOrElse(it) { 0 } }, Modifier.heightIn(max = if (landscape) 260.dp else 200.dp))
                    else HouseCanvas(map, photo, Modifier.heightIn(max = if (landscape) 260.dp else 200.dp)) {
                        // DMX fixtures as spotlights with their beams, behind the other lights.
                        layout.leds.forEachIndexed { i, l ->
                            if (l.deviceId in fixtureIds) {
                                drawSpotlight(Offset(l.x * size.width, l.y * size.height), colors[i], motion.getOrElse(i * 2) { 0.5f }, motion.getOrElse(i * 2 + 1) { 0.5f }, 0.8f)
                            }
                        }
                        layout.leds.forEachIndexed { i, l ->
                            if (!l.hidden && l.deviceId !in fixtureIds) drawPixel(colors[i], Offset(l.x * size.width, l.y * size.height), pixelRadius(pixel, gaps[i]))
                        }
                    }
                    FilterChip(selected = view3d, onClick = { view3d = !view3d }, label = { Text("3D") }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
            }
            val transport: @Composable () -> Unit = {
                tapLyrics?.let { words ->
                    val lines = words.lines().map { it.trim() }.filter { it.isNotEmpty() && LyricSync.words(it).isNotEmpty() }
                    val next = lines.getOrNull(tapStarts.size)
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (next != null) "Tap when this line starts:" else "All lines tapped", style = MaterialTheme.typography.labelSmall)
                                Text(next ?: "Press Done.", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (next != null) Button(onClick = { tapStarts += pos; if (!playing) player.play() }) { Text("Now") }
                            TextButton(onClick = { finishTapping() }) { Text("Done") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { player.seek(0) }) { Icon(Icons.Filled.SkipPrevious, "To start") }
                    FilledIconButton(onClick = { if (playing) player.pause() else player.play() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play")
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${formatTime(pos, tenths = true)} / ${formatTime(seq.durationMs)}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    if (playing) {
                        FilledTonalButton(
                            onClick = { seq = seq.copy(marks = (seq.marks + pos).sorted()) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) { Text("Tap beat") }
                    }
                    IconButton(onClick = { pxPerSec = (pxPerSec / 1.5f).coerceAtLeast(4f) }) { Icon(Icons.Filled.ZoomOut, "Zoom out") }
                    IconButton(onClick = { pxPerSec = (pxPerSec * 1.5f).coerceAtMost(800f) }) { Icon(Icons.Filled.ZoomIn, "Zoom in") }
                }
            }
            val timeline: @Composable (Modifier) -> Unit = { m ->
                Timeline(
                    seq = seq, map = map, envelope = envelope, position = pos, pxPerSec = pxPerSec, scrollX = scrollX,
                    selectedTrack = selectedTrack, selectedClip = selectedClip, multiSelected = multi,
                    onZoom = { pxPerSec = it }, onScroll = { scrollX = it },
                    onSeek = { player.seek(it) },
                    onSelectTrack = { selectedTrack = it; selectedClip = null; multi = emptySet() },
                    onSelectClip = { t, c ->
                        selectedTrack = t
                        // Tapping a clip outside the group starts a fresh selection.
                        if (c !in allSelected()) multi = emptySet()
                        selectedClip = c
                    },
                    onLongPressClip = { t, c ->
                        selectedTrack = t
                        val group = allSelected()
                        multi = if (c in group) group - c else group + c
                        selectedClip = if (c in multi) c else multi.firstOrNull()
                    },
                    onClipChange = { t, c -> dragClip(t, c) },
                    onTrackChange = { t -> updateTrack(t.id) { t } },
                    onTrackDelete = { t -> seq = seq.copy(tracks = seq.tracks.filterNot { it.id == t.id }); if (selectedTrack == t.id) { selectedTrack = null; selectedClip = null } },
                    onTrackMove = { t, delta ->
                        val list = seq.tracks.toMutableList(); val i = list.indexOfFirst { it.id == t.id }; val j = (i + delta).coerceIn(0, list.lastIndex)
                        if (i >= 0 && i != j) { list.add(j, list.removeAt(i)); seq = seq.copy(tracks = list) }
                    },
                    onTrackAddLayer = { t ->
                        // A new empty track on the same lights, just above, mixing in normally.
                        val list = seq.tracks.toMutableList(); val i = list.indexOfFirst { it.id == t.id }.coerceAtLeast(0)
                        val layers = seq.tracks.count { it.target == t.target }
                        val layer = Track(UUID.randomUUID().toString(), "${t.name.substringBefore(" · layer")} · layer ${layers + 1}", t.target, blend = BlendMode.NORMAL)
                        list.add(i, layer)
                        seq = seq.copy(tracks = list); selectedTrack = layer.id; selectedClip = null
                    },
                    onAddTrack = { addingTrack = true },
                    modifier = m,
                )
            }
            val inspector: @Composable (Modifier) -> Unit = { m ->
                val trackId = selectedTrack
                val group = allSelected()
                if (group.size > 1) {
                    Column(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${group.size} effects selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Drag any of them to move them all. Long-press an effect to add or remove it from the selection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { ClipClipboard.copy(selectedPairs()) }) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy") }
                            OutlinedButton(onClick = { ClipClipboard.copy(selectedPairs()); deleteSelected() }) { Text("Cut") }
                            OutlinedButton(onClick = { deleteSelected() }) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete") }
                            TextButton(onClick = { multi = emptySet(); selectedClip = null }) { Text("Clear selection") }
                        }
                    }
                } else if (selected != null && trackId != null) {
                    ClipInspector(
                        clip = selected,
                        maxMs = seq.durationMs,
                        onChange = { updateClip(trackId, it) },
                        onDelete = { updateTrack(trackId) { t -> t.copy(clips = t.clips.filterNot { it.id == selected.id }) }; selectedClip = null },
                        onDuplicate = {
                            val copy = selected.copy(id = UUID.randomUUID().toString(), startMs = selected.endMs.coerceAtMost(seq.durationMs - 200))
                            updateTrack(trackId) { t -> t.copy(clips = t.clips + copy) }
                            selectedClip = copy.id
                        },
                        onClose = { selectedClip = null },
                        onCopy = { ClipClipboard.copy(listOf(trackId to selected)) },
                        hasSong = seq.audioFile != null,
                        modifier = m,
                        timingTracks = seq.timingTracks,
                    )
                } else {
                    Column(m.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (seq.tracks.isEmpty()) "Add a track to start." else
                                "Select a track, move the playhead, then add an effect. Drag effects to move them, drag their right edge to resize, pinch the timeline to zoom.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { addClip() }, enabled = seq.tracks.isNotEmpty()) {
                                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add effect at playhead")
                            }
                            OutlinedButton(onClick = { addingTrack = true }) { Text("Add track") }
                        }
                        if (ClipClipboard.items.isNotEmpty()) {
                            OutlinedButton(onClick = { paste() }, enabled = seq.tracks.isNotEmpty()) {
                                Icon(Icons.Filled.ContentPaste, null); Spacer(Modifier.width(6.dp))
                                Text("Paste ${ClipClipboard.items.size} effect" + (if (ClipClipboard.items.size > 1) "s" else "") + " at playhead")
                            }
                        }
                    }
                }
            }

            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(0.42f).fillMaxHeight()) {
                        preview(Modifier.fillMaxWidth().padding(8.dp))
                        inspector(Modifier.weight(1f).fillMaxWidth())
                    }
                    Column(Modifier.weight(0.58f).fillMaxHeight()) {
                        transport()
                        timeline(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
                    transport()
                    timeline(Modifier.weight(1f).fillMaxWidth())
                    HorizontalDivider()
                    inspector(Modifier.fillMaxWidth().heightIn(max = 330.dp))
                }
            }
        }
    }

    if (showMusic) {
        MusicSheet(
            seq = seq,
            envelope = envelope,
            selectedTrack = seq.tracks.firstOrNull { it.id == selectedTrack },
            onDismiss = { showMusic = false },
            onChange = { seq = it; if (it.audioFile != initial.audioFile) player.seek(0) },
            onTapLyrics = { words -> tapLyrics = words; tapStarts.clear(); player.seek(0); player.play() },
            position = pos,
        )
    }
    if (addingTrack) {
        AddTrackDialog(map, onDismiss = { addingTrack = false }) { name, target ->
            val t = Track(UUID.randomUUID().toString(), name, target)
            seq = seq.copy(tracks = seq.tracks + t)
            selectedTrack = t.id; selectedClip = null
            addingTrack = false
        }
    }
}

@Composable
private fun AddTrackDialog(map: HouseMap, onDismiss: () -> Unit, onAdd: (String, TrackTarget) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add track") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onAdd("Whole house", TrackTarget(TrackTarget.Kind.HOUSE)) }) { Text("Whole house") }
                if (map.groups.isNotEmpty()) {
                    Text("Groups", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    map.groups.forEach { g -> TextButton(onClick = { onAdd(g.name, TrackTarget(TrackTarget.Kind.GROUP, g.id)) }) { Text(g.name) } }
                }
                if (map.runs.isNotEmpty()) {
                    Text("Props", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    map.runs.forEach { r ->
                        TextButton(onClick = { onAdd(r.name, TrackTarget(TrackTarget.Kind.PROP, r.id)) }) { Text("${r.name} · ${r.prop.label}") }
                        // Parts of the prop: a tree's strings, a star's points, your own ranges.
                        val subs = r.allSubmodels()
                        if (subs.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                subs.forEach { sm ->
                                    AssistChip(onClick = { onAdd("${r.name} · ${sm.name}", TrackTarget.submodel(r.id, sm.name)) }, label = { Text(sm.name) })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ================================================================== timeline

private val TrackHeight = 56.dp
private val HeaderWidth = 116.dp
private val RulerHeight = 28.dp

@Composable
private fun Timeline(
    seq: Sequence,
    map: HouseMap,
    envelope: AudioEnvelope?,
    position: Long,
    pxPerSec: Float,
    scrollX: Float,
    selectedTrack: String?,
    selectedClip: String?,
    multiSelected: Set<String>,
    onZoom: (Float) -> Unit,
    onScroll: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onSelectTrack: (String) -> Unit,
    onSelectClip: (String, String) -> Unit,
    onLongPressClip: (String, String) -> Unit,
    onClipChange: (String, Clip) -> Unit,
    onTrackChange: (Track) -> Unit,
    onTrackDelete: (Track) -> Unit,
    onTrackMove: (Track, Int) -> Unit,
    onTrackAddLayer: (Track) -> Unit,
    onAddTrack: () -> Unit,
    modifier: Modifier,
) {
    val playhead = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val laneBg = MaterialTheme.colorScheme.surfaceContainerLow
    val laneSel = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    val state by rememberUpdatedState(TimelineState(seq, position, pxPerSec, scrollX))
    val zoom by rememberUpdatedState(onZoom)
    val scroll by rememberUpdatedState(onScroll)
    val seek by rememberUpdatedState(onSeek)

    /** Pinch zoom (around the fingers) and one-finger pan, shared by the ruler and lanes. */
    fun maxScroll(width: Float, s: TimelineState) = (s.seq.durationMs / 1000f * s.pps - width + 40f).coerceAtLeast(0f)

    Column(modifier) {
        Row(Modifier.height(RulerHeight)) {
            Box(Modifier.width(HeaderWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Text("  Tracks", style = MaterialTheme.typography.labelMedium, color = textColor)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        // Tap or drag on the ruler scrubs the playhead.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun at(x: Float) = (((x + state.scrollX) / state.pps) * 1000).roundToLong().coerceIn(0, state.seq.durationMs)
                            seek(at(down.position.x))
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                seek(at(ch.position.x)); ch.consume()
                            }
                        }
                    }
            ) {
                val step = tickStep(pxPerSec)
                val first = (scrollX / pxPerSec / step).toInt()
                var k = first
                while (true) {
                    val sec = k * step
                    val x = sec * pxPerSec - scrollX
                    if (x > size.width) break
                    if (sec * 1000 <= seq.durationMs) {
                        drawLine(gridColor, Offset(x, size.height * 0.55f), Offset(x, size.height), 1.dp.toPx())
                        val label = measurer.measure(formatTime((sec * 1000).toLong()), TextStyle(fontSize = 10.sp, color = textColor))
                        drawText(label, topLeft = Offset(x + 3.dp.toPx(), 2.dp.toPx()))
                    }
                    k++
                }
                val px = position / 1000f * pxPerSec - scrollX
                drawLine(playhead, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
            }
        }
        if (envelope != null || seq.marks.isNotEmpty()) {
            val wave = MaterialTheme.colorScheme.tertiary
            Row(Modifier.height(34.dp)) {
                Box(Modifier.width(HeaderWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    Text(
                        "  " + (seq.audioTitle ?: "Beats"),
                        style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Canvas(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(laneBg)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                seek((((down.position.x + state.scrollX) / state.pps) * 1000).roundToLong().coerceIn(0, state.seq.durationMs))
                            }
                        }
                ) {
                    val env = envelope
                    if (env != null) {
                        val step = 2.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            val ms = ((x + scrollX) / pxPerSec * 1000).toLong()
                            if (ms > seq.durationMs) break
                            val a = env.at(ms) * size.height * 0.9f
                            drawLine(wave.copy(alpha = 0.7f), Offset(x, (size.height - a) / 2), Offset(x, (size.height + a) / 2), step * 0.6f)
                            x += step
                        }
                    }
                    seq.marks.forEach { m ->
                        val mx = m / 1000f * pxPerSec - scrollX
                        if (mx in 0f..size.width) drawLine(Color.White.copy(alpha = 0.8f), Offset(mx, 0f), Offset(mx, size.height * 0.3f), 1.5.dp.toPx())
                    }
                    val px = position / 1000f * pxPerSec - scrollX
                    drawLine(playhead, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
                }
            }
        }
        seq.lyrics?.takeIf { !it.isEmpty }?.let { lyrics ->
            // The words (and, zoomed in, their mouth shapes) under the song.
            val wordColor = MaterialTheme.colorScheme.secondary
            Row(Modifier.height(30.dp)) {
                Box(Modifier.width(HeaderWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    Text("  Lyrics", style = MaterialTheme.typography.labelSmall, color = textColor)
                }
                Canvas(Modifier.weight(1f).fillMaxHeight().background(laneBg)) {
                    clipRect {
                        for (w in lyrics.words) {
                            val x0 = w.startMs / 1000f * pxPerSec - scrollX; val x1 = w.endMs / 1000f * pxPerSec - scrollX
                            if (x1 < 0 || x0 > size.width) continue
                            drawRoundRect(wordColor.copy(alpha = 0.25f), Offset(x0, 2f), Size((x1 - x0).coerceAtLeast(1f), size.height * 0.55f), CornerRadius(4f))
                            if (x1 - x0 > 18f) {
                                val label = measurer.measure(w.label, TextStyle(fontSize = 10.sp, color = textColor), maxLines = 1,
                                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = (x1 - x0).toInt().coerceAtLeast(1)))
                                drawText(label, topLeft = Offset(x0 + 2f, 2f))
                            }
                        }
                        if (pxPerSec > 120f) for (p in lyrics.phonemes) {
                            val x0 = p.startMs / 1000f * pxPerSec - scrollX
                            if (x0 < -40f || x0 > size.width) continue
                            val label = measurer.measure(p.label, TextStyle(fontSize = 8.sp, color = textColor.copy(alpha = 0.8f)))
                            drawText(label, topLeft = Offset(x0, size.height * 0.6f))
                        }
                        val px = position / 1000f * pxPerSec - scrollX
                        drawLine(playhead, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
                    }
                }
            }
        }
        seq.timingTracks.forEachIndexed { ti, tt ->
            // Each named timing track: its labelled stretches, tap one to jump there.
            val tint = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)[ti % 3]
            Row(Modifier.height(26.dp)) {
                Box(Modifier.width(HeaderWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    Text("  " + tt.name, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Canvas(
                    Modifier.weight(1f).fillMaxHeight().background(laneBg).pointerInput(tt.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            seek((((down.position.x + state.scrollX) / state.pps) * 1000).roundToLong().coerceIn(0, state.seq.durationMs))
                        }
                    }
                ) {
                    clipRect {
                        tt.marks.forEachIndexed { i, m ->
                            val x0 = m.startMs / 1000f * pxPerSec - scrollX; val x1 = m.endMs / 1000f * pxPerSec - scrollX
                            if (x1 < 0 || x0 > size.width) return@forEachIndexed
                            drawRect(tint.copy(alpha = if (i % 2 == 0) 0.28f else 0.18f), Offset(x0, 2f), Size((x1 - x0).coerceAtLeast(1f), size.height - 4f))
                            drawLine(tint, Offset(x0, 0f), Offset(x0, size.height), 1.5.dp.toPx())
                            if (x1 - x0 > 16f && m.label.isNotBlank()) {
                                val label = measurer.measure(m.label, TextStyle(fontSize = 10.sp, color = textColor), maxLines = 1,
                                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = (x1 - x0 - 4f).toInt().coerceAtLeast(1)))
                                drawText(label, topLeft = Offset(x0 + 3f, 3f))
                            }
                        }
                        val px = position / 1000f * pxPerSec - scrollX
                        drawLine(playhead, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            seq.tracks.forEach { track ->
                val isSel = track.id == selectedTrack
                Row(Modifier.height(TrackHeight)) {
                    TrackHeader(track, map, isSel, onSelect = { onSelectTrack(track.id) }, onChange = onTrackChange, onDelete = { onTrackDelete(track) }, onMove = { onTrackMove(track, it) }, onAddLayer = { onTrackAddLayer(track) })
                    val trackState by rememberUpdatedState(track)
                    val select by rememberUpdatedState(onSelectClip)
                    val longPress by rememberUpdatedState(onLongPressClip)
                    val selectTrack by rememberUpdatedState(onSelectTrack)
                    val change by rememberUpdatedState(onClipChange)
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isSel) laneSel else laneBg)
                            .pointerInput(track.id) {
                                val edge = 16.dp.toPx()
                                val snapPx = 10.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val s0 = state
                                    val t = trackState
                                    val downX = down.position.x
                                    fun xOf(ms: Long) = ms / 1000f * s0.pps - s0.scrollX
                                    val hit = t.clips.lastOrNull { downX >= xOf(it.startMs) - 2 && downX <= xOf(it.endMs) + edge / 2 }
                                    val resize = hit != null && downX >= xOf(hit.endMs) - edge
                                    var dragging = false
                                    var pinched = false
                                    var lastTime = down.uptimeMillis
                                    val startScroll = s0.scrollX
                                    fun snap(ms: Long, exclude: String): Long {
                                        val candidates = buildList {
                                            add(s0.position)
                                            addAll(s0.seq.marks)
                                            s0.seq.timingTracks.forEach { tt -> tt.marks.forEach { add(it.startMs) } }
                                            s0.seq.tracks.forEach { tr -> tr.clips.forEach { c -> if (c.id != exclude) { add(c.startMs); add(c.endMs) } } }
                                        }
                                        val near = candidates.minByOrNull { abs(it - ms) }
                                        return if (near != null && abs(near - ms) / 1000f * s0.pps < snapPx) near else (ms / 100) * 100
                                    }
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        lastTime = ev.changes.maxOf { it.uptimeMillis }
                                        val pressed = ev.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size >= 2) {
                                            pinched = true
                                            val z = ev.calculateZoom()
                                            val c = ev.calculateCentroid().x
                                            val cur = state
                                            val newPps = (cur.pps * z).coerceIn(4f, 800f)
                                            val timeAtC = (c + cur.scrollX) / cur.pps
                                            zoom(newPps)
                                            scroll((timeAtC * newPps - c).coerceIn(0f, maxScroll(size.width.toFloat(), cur.copy(pps = newPps))))
                                            ev.changes.forEach { it.consume() }
                                            continue
                                        }
                                        if (pinched) continue
                                        val ch = pressed.first()
                                        val dx = ch.position.x - downX
                                        if (!dragging && abs(dx) > viewConfiguration.touchSlop) dragging = true
                                        if (!dragging) continue
                                        ch.consume()
                                        val dMs = (dx / s0.pps * 1000).roundToLong()
                                        when {
                                            hit != null && resize -> {
                                                val end = snap(hit.endMs + dMs, hit.id).coerceIn(hit.startMs + 200, s0.seq.durationMs)
                                                change(t.id, hit.copy(durationMs = end - hit.startMs))
                                            }
                                            hit != null -> {
                                                val start = snap(hit.startMs + dMs, hit.id).coerceIn(0, s0.seq.durationMs - hit.durationMs)
                                                change(t.id, hit.copy(startMs = start))
                                            }
                                            else -> scroll((startScroll - dx).coerceIn(0f, maxScroll(size.width.toFloat(), s0)))
                                        }
                                    }
                                    if (!dragging && !pinched) {
                                        if (hit != null) {
                                            if (lastTime - down.uptimeMillis >= 450) longPress(t.id, hit.id) else select(t.id, hit.id)
                                        } else {
                                            selectTrack(t.id)
                                            seek(((downX + s0.scrollX) / s0.pps * 1000).roundToLong().coerceIn(0, s0.seq.durationMs))
                                        }
                                    }
                                }
                            }
                    ) {
                        clipRect {
                            val endX = seq.durationMs / 1000f * pxPerSec - scrollX
                            if (endX < size.width) drawRect(Color.Black.copy(alpha = 0.35f), Offset(endX, 0f), Size(size.width - endX, size.height))
                            val pad = 6.dp.toPx()
                            if (seq.marks.size < 2 || (seq.marks[1] - seq.marks[0]) / 1000f * pxPerSec > 4f) {
                                seq.marks.forEach { m ->
                                    val mx = m / 1000f * pxPerSec - scrollX
                                    if (mx in 0f..size.width) drawLine(gridColor.copy(alpha = 0.5f), Offset(mx, 0f), Offset(mx, size.height), 1f)
                                }
                            }
                            track.clips.forEach { c ->
                                val x0 = c.startMs / 1000f * pxPerSec - scrollX
                                val x1 = c.endMs / 1000f * pxPerSec - scrollX
                                if (x1 < 0 || x0 > size.width) return@forEach
                                val w = (x1 - x0).coerceAtLeast(2f)
                                val cols = c.colors.ifEmpty { listOf(0xFFFFFFFF.toInt()) }.map { it.toComposeColor().copy(alpha = if (track.muted) 0.35f else 0.85f) }
                                val brush = if (cols.size > 1) Brush.horizontalGradient(cols, startX = x0, endX = x1) else Brush.horizontalGradient(listOf(cols[0], cols[0]))
                                drawRoundRect(brush, Offset(x0, pad), Size(w, size.height - pad * 2), CornerRadius(8.dp.toPx()))
                                val isSelClip = c.id == selectedClip
                                if (c.id in multiSelected && !isSelClip) {
                                    drawRoundRect(
                                        Color.White, Offset(x0, pad), Size(w, size.height - pad * 2), CornerRadius(8.dp.toPx()),
                                        style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
                                    )
                                }
                                if (isSelClip) {
                                    drawRoundRect(Color.White, Offset(x0, pad), Size(w, size.height - pad * 2), CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
                                    drawRoundRect(Color.White, Offset(x1 - 5.dp.toPx(), size.height / 2 - 10.dp.toPx()), Size(3.dp.toPx(), 20.dp.toPx()), CornerRadius(2.dp.toPx()))
                                }
                                if (w > 40.dp.toPx()) {
                                    val label = measurer.measure(
                                        c.effect.label,
                                        TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
                                        maxLines = 1, softWrap = false,
                                    )
                                    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(x0 + 4.dp.toPx(), pad + 4.dp.toPx()), Size(label.size.width + 8.dp.toPx(), label.size.height.toFloat()), CornerRadius(4.dp.toPx()))
                                    drawText(label, topLeft = Offset(x0 + 8.dp.toPx(), pad + 4.dp.toPx()))
                                }
                            }
                            val px = position / 1000f * pxPerSec - scrollX
                            drawLine(playhead, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
                        }
                    }
                }
                HorizontalDivider(color = gridColor.copy(alpha = 0.4f))
            }
            TextButton(onClick = onAddTrack, modifier = Modifier.padding(start = 8.dp)) { Icon(Icons.Filled.Add, null); Text("Add track") }
        }
    }
}

private data class TimelineState(val seq: Sequence, val position: Long, val pps: Float, val scrollX: Float)

/** Seconds between ruler labels for a zoom level, so labels are ~70 px apart. */
private fun tickStep(pps: Float): Float {
    val steps = floatArrayOf(0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f)
    return steps.firstOrNull { it * pps >= 70f } ?: 300f
}

@Composable
private fun TrackHeader(
    track: Track,
    map: HouseMap,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (Track) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onAddLayer: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var choosingBlend by remember { mutableStateOf(false) }
    var choosingStyle by remember { mutableStateOf(false) }
    val kind = when (track.target.kind) {
        TrackTarget.Kind.HOUSE -> "Whole house"
        TrackTarget.Kind.GROUP -> if (map.groups.any { it.id == track.target.refId }) "Group" else "Missing group"
        TrackTarget.Kind.PROP -> map.runs.firstOrNull { it.id == track.target.refId }?.prop?.label ?: "Missing prop"
        TrackTarget.Kind.SUBMODEL -> map.runs.firstOrNull { it.id == track.target.refId.substringBefore('/') }
            ?.takeIf { r -> r.allSubmodels().any { it.name == track.target.refId.substringAfter('/') } }?.let { "Part of ${it.name}" } ?: "Missing part"
    }
    Surface(
        onClick = onSelect,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(HeaderWidth).fillMaxHeight(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(track.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    kind + (if (track.blend != BlendMode.MAX) " · ${track.blend.label}" else "") + (if (track.style != RenderStyle.AS_ONE) " · ${track.style.label}" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (track.blend != BlendMode.MAX) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (track.muted) Icon(Icons.Filled.VolumeOff, "Muted", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "Track options", Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (track.muted) "Unmute" else "Mute") }, onClick = { menu = false; onChange(track.copy(muted = !track.muted)) })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Blend: ${track.blend.label}") }, onClick = { menu = false; choosingBlend = true })
                    if (track.target.kind == com.wledcontroller.app.show.TrackTarget.Kind.GROUP || track.target.kind == com.wledcontroller.app.show.TrackTarget.Kind.HOUSE) {
                        DropdownMenuItem(text = { Text("Render: ${track.style.label}") }, onClick = { menu = false; choosingStyle = true })
                    }
                    DropdownMenuItem(text = { Text("Add layer above") }, onClick = { menu = false; onAddLayer() })
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Delete track") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
    if (renaming) {
        TextInputDialog("Rename track", track.name, "Name", onDismiss = { renaming = false }) { onChange(track.copy(name = it)) }
    }
    if (choosingBlend) {
        AlertDialog(
            onDismissRequest = { choosingBlend = false },
            title = { Text("How ${track.name} mixes") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Tracks higher in the list sit on top. This sets how this track mixes with the ones below it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BlendMode.entries.forEach { b ->
                        Surface(
                            onClick = { onChange(track.copy(blend = b)); choosingBlend = false },
                            shape = RoundedCornerShape(12.dp),
                            color = if (b == track.blend) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(b.label, fontWeight = FontWeight.SemiBold)
                                Text(b.help, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingBlend = false }) { Text("Close") } },
        )
    }
    if (choosingStyle) {
        AlertDialog(
            onDismissRequest = { choosingStyle = false },
            title = { Text("How ${track.name} spreads its effects") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RenderStyle.entries.forEach { r ->
                        Surface(
                            onClick = { onChange(track.copy(style = r)); choosingStyle = false },
                            shape = RoundedCornerShape(12.dp),
                            color = if (r == track.style) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(r.label, fontWeight = FontWeight.SemiBold)
                                Text(r.help, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingStyle = false }) { Text("Close") } },
        )
    }
}

// ================================================================== clip inspector

@Composable
private fun ClipInspector(
    clip: Clip,
    maxMs: Long,
    onChange: (Clip) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    hasSong: Boolean,
    modifier: Modifier,
    timingTracks: List<com.wledcontroller.app.show.TimingTrack> = emptyList(),
) {
    var editingSlot by remember { mutableIntStateOf(-1) }
    var editingCurve by remember { mutableStateOf<String?>(null) }
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoProgress by remember { mutableStateOf<Float?>(null) }
    var sketching by remember { mutableStateOf(false) }
    if (sketching) SketchDialog(clip.sketch, onDismiss = { sketching = false }) { onChange(clip.copy(sketch = it)); sketching = false }
    var videoError by remember { mutableStateOf<String?>(null) }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) container.scope.launch {
            videoProgress = 0f; videoError = null
            runCatching { container.sequences.importVideo(context, uri) { videoProgress = it } }
                .onSuccess { onChange(clip.copy(videoFile = it)) }
                .onFailure { videoError = it.message ?: "Couldn't read that video." }
            videoProgress = null
        }
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { container.sequences.importPicture(context, uri) }.onSuccess { onChange(clip.copy(imageFile = it)) }
        }
    }
    val themes = remember { HolidayThemes.forToday() }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(clip.effect.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatTime(clip.startMs, true)} – ${formatTime(clip.endMs, true)} (${"%.1f".format(clip.durationMs / 1000f)} s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Copy") }
            IconButton(onClick = onDuplicate) { Icon(Icons.Filled.AddToPhotos, "Duplicate after") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete effect") }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-1000L, -100L, 100L, 1000L).forEach { d ->
                OutlinedButton(
                    onClick = { onChange(clip.copy(durationMs = (clip.durationMs + d).coerceIn(200, maxMs - clip.startMs))) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) { Text((if (d > 0) "+" else "−") + "%.1fs".format(abs(d) / 1000f), style = MaterialTheme.typography.labelSmall) }
            }
        }

        SectionTitle("Effect")
        // Only the effects that suit a kind of light, when one is picked (the clip's own stays shown).
        var lightType by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<com.wledcontroller.app.data.LightType?>(null) }
        com.wledcontroller.app.ui.components.LightTypeFilter(lightType, { lightType = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SeqEffect.entries
                .filter { e -> lightType == null || e == clip.effect || lightType in com.wledcontroller.app.data.EffectFit.show(e) }
                .forEach { e -> FilterChip(selected = clip.effect == e, onClick = { onChange(clip.copy(effect = e)) }, label = { Text(e.label) }) }
        }
        when {
            clip.effect == SeqEffect.TEXT -> OutlinedTextField(
                clip.text ?: "", { onChange(clip.copy(text = it.take(80))) },
                label = { Text("Text") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Scrolls across the lights at the speed below (speed 0 holds it still). Size sets how tall it is.") },
            )
            clip.effect == SeqEffect.PICTURE -> Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (clip.imageFile == null) "Choose picture" else "Change picture")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Fits the lights; speed above 0 scrolls it across. Best on matrices and big groups.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            clip.effect == SeqEffect.STATE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Lights the parts of the prop named by a timing track's labels as the song goes: label a stretch \"eyes\" or \"mouth, nose\" (or \"all\") to light those parts. Make the parts in the house map: edit the prop › Parts › Add part.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (timingTracks.isEmpty()) Text("Add a timing track first (♪ › Timing tracks).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    timingTracks.forEach { tt -> FilterChip(selected = clip.timingTrackId == tt.id, onClick = { onChange(clip.copy(timingTrackId = tt.id)) }, label = { Text(tt.name) }) }
                }
            }
            clip.effect == SeqEffect.SHADER -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var text by remember(clip.id) { mutableStateOf(clip.formula ?: com.wledcontroller.app.show.Formula.examples.first().second) }
                val problem = remember(text) { com.wledcontroller.app.show.Formula.check(text) }
                OutlinedTextField(
                    text, { text = it; if (com.wledcontroller.app.show.Formula.check(it) == null) onChange(clip.copy(formula = it)) },
                    label = { Text("Formula") }, isError = problem != null, singleLine = false, maxLines = 3,
                    supportingText = { Text(problem ?: "x, y across and up · t seconds · r, a from the middle · i along · result picks the colour") },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.wledcontroller.app.show.Formula.examples.forEach { (name, f) ->
                        SuggestionChip(onClick = { text = f; onChange(clip.copy(formula = f)) }, label = { Text(name) })
                    }
                }
            }
            clip.effect == SeqEffect.SKETCH -> Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { sketching = true }) { Text(if (clip.sketch.size < 2) "Draw the path" else "Redraw") }
                Spacer(Modifier.width(8.dp))
                Text("The path draws itself along the lights over the effect. Size sets how thick.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            clip.effect == SeqEffect.VIDEO -> Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = videoProgress == null,
                        onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    ) { Text(if (clip.videoFile == null) "Choose video" else "Change video") }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Fits the lights. Speed in the middle plays it at normal speed. Best on matrices and mega trees.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                videoProgress?.let { p ->
                    androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Text("Preparing the video for the lights… ${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
                videoError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
            clip.effect.musical && !hasSong -> Text(
                "This effect follows the song — add one with the ♪ button.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }

        SectionTitle("Colours")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 3) {
                ColorSwatch(clip.colors.getOrElse(i) { 0xFF000000.toInt() }.toComposeColor(), onClick = { editingSlot = i }, size = 40.dp)
            }
            Text("Tap to change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Colour curves: the colours turn into others over the effect.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Change colours over the effect", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(checked = clip.colorsEnd != null, onCheckedChange = { on ->
                onChange(clip.copy(colorsEnd = if (on) clip.colors.map { com.wledcontroller.app.show.SequenceRenderer.hueShift(it and 0xFFFFFF, 0.5f) or 0xFF000000.toInt() } else null))
            })
        }
        clip.colorsEnd?.let { end ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("to", style = MaterialTheme.typography.labelMedium)
                for (i in 0 until 3) {
                    ColorSwatch(end.getOrElse(i) { 0xFF000000.toInt() }.toComposeColor(), onClick = { editingSlot = 3 + i }, size = 40.dp)
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(themes, key = { it.id }) { t ->
                Surface(onClick = { onChange(clip.copy(colors = t.colors)) }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(8.dp).width(84.dp)) {
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(themeBrush(t.colors, false)))
                        Text(t.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        SectionTitle("Direction")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Direction.entries.forEach { d -> FilterChip(selected = clip.direction == d, onClick = { onChange(clip.copy(direction = d)) }, label = { Text(d.label) }) }
        }

        SectionTitle("Adjust")
        CurvedSlider("Speed", clip.speed, clip.speedCurveOrRamp, { onChange(clip.copy(speed = it)) }) { editingCurve = "speed" }
        CurvedSlider("Size", clip.size, clip.sizeCurve, { onChange(clip.copy(size = it)) }) { editingCurve = "size" }
        CurvedSlider("Brightness", clip.intensity, clip.intensityCurveOrRamp, { onChange(clip.copy(intensity = it)) }) { editingCurve = "intensity" }
        val maxFade = (clip.durationMs / 2).coerceAtLeast(1)
        GradientSlider(
            clip.fadeInMs.toFloat() / maxFade, { onChange(clip.copy(fadeInMs = (it * maxFade).toLong())) },
            label = "Fade in", valueText = "%.1f s".format(clip.fadeInMs / 1000f), height = 30.dp,
        )
        GradientSlider(
            clip.fadeOutMs.toFloat() / maxFade, { onChange(clip.copy(fadeOutMs = (it * maxFade).toLong())) },
            label = "Fade out", valueText = "%.1f s".format(clip.fadeOutMs / 1000f), height = 30.dp,
        )
        TransitionRow("Comes in with", clip.inTransition, enabled = clip.fadeInMs > 0) { onChange(clip.copy(inTransition = it)) }
        TransitionRow("Goes out with", clip.outTransition, enabled = clip.fadeOutMs > 0) { onChange(clip.copy(outTransition = it)) }

        SectionTitle("Moving heads")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aim DMX moving heads under this effect", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = clip.pan != null || clip.tilt != null,
                onCheckedChange = { on -> onChange(if (on) clip.copy(pan = 0.5f, tilt = 0.5f) else clip.copy(pan = null, tilt = null, panCurve = null, tiltCurve = null)) },
            )
        }
        if (clip.pan != null || clip.tilt != null) {
            CurvedSlider("Pan (left ↔ right)", clip.pan ?: 0.5f, clip.panCurve, { onChange(clip.copy(pan = it)) }) { editingCurve = "pan" }
            CurvedSlider("Tilt (down ↔ up)", clip.tilt ?: 0.5f, clip.tiltCurve, { onChange(clip.copy(tilt = it)) }) { editingCurve = "tilt" }
            Text("Give pan a Wave curve to sweep the beam back and forth.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LayerSettings(clip, onChange) { editingCurve = it }
        EffectPresets(clip, onChange)
        Spacer(Modifier.height(16.dp))
    }

    editingCurve?.let { key ->
        val (label, start, curve) = when (key) {
            "speed" -> Triple("Speed", clip.speed, clip.speedCurveOrRamp)
            "size" -> Triple("Size", clip.size, clip.sizeCurve)
            "pan" -> Triple("Pan", clip.pan ?: 0.5f, clip.panCurve)
            "tilt" -> Triple("Tilt", clip.tilt ?: 0.5f, clip.tiltCurve)
            "hue" -> Triple("Hue shift", clip.hueShift, clip.hueCurve)
            "rotation" -> Triple("Rotate", clip.rotation, clip.rotationCurve)
            else -> Triple("Brightness", clip.intensity, clip.intensityCurveOrRamp)
        }
        CurveDialog(label, start, curve, onDismiss = { editingCurve = null }) { c ->
            onChange(
                when (key) {
                    "speed" -> clip.copy(speedCurve = c, speedEnd = null)
                    "size" -> clip.copy(sizeCurve = c)
                    "pan" -> clip.copy(panCurve = c)
                    "tilt" -> clip.copy(tiltCurve = c)
                    "hue" -> clip.copy(hueCurve = c)
                    "rotation" -> clip.copy(rotationCurve = c)
                    else -> clip.copy(intensityCurve = c, intensityEnd = null)
                }
            )
            editingCurve = null
        }
    }

    if (editingSlot >= 0) {
        val slot = editingSlot
        // Slots 0–2 are the colours, 3–5 the ones they change to.
        val ends = slot >= 3
        val slotColors = if (ends) clip.colorsEnd ?: clip.colors else clip.colors
        var temp by remember(slot) { mutableIntStateOf(slotColors.getOrElse(slot % 3) { 0xFFFF0000.toInt() }) }
        AlertDialog(
            onDismissRequest = { editingSlot = -1 },
            title = { Text(if (ends) "Colour ${slot - 2} at the end" else "Colour ${slot + 1}") },
            text = {
                Column {
                    ColorPickerPanel(temp, onColorChange = { temp = it }, onCommit = {})
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (QuickColors + 0xFF000000.toInt()).forEach { q -> ColorSwatch(q.toComposeColor(), onClick = { temp = q }, size = 32.dp, selected = q == temp) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cols = List(3) { i -> if (i == slot % 3) temp else slotColors.getOrElse(i) { 0xFF000000.toInt() } }
                    onChange(if (ends) clip.copy(colorsEnd = cols) else clip.copy(colors = cols))
                    editingSlot = -1
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editingSlot = -1 }) { Text("Cancel") } },
        )
    }
}

// ================================================================== check

/** What the setup check found, problems first. */
@Composable
private fun SetupCheckDialog(issues: List<com.wledcontroller.app.show.SetupIssue>, onDismiss: () -> Unit, onEditMap: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (issues.isEmpty()) "All good" else "Check: ${issues.size} to look at") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (issues.isEmpty()) {
                    Text("Every prop has a controller and a place on the house, and every show's tracks, pictures, videos and songs are there.")
                }
                issues.forEach { i ->
                    val problem = i.severity == com.wledcontroller.app.show.SetupIssue.Severity.PROBLEM
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            if (problem) Icons.Filled.Error else Icons.Filled.Warning, null,
                            Modifier.size(18.dp).padding(top = 2.dp),
                            tint = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(i.where, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(i.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { if (issues.any { it.where.startsWith("Prop") || it.where.startsWith("Group") }) TextButton(onClick = onEditMap) { Text("Open house map") } },
    )
}

// ================================================================== layer settings and presets

/** xLights' layer settings: turn, spin, zoom, mirror, recolour and sparkle the effect. */
@Composable
private fun LayerSettings(clip: Clip, onChange: (Clip) -> Unit, onEditCurve: (String) -> Unit) {
    var open by remember { mutableStateOf(clip.hasLayerSettings) }
    SectionTitle("Layer settings") {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else if (clip.hasLayerSettings) "Show (in use)" else "Show") }
    }
    if (!open) return
    CurvedSlider("Rotate", clip.rotation, clip.rotationCurve, { onChange(clip.copy(rotation = it)) }) { onEditCurve("rotation") }
    // Spin: −4 … +4 turns over the effect.
    GradientSlider(
        (clip.spin + 4f) / 8f, { onChange(clip.copy(spin = ((it * 8f - 4f) * 4).roundToInt() / 4f)) },
        label = "Spin", valueText = if (clip.spin == 0f) "None" else "%+.2f turns".format(clip.spin), height = 30.dp,
    )
    // Zoom on a doubling scale: ¼× … 4×.
    fun zoomToSlider(z: Float) = ((kotlin.math.ln(z) / kotlin.math.ln(2f)) + 2f) / 4f
    fun sliderToZoom(v: Float) = 2f.pow(v * 4f - 2f).let { (it * 20).roundToInt() / 20f }
    GradientSlider(zoomToSlider(clip.zoom), { onChange(clip.copy(zoom = sliderToZoom(it))) }, label = "Zoom", valueText = "%.2f×".format(clip.zoom), height = 30.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Zoom changes over the effect", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(checked = clip.zoomEnd != null, onCheckedChange = { on -> onChange(clip.copy(zoomEnd = if (on) clip.zoom * 2f else null)) })
    }
    clip.zoomEnd?.let { end ->
        GradientSlider(zoomToSlider(end), { onChange(clip.copy(zoomEnd = sliderToZoom(it))) }, label = "Zoom at the end", valueText = "%.2f×".format(end), height = 30.dp)
    }
    CurvedSlider("Hue shift", clip.hueShift, clip.hueCurve, { onChange(clip.copy(hueShift = it)) }) { onEditCurve("hue") }
    GradientSlider(clip.sparkles, { onChange(clip.copy(sparkles = it)) }, label = "Sparkles", valueText = "${(clip.sparkles * 100).roundToInt()}%", height = 30.dp)
    GradientSlider(clip.blur, { onChange(clip.copy(blur = it)) }, label = "Blur", valueText = "${(clip.blur * 100).roundToInt()}%", height = 30.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = clip.mirrorX, onClick = { onChange(clip.copy(mirrorX = !clip.mirrorX)) }, label = { Text("Mirror ↔") })
        FilterChip(selected = clip.mirrorY, onClick = { onChange(clip.copy(mirrorY = !clip.mirrorY)) }, label = { Text("Flip ↕") })
        if (clip.hasLayerSettings) TextButton(onClick = {
            onChange(clip.copy(rotation = 0f, spin = 0f, zoom = 1f, zoomEnd = null, hueShift = 0f, sparkles = 0f, mirrorX = false, mirrorY = false, blur = 0f, hueCurve = null, rotationCurve = null))
        }) { Text("Reset") }
    }
}

/** Effects saved with all their settings under a name, to put on other effects in any show. */
@Composable
private fun EffectPresets(clip: Clip, onChange: (Clip) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val presets by container.store.effectPresets.collectAsStateWithLifecycle(emptyList())
    var naming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<com.wledcontroller.app.data.EffectPreset?>(null) }
    SectionTitle("Presets") { TextButton(onClick = { naming = true }) { Text("Save this one") } }
    if (presets.isEmpty()) {
        Text("Save an effect you like with all its settings, then tap it here to use it on any effect in any show.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { p ->
                androidx.compose.material3.InputChip(
                    selected = false,
                    onClick = { onChange(p.applyTo(clip)) },
                    label = { Text(p.name) },
                    trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp).clickable { removing = p }) },
                )
            }
        }
    }
    if (naming) {
        var name by remember { mutableStateOf(clip.effect.label) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save as a preset") },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val n = name.trim()
                    scope.launch { container.store.saveEffectPreset(com.wledcontroller.app.data.EffectPreset(n, clip)) }
                    naming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${p.name}?") },
            confirmButton = { TextButton(onClick = { scope.launch { container.store.removeEffectPreset(p.name) }; removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

// ================================================================== music

private enum class BeatStyle(val label: String) { FLASH("Flash"), PULSE("Pulse"), SPARKLE("Sparkle"), CHASE("Chase") }

@Composable
internal fun MusicSheet(
    seq: Sequence,
    envelope: AudioEnvelope?,
    selectedTrack: Track?,
    onDismiss: () -> Unit,
    onChange: (Sequence) -> Unit,
    onTapLyrics: (String) -> Unit = {},
    position: Long = 0,
) {
    val container = LocalAppContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(seq)
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var every by remember { mutableIntStateOf(1) }
    var style by remember { mutableStateOf(BeatStyle.FLASH) }
    var replace by remember { mutableStateOf(true) }
    val themes = remember { HolidayThemes.forToday() }
    var colors by remember { mutableStateOf(themes.first().colors) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            busy = "Importing song…"; error = null
            runCatching { container.sequences.importAudio(context, uri) }
                .onSuccess { (path, title, dur) ->
                    onChange(current.copy(audioFile = path, audioTitle = title, durationMs = dur, marks = emptyList(), bpm = null))
                }
                .onFailure { error = it.message }
            busy = null
        }
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Music", style = MaterialTheme.typography.headlineSmall)
            if (seq.audioFile == null) {
                Text(
                    "Pick a song from your phone. The show's length is set to match it, and it plays in sync with the timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = busy == null) {
                    Icon(Icons.Filled.MusicNote, null); Spacer(Modifier.width(8.dp)); Text("Choose song")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(seq.audioTitle ?: "Song", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatTime(seq.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = busy == null) { Text("Change") }
                    TextButton(onClick = { onChange(seq.copy(audioFile = null, audioTitle = null)) }) { Text("Remove") }
                }
            }
            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text(it)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (seq.audioFile != null) {
                HorizontalDivider()
                LightsDelaySetting()
            }
            HorizontalDivider()
            Text("Beats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (seq.marks.isEmpty()) "No beat marks yet. Detect them from the song, or press Play and tap \"Tap beat\" in time with the music."
                else "${seq.marks.size} marks" + (seq.bpm?.let { " · about ${it.toInt()} BPM" } ?: "") + ". Effects snap to them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    enabled = seq.audioFile != null && busy == null,
                    onClick = {
                        val path = seq.audioFile
                        if (path != null) scope.launch {
                            busy = "Listening for the beat…"; error = null
                            runCatching {
                                withContext(Dispatchers.Default) {
                                    AudioAnalyzer.detectBeats(envelope ?: AudioAnalyzer.envelope(path))
                                }
                            }.onSuccess { r ->
                                if (r.beats.isEmpty()) error = "Couldn't find a steady beat in this song. Try tapping it instead."
                                else onChange(current.copy(marks = r.beats.filter { it < current.durationMs }, bpm = r.bpm))
                            }.onFailure { error = "Couldn't analyse the song: ${it.message}" }
                            busy = null
                        }
                    },
                ) { Text("Detect beats") }
                OutlinedButton(
                    enabled = busy == null && seq.audioFile != null,
                    onClick = {
                        val path = seq.audioFile
                        if (path != null) scope.launch {
                            busy = "Listening for notes…"; error = null
                            runCatching {
                                withContext(Dispatchers.Default) {
                                    val env = envelope?.takeIf { it.bands != null } ?: AudioAnalyzer.envelope(path)
                                    com.wledcontroller.app.show.Onsets.detect(env.values, env.bands, env.hopMs)
                                }
                            }.onSuccess { notes ->
                                if (notes.isEmpty()) error = "Couldn't pick out notes in this song."
                                else onChange(current.copy(marks = notes.filter { it < current.durationMs }, bpm = null))
                            }.onFailure { error = "Couldn't analyse the song: ${it.message}" }
                            busy = null
                        }
                    },
                ) { Text("Detect notes") }
                OutlinedButton(enabled = seq.marks.isNotEmpty(), onClick = { onChange(seq.copy(marks = emptyList(), bpm = null)) }) { Text("Clear marks") }
            }

            HorizontalDivider()
            Text("Effect on every beat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (selectedTrack == null || seq.marks.isEmpty()) {
                Text(
                    "Add beat marks and select a track first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Fills \"${selectedTrack.name}\" with an effect on each beat.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BeatStyle.entries.forEach { b -> FilterChip(selected = style == b, onClick = { style = b }, label = { Text(b.label) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Every beat", 2 to "Every 2nd", 4 to "Every 4th (bar)").forEach { (n, l) ->
                        FilterChip(selected = every == n, onClick = { every = n }, label = { Text(l) })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(themes, key = { it.id }) { t ->
                        Surface(
                            onClick = { colors = t.colors }, shape = RoundedCornerShape(10.dp),
                            color = if (colors == t.colors) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(Modifier.padding(8.dp).width(84.dp)) {
                                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(themeBrush(t.colors, false)))
                                Text(t.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = replace, onCheckedChange = { replace = it })
                    Text("Replace the track's existing effects")
                }
                FilledTonalButton(onClick = {
                    val beats = seq.marks.filterIndexed { i, _ -> i % every == 0 }
                    val clips = beats.mapIndexed { i, m ->
                        val next = beats.getOrNull(i + 1) ?: seq.durationMs
                        val gap = (next - m).coerceAtLeast(80)
                        val color = colors[i % colors.size]
                        when (style) {
                            BeatStyle.FLASH -> Clip(UUID.randomUUID().toString(), m, minOf(gap, 600L), SeqEffect.ON, listOf(color), fadeOutMs = minOf(gap, 600L) * 7 / 10)
                            BeatStyle.PULSE -> Clip(UUID.randomUUID().toString(), m, gap, SeqEffect.ON, listOf(color), fadeInMs = gap / 5, fadeOutMs = gap * 3 / 5)
                            BeatStyle.SPARKLE -> Clip(UUID.randomUUID().toString(), m, gap, SeqEffect.SPARKLE, listOf(color), speed = 0.7f, fadeOutMs = gap / 2)
                            BeatStyle.CHASE -> Clip(UUID.randomUUID().toString(), m, gap, SeqEffect.CHASE, colors, speed = 0.6f)
                        }
                    }
                    onChange(seq.copy(tracks = seq.tracks.map { t ->
                        if (t.id == selectedTrack.id) t.copy(clips = (if (replace) emptyList() else t.clips) + clips) else t
                    }))
                    onDismiss()
                }) { Text("Create ${seq.marks.size / every} effects") }
            }

            HorizontalDivider()
            LyricsSection(seq, selectedTrack, onChange, onTapLyrics = { onTapLyrics(it); onDismiss() })
            HorizontalDivider()
            TimingTracksSection(seq, position, onChange)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Named timing tracks, like xLights': mark the song's sections (or any labels) at the playhead.
 * Effects snap to them and the State effect lights the parts their labels name.
 */
@Composable
private fun TimingTracksSection(seq: Sequence, position: Long, onChange: (Sequence) -> Unit) {
    var selectedId by remember(seq.id) { mutableStateOf(seq.timingTracks.firstOrNull()?.id) }
    var naming by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    val selected = seq.timingTracks.firstOrNull { it.id == selectedId }
    fun update(tt: com.wledcontroller.app.show.TimingTrack) { onChange(seq.copy(timingTracks = seq.timingTracks.map { if (it.id == tt.id) tt else it })) }

    Text("Timing tracks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Label stretches of the song — verse, chorus, bridge, or states like \"eyes\" for the State effect. Each label runs until the next. Effects snap to them.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        seq.timingTracks.forEach { tt -> FilterChip(selected = tt.id == selectedId, onClick = { selectedId = tt.id }, label = { Text(tt.name) }) }
        AssistChip(onClick = { naming = true }, label = { Text("New track") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) })
    }
    if (selected != null) {
        Text("At ${formatTime(position, true)}:", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Intro", "Verse", "Chorus", "Bridge", "Solo", "Outro").forEach { l ->
                SuggestionChip(onClick = { update(selected.withMark(position, l, seq.durationMs)) }, label = { Text(l) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(label, { label = it.take(40) }, singleLine = true, label = { Text("Other label") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(enabled = label.isNotBlank(), onClick = { update(selected.withMark(position, label.trim(), seq.durationMs)); label = "" }) { Text("Mark") }
        }
        selected.marks.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${formatTime(m.startMs, true)}  ${m.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { update(selected.without(m.startMs, seq.durationMs)) }) { Icon(Icons.Filled.Close, "Remove", Modifier.size(18.dp)) }
            }
        }
        TextButton(onClick = { onChange(seq.copy(timingTracks = seq.timingTracks.filterNot { it.id == selected.id })); selectedId = null }) {
            Text("Delete \"${selected.name}\"")
        }
    }
    if (naming) {
        var name by remember { mutableStateOf(if (seq.timingTracks.isEmpty()) "Sections" else "Timing ${seq.timingTracks.size + 1}") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("New timing track") },
            text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val tt = com.wledcontroller.app.show.TimingTrack(UUID.randomUUID().toString(), name.trim())
                    onChange(seq.copy(timingTracks = seq.timingTracks + tt)); selectedId = tt.id; naming = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
}

/** Draw a path with a finger for the Sketch effect. */
@Composable
private fun SketchDialog(initial: List<MapPoint>, onDismiss: () -> Unit, onDone: (List<MapPoint>) -> Unit) {
    val points = remember { mutableStateListOf<MapPoint>().apply { addAll(initial) } }
    val ink = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw the path") },
        text = {
            Column {
                Text("Draw with one finger: the lights will trace it from where you start.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF14161C))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                points.clear()
                                fun add(o: Offset) {
                                    val p = MapPoint((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
                                    val last = points.lastOrNull()
                                    if (last == null || kotlin.math.hypot(p.x - last.x, p.y - last.y) > 0.01f) points.add(p)
                                }
                                add(down.position)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    add(ch.position); ch.consume()
                                    if (points.size > 240) break
                                }
                            }
                        }
                ) {
                    for ((a, b) in points.zipWithNext()) {
                        drawLine(ink, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                    points.firstOrNull()?.let { drawCircle(Color.White, 5.dp.toPx(), Offset(it.x * size.width, it.y * size.height)) }
                }
            }
        },
        confirmButton = { TextButton(enabled = points.size >= 2, onClick = { onDone(points.toList()) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { points.clear() }) { Text("Clear") } },
    )
}

/**
 * The song's words, for singing faces (like xLights' lyric track): paste them, then either let
 * them spread over the song or tap along as each line is sung. Each word gets its mouth shapes.
 */
@Composable
private fun LyricsSection(seq: Sequence, selectedTrack: Track?, onChange: (Sequence) -> Unit, onTapLyrics: (String) -> Unit) {
    var text by remember(seq.id) { mutableStateOf(seq.lyrics?.phrases?.joinToString("\n") { it.label } ?: "") }
    val lyrics = seq.lyrics
    Text("Lyrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Paste the words, one line per sung line. Singing faces (the Sing effect) then mouth each word: closed, open or wide for every sound.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        text, { text = it }, label = { Text("Words") }, minLines = 3, maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledTonalButton(enabled = text.isNotBlank() && seq.audioFile != null, onClick = { onTapLyrics(text) }) { Text("Tap along") }
        OutlinedButton(enabled = text.isNotBlank(), onClick = { onChange(seq.copy(lyrics = LyricSync.breakdown(text, 0, seq.durationMs))) }) { Text("Spread over the song") }
        if (lyrics != null) OutlinedButton(onClick = { onChange(seq.copy(lyrics = null)) }) { Text("Clear") }
    }
    Text(
        "Tap along plays the song: tap as each line starts. Spread shares the whole song out by syllables (a rough start to fine-tune).",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (lyrics != null && !lyrics.isEmpty) {
        Text("${lyrics.phrases.size} lines, ${lyrics.words.size} words timed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (selectedTrack != null) {
            OutlinedButton(onClick = {
                // One Sing effect from the first word to the last on the selected track.
                val s = lyrics.phrases.first().startMs; val e = lyrics.phrases.last().endMs
                val clip = Clip(UUID.randomUUID().toString(), s, (e - s).coerceAtLeast(500), SeqEffect.SING, listOf(0xFFFFFFFF.toInt(), 0xFFFF2020.toInt()))
                onChange(seq.copy(tracks = seq.tracks.map { t -> if (t.id == selectedTrack.id) t.copy(clips = t.clips + clip) else t }))
            }) { Text("Sing them on \"${selectedTrack.name}\"") }
        }
    }
}

/** App-wide lights delay, so the lights match music on a laggy (e.g. Bluetooth) speaker. */
@Composable
internal fun LightsDelaySetting() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val stored by container.store.lightsDelayMs.collectAsStateWithLifecycle(0)
    var local by remember(stored) { mutableFloatStateOf(stored.toFloat()) }
    fun save(v: Int) = scope.launch { container.store.setLightsDelay(v) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Lights delay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "If the lights run ahead of the music (common with Bluetooth speakers), delay them. Play a show with a strong beat and adjust until flashes land on the beat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GradientSlider(
            value = (local + 500f) / 1500f,
            onValueChange = { local = ((it * 1500f - 500f) / 10f).roundToLong() * 10f },
            onValueChangeFinished = { save(local.toInt()) },
            label = if (local < 0) "Lights lead by" else "Lights wait",
            valueText = "${abs(local.toInt())} ms",
            height = 30.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Phone speaker", 200 to "Bluetooth", 350 to "Slow Bluetooth").forEach { (v, l) ->
                FilterChip(selected = local.toInt() == v, onClick = { local = v.toFloat(); save(v) }, label = { Text(l) })
            }
        }
    }
}

/** A setting's slider, with a button to make it follow a curve over the effect. */
@Composable
private fun CurvedSlider(label: String, value: Float, curve: ValueCurve?, onChange: (Float) -> Unit, onEditCurve: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        GradientSlider(
            value, onChange, modifier = Modifier.weight(1f),
            label = if (curve != null) "$label at start" else label, valueText = "${(value * 100).toInt()}%", height = 30.dp,
        )
        Spacer(Modifier.width(6.dp))
        FilledTonalIconButton(
            onClick = onEditCurve,
            colors = if (curve != null) IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            else IconButtonDefaults.filledTonalIconButtonColors(),
        ) { Icon(Icons.AutoMirrored.Filled.ShowChart, "$label curve") }
    }
    if (curve != null) {
        Text(
            "Follows a ${curve.shape.label.lowercase()} curve to ${(curve.end * 100).toInt()}%" + if (curve.shape.periodic) ", ${"%.1f".format(curve.cycles).removeSuffix(".0")} times" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Picks the shape of a value curve and where it ends, with a picture of it. */
@Composable
private fun CurveDialog(label: String, start: Float, curve: ValueCurve?, onDismiss: () -> Unit, onSave: (ValueCurve?) -> Unit) {
    var shape by remember { mutableStateOf(curve?.shape ?: CurveShape.RAMP) }
    var end by remember { mutableFloatStateOf(curve?.end ?: if (start > 0.5f) 0f else 1f) }
    var cycles by remember { mutableFloatStateOf(curve?.cycles ?: 2f) }
    val draft = ValueCurve(shape, end, cycles)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$label curve") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "$label goes from its slider value (${(start * 100).toInt()}%) along this curve over the effect's length.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CurveShape.entries.forEach { sh -> FilterChip(selected = shape == sh, onClick = { shape = sh }, label = { Text(sh.label) }) }
                }
                Canvas(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
                    val path = Path()
                    val n = 80
                    for (i in 0..n) {
                        val p = i / n.toFloat()
                        val v = draft.at(start, p)
                        val x = p * size.width; val y = (1f - v) * (size.height - 8f) + 4f
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(2.5f))
                }
                GradientSlider(end, { end = it }, label = if (shape.periodic) "Swings to" else "Ends at", valueText = "${(end * 100).toInt()}%", height = 28.dp)
                if (shape.periodic) {
                    GradientSlider((cycles - 0.5f) / 15.5f, { cycles = 0.5f + it * 15.5f }, label = "Times", valueText = "%.1f".format(cycles), height = 28.dp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Use curve") } },
        dismissButton = {
            Row {
                if (curve != null) TextButton(onClick = { onSave(null) }) { Text("No curve") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** How an effect comes in or goes out over its fade time. */
@Composable
private fun TransitionRow(label: String, value: Transition, enabled: Boolean, onChange: (Transition) -> Unit) {
    Text(label + if (!enabled) " (set a fade time first)" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Transition.entries.forEach { t -> FilterChip(selected = value == t, enabled = enabled, onClick = { onChange(t) }, label = { Text(t.label) }) }
    }
}
