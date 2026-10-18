@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.wledcontroller.app.show.AudioAnalyzer
import com.wledcontroller.app.show.AudioEnvelope
import com.wledcontroller.app.show.Clip
import com.wledcontroller.app.show.ClipClipboard
import com.wledcontroller.app.show.SeqEffect
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shows") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.FileOpen, "Import xLights .fseq") }
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
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = s })
                            }
                        }
                    }
                }
            }
        }
    }

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
                    val colors = remember(pos, seq, layout) { SequenceRenderer.render(seq, layout, pos) }
                    HouseCanvas(map, photo, Modifier.heightIn(max = if (landscape) 260.dp else 200.dp)) {
                        val r = 3.dp.toPx()
                        layout.leds.forEachIndexed { i, l ->
                            val c = Color(colors[i] or (0xFF shl 24))
                            val o = Offset(l.x * size.width, l.y * size.height)
                            if (colors[i] != 0) drawCircle(c.copy(alpha = 0.35f), r * 2.2f, o)
                            drawCircle(if (colors[i] == 0) Color.White.copy(alpha = 0.12f) else c, r, o)
                        }
                    }
                }
            }
            val transport: @Composable () -> Unit = {
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
                        modifier = m,
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
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            seq.tracks.forEach { track ->
                val isSel = track.id == selectedTrack
                Row(Modifier.height(TrackHeight)) {
                    TrackHeader(track, map, isSel, onSelect = { onSelectTrack(track.id) }, onChange = onTrackChange, onDelete = { onTrackDelete(track) }, onMove = { onTrackMove(track, it) })
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
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val kind = when (track.target.kind) {
        TrackTarget.Kind.HOUSE -> "Whole house"
        TrackTarget.Kind.GROUP -> if (map.groups.any { it.id == track.target.refId }) "Group" else "Missing group"
        TrackTarget.Kind.PROP -> map.runs.firstOrNull { it.id == track.target.refId }?.prop?.label ?: "Missing prop"
    }
    Surface(
        onClick = onSelect,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(HeaderWidth).fillMaxHeight(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(track.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (track.muted) Icon(Icons.Filled.VolumeOff, "Muted", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "Track options", Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (track.muted) "Unmute" else "Mute") }, onClick = { menu = false; onChange(track.copy(muted = !track.muted)) })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
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
    modifier: Modifier,
) {
    var editingSlot by remember { mutableIntStateOf(-1) }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SeqEffect.entries.forEach { e -> FilterChip(selected = clip.effect == e, onClick = { onChange(clip.copy(effect = e)) }, label = { Text(e.label) }) }
        }

        SectionTitle("Colours")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 3) {
                ColorSwatch(clip.colors.getOrElse(i) { 0xFF000000.toInt() }.toComposeColor(), onClick = { editingSlot = i }, size = 40.dp)
            }
            Text("Tap to change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        GradientSlider(
            clip.speed, { onChange(clip.copy(speed = it)) },
            label = if (clip.speedEnd != null) "Speed at start" else "Speed", valueText = "${(clip.speed * 100).toInt()}%", height = 30.dp,
        )
        RampRow("Speed ramp", clip.speedEnd, clip.speed, onChange = { onChange(clip.copy(speedEnd = it)) })
        GradientSlider(clip.size, { onChange(clip.copy(size = it)) }, label = "Size", valueText = "${(clip.size * 100).toInt()}%", height = 30.dp)
        GradientSlider(
            clip.intensity, { onChange(clip.copy(intensity = it)) },
            label = if (clip.intensityEnd != null) "Brightness at start" else "Brightness", valueText = "${(clip.intensity * 100).toInt()}%", height = 30.dp,
        )
        RampRow("Brightness ramp", clip.intensityEnd, clip.intensity, onChange = { onChange(clip.copy(intensityEnd = it)) })
        val maxFade = (clip.durationMs / 2).coerceAtLeast(1)
        GradientSlider(
            clip.fadeInMs.toFloat() / maxFade, { onChange(clip.copy(fadeInMs = (it * maxFade).toLong())) },
            label = "Fade in", valueText = "%.1f s".format(clip.fadeInMs / 1000f), height = 30.dp,
        )
        GradientSlider(
            clip.fadeOutMs.toFloat() / maxFade, { onChange(clip.copy(fadeOutMs = (it * maxFade).toLong())) },
            label = "Fade out", valueText = "%.1f s".format(clip.fadeOutMs / 1000f), height = 30.dp,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (editingSlot >= 0) {
        val slot = editingSlot
        var temp by remember(slot) { mutableIntStateOf(clip.colors.getOrElse(slot) { 0xFFFF0000.toInt() }) }
        AlertDialog(
            onDismissRequest = { editingSlot = -1 },
            title = { Text("Colour ${slot + 1}") },
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
                    val cols = List(3) { i -> if (i == slot) temp else clip.colors.getOrElse(i) { 0xFF000000.toInt() } }
                    onChange(clip.copy(colors = cols))
                    editingSlot = -1
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editingSlot = -1 }) { Text("Cancel") } },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Spacer(Modifier.height(24.dp))
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

/** Turns a ramp on (ending at the current value, so nothing jumps) and edits where it ends. */
@Composable
private fun RampRow(label: String, end: Float?, start: Float, onChange: (Float?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Switch(checked = end != null, onCheckedChange = { on -> onChange(if (on) start else null) })
    }
    if (end != null) {
        GradientSlider(end, { onChange(it) }, label = "…at the end", valueText = "${(end * 100).toInt()}%", height = 30.dp)
    }
}
