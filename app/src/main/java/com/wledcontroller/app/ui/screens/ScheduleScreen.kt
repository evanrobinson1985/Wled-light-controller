@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.show.Playlist
import com.wledcontroller.app.show.GeoPoint
import com.wledcontroller.app.show.ScheduleEntry
import com.wledcontroller.app.show.Solar
import com.wledcontroller.app.show.TimeKind
import com.wledcontroller.app.show.formatMonthDay
import com.wledcontroller.app.show.formatOffset
import com.wledcontroller.app.show.parseMonthDay
import java.time.LocalDate
import java.time.ZoneId
import com.wledcontroller.app.show.Sequence
import com.wledcontroller.app.show.ShowRunnerService
import com.wledcontroller.app.show.formatMinutes
import com.wledcontroller.app.show.formatTime
import com.wledcontroller.app.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.UUID

@SuppressLint("BatteryLife")
@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by container.store.playlists.collectAsStateWithLifecycle(emptyList())
    val schedules by container.store.schedules.collectAsStateWithLifecycle(emptyList())
    val scheduleOn by container.store.scheduleOn.collectAsStateWithLifecycle(false)
    val runner by container.showRunner.state.collectAsStateWithLifecycle()
    val shows by container.sequences.all.collectAsStateWithLifecycle()
    val place by container.store.location.collectAsStateWithLifecycle(null)

    var editPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var editSchedule by remember { mutableStateOf<ScheduleEntry?>(null) }

    val pm = context.getSystemService(PowerManager::class.java)
    var batteryOk by remember { mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) == true) }

    fun enable() {
        scope.launch {
            container.store.setScheduleOn(true)
            ShowRunnerService.startSchedule(context)
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { enable() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Show schedule") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (scheduleOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Run show schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    runner.nowPlaying != null -> "Now playing: ${runner.nowPlaying}"
                                    scheduleOn && runner.nextStart != null ->
                                        "Next show: " + runner.nextStart!!.format(DateTimeFormatter.ofPattern("EEE MMM d, h:mm a"))
                                    scheduleOn && schedules.none { it.enabled } -> "On, but no schedules are set up yet"
                                    scheduleOn -> "Waiting for a show window"
                                    else -> "Off — shows only play when you start them"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = scheduleOn,
                            onCheckedChange = { on ->
                                if (on) {
                                    if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else enable()
                                } else {
                                    ShowRunnerService.stop(context)
                                }
                            },
                        )
                    }
                    if (runner.running && runner.mode == "playlist") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { ShowRunnerService.stop(context) }) {
                            Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop playlist")
                        }
                    }
                }
            }
            Text(
                "Your phone runs the show, like a show computer: keep it plugged in and on your home Wi-Fi. It keeps working with the screen off and restarts after a reboot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!batteryOk) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                    }
                    batteryOk = true
                }) { Text("Allow running in the background") }
            }

            LightsDelaySetting()

            SectionTitle("Playlists") {
                TextButton(onClick = { editPlaylist = Playlist(UUID.randomUUID().toString(), "", emptyList()) }, enabled = shows.isNotEmpty()) {
                    Icon(Icons.Filled.Add, null); Text("New")
                }
            }
            if (shows.isEmpty()) {
                Text("Create a show first, then add it to a playlist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (playlists.isEmpty()) {
                Text("A playlist is a set of shows played back to back.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            playlists.forEach { p ->
                val total = p.sequenceIds.mapNotNull { id -> shows.firstOrNull { it.id == id } }.sumOf { it.durationMs }
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${p.sequenceIds.size} shows · ${formatTime(total)}" + if (p.loop) " · loops" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { ShowRunnerService.playNow(context, p.id) }, enabled = p.sequenceIds.isNotEmpty()) {
                            Icon(Icons.Filled.PlayArrow, "Play now")
                        }
                        IconButton(onClick = { editPlaylist = p }) { Icon(Icons.Outlined.Edit, "Edit playlist") }
                        IconButton(onClick = { scope.launch { container.store.removePlaylist(p.id) } }) { Icon(Icons.Outlined.Delete, "Delete playlist") }
                    }
                }
            }

            SectionTitle("When to play") {
                TextButton(
                    onClick = { editSchedule = ScheduleEntry(UUID.randomUUID().toString(), playlists.first().id) },
                    enabled = playlists.isNotEmpty(),
                ) { Icon(Icons.Filled.Add, null); Text("New") }
            }
            if (schedules.isEmpty()) {
                Text(
                    "Add a time window, e.g. every day 5:00–10:00 PM, and the playlist plays automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            schedules.forEach { e ->
                val p = playlists.firstOrNull { it.id == e.playlistId }
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.describe(), fontWeight = FontWeight.SemiBold)
                            if (e.startKind != TimeKind.CLOCK || e.endKind != TimeKind.CLOCK) {
                                val (st, en) = e.window(LocalDate.now(), place)
                                Text(
                                    if (place == null) "Set your location below for sunset times"
                                    else "Tonight ${formatMinutes(st)}–${formatMinutes(en)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (place == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                (p?.name ?: "Missing playlist") + if (e.lightsOffAtEnd) " · lights off after" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = e.enabled, onCheckedChange = { v -> scope.launch { container.store.upsertSchedule(e.copy(enabled = v)) } })
                        IconButton(onClick = { editSchedule = e }) { Icon(Icons.Outlined.Edit, "Edit schedule") }
                        IconButton(onClick = { scope.launch { container.store.removeSchedule(e.id) } }) { Icon(Icons.Outlined.Delete, "Delete schedule") }
                    }
                }
            }
            LocationCard()
            Spacer(Modifier.height(32.dp))
        }
    }

    editPlaylist?.let { p ->
        PlaylistDialog(p, shows, onDismiss = { editPlaylist = null }) { updated ->
            scope.launch { container.store.upsertPlaylist(updated) }
            editPlaylist = null
        }
    }
    editSchedule?.let { e ->
        ScheduleDialog(e, playlists, place, onDismiss = { editSchedule = null }) { updated ->
            scope.launch { container.store.upsertSchedule(updated) }
            editSchedule = null
        }
    }
}

@Composable
private fun PlaylistDialog(playlist: Playlist, shows: List<Sequence>, onDismiss: () -> Unit, onSave: (Playlist) -> Unit) {
    var name by remember { mutableStateOf(playlist.name) }
    var loop by remember { mutableStateOf(playlist.loop) }
    var gap by remember { mutableStateOf(playlist.gapSec.toString()) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(playlist.sequenceIds) } }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (playlist.name.isEmpty()) "New playlist" else "Edit playlist") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                chosen.forEachIndexed { i, id ->
                    val s = shows.firstOrNull { it.id == id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${s?.name ?: "Missing show"}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { if (i > 0) chosen.add(i - 1, chosen.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                        IconButton(onClick = { if (i < chosen.lastIndex) chosen.add(i + 1, chosen.removeAt(i)) }, enabled = i < chosen.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, "Move down")
                        }
                        IconButton(onClick = { chosen.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                }
                Box {
                    OutlinedButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add show") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        shows.forEach { s ->
                            DropdownMenuItem(text = { Text("${s.name} · ${formatTime(s.durationMs)}") }, onClick = { chosen.add(s.id); menu = false })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(loop, { loop = it }); Text("Loop until the time window ends")
                }
                OutlinedTextField(
                    gap, { gap = it.filter(Char::isDigit).take(3) }, label = { Text("Pause between shows (seconds)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && chosen.isNotEmpty(), onClick = {
                onSave(playlist.copy(name = name.trim(), sequenceIds = chosen.toList(), loop = loop, gapSec = gap.toIntOrNull() ?: 0))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun ScheduleDialog(entry: ScheduleEntry, playlists: List<Playlist>, place: GeoPoint?, onDismiss: () -> Unit, onSave: (ScheduleEntry) -> Unit) {
    val context = LocalContext.current
    var playlistId by remember { mutableStateOf(entry.playlistId) }
    val days = remember { mutableStateListOf<Int>().apply { addAll(entry.days) } }
    var start by remember { mutableIntStateOf(entry.startMin) }
    var end by remember { mutableIntStateOf(entry.endMin) }
    var startKind by remember { mutableStateOf(entry.startKind) }
    var endKind by remember { mutableStateOf(entry.endKind) }
    var startOffset by remember { mutableIntStateOf(entry.startOffsetMin) }
    var endOffset by remember { mutableIntStateOf(entry.endOffsetMin) }
    var seasonOn by remember { mutableStateOf(entry.seasonStart != null && entry.seasonEnd != null) }
    var seasonStart by remember { mutableStateOf(entry.seasonStart ?: "11-25") }
    var seasonEnd by remember { mutableStateOf(entry.seasonEnd ?: "01-06") }
    var lightsOff by remember { mutableStateOf(entry.lightsOffAtEnd) }
    var menu by remember { mutableStateOf(false) }

    fun pick(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60 % 24, initial % 60, false).show()
    }
    fun pickDate(current: String, onPicked: (String) -> Unit) {
        val md = parseMonthDay(current) ?: java.time.MonthDay.of(12, 1)
        DatePickerDialog(context, { _, _, m, d -> onPicked("%02d-%02d".format(m + 1, d)) }, LocalDate.now().year, md.monthValue - 1, md.dayOfMonth).show()
    }
    val draft = entry.copy(
        startMin = start, endMin = end, startKind = startKind, endKind = endKind,
        startOffsetMin = startOffset, endOffsetMin = endOffset,
        seasonStart = if (seasonOn) seasonStart else null, seasonEnd = if (seasonOn) seasonEnd else null,
    )
    val usesSun = startKind != TimeKind.CLOCK || endKind != TimeKind.CLOCK

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show time") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(playlists.firstOrNull { it.id == playlistId }?.name ?: "Choose playlist", Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        playlists.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { playlistId = p.id; menu = false }) }
                    }
                }
                Text("Days", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayLetters.forEachIndexed { i, l ->
                        val d = i + 1
                        FilterChip(selected = d in days, onClick = { if (d in days) days.remove(d) else days.add(d) }, label = { Text(l) })
                    }
                }

                TimeChooser(
                    "Starts", listOf(TimeKind.CLOCK, TimeKind.SUNSET, TimeKind.DUSK), startKind, { startKind = it },
                    start, { pick(start) { v -> start = v } }, startOffset, { startOffset = it },
                )
                TimeChooser(
                    "Ends", listOf(TimeKind.CLOCK, TimeKind.SUNRISE), endKind, { endKind = it },
                    end, { pick(end) { v -> end = v } }, endOffset, { endOffset = it },
                )
                val (ws, we) = draft.window(LocalDate.now(), place)
                Text(
                    when {
                        usesSun && place == null -> "Set your location on the schedule screen so the app knows when sunset is. Until then the clock times are used."
                        else -> "Tonight: ${formatMinutes(ws)}–${formatMinutes(we)}" + if (we >= 1440) " (after midnight)" else ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (usesSun && place == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Only between dates", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Switch(checked = seasonOn, onCheckedChange = { seasonOn = it })
                }
                if (seasonOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickDate(seasonStart) { seasonStart = it } }) { Text(formatMonthDay(seasonStart)) }
                        Text("to")
                        OutlinedButton(onClick = { pickDate(seasonEnd) { seasonEnd = it } }) { Text(formatMonthDay(seasonEnd)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Christmas" to ("11-25" to "01-06"), "Halloween" to ("10-01" to "10-31"),
                            "Thanksgiving week" to ("11-20" to "11-30"), "July 4th" to ("07-01" to "07-05"),
                        ).forEach { (label, range) ->
                            AssistChip(onClick = { seasonStart = range.first; seasonEnd = range.second }, label = { Text(label) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(lightsOff, { lightsOff = it }); Text("Turn lights off when it ends")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = days.isNotEmpty() && playlists.any { it.id == playlistId } && (usesSun || start != end),
                onClick = { onSave(draft.copy(playlistId = playlistId, days = days.toSet(), lightsOffAtEnd = lightsOff)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A start or end time: a clock time, or sunset / dusk / sunrise plus or minus an offset. */
@Composable
private fun TimeChooser(
    label: String,
    kinds: List<TimeKind>,
    kind: TimeKind,
    onKind: (TimeKind) -> Unit,
    clock: Int,
    onPickClock: () -> Unit,
    offset: Int,
    onOffset: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { onKind(k) }, label = { Text(k.label) }) }
        }
        if (kind == TimeKind.CLOCK) {
            OutlinedButton(onClick = onPickClock) { Text(formatMinutes(clock)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onOffset(offset - 15) }) { Text("− 15 min") }
                Text(kind.label + formatOffset(offset), Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onOffset(offset + 15) }) { Text("+ 15 min") }
            }
        }
    }
}

/** Where the show is, so sunset and dusk times can be worked out. */
@Composable
private fun LocationCard() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val place by container.store.location.collectAsStateWithLifecycle(null)
    var editing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    @SuppressLint("MissingPermission")
    fun locate() {
        val lm = context.getSystemService(android.location.LocationManager::class.java)
        val loc = lm?.getProviders(true)?.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }?.maxByOrNull { it.time }
        if (loc != null) {
            scope.launch { container.store.setLocation(GeoPoint(loc.latitude, loc.longitude)) }
            error = null
        } else if (lm != null && android.os.Build.VERSION.SDK_INT >= 30) {
            val provider = if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) android.location.LocationManager.NETWORK_PROVIDER
            else android.location.LocationManager.GPS_PROVIDER
            runCatching {
                lm.getCurrentLocation(provider, null, context.mainExecutor) { l ->
                    if (l != null) scope.launch { container.store.setLocation(GeoPoint(l.latitude, l.longitude)) }
                    else error = "Couldn't get your location. Turn on location, or enter it by hand."
                }
            }.onFailure { error = "Couldn't get your location. Enter it by hand." }
        } else error = "Couldn't get your location. Turn on location, or enter it by hand."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else error = "Location permission was declined. You can enter it by hand."
    }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Location for sunset times", fontWeight = FontWeight.SemiBold)
            val p = place
            if (p == null) {
                Text(
                    "Shows can start at sunset or dusk. The app works the time out each day from where you are — it isn't shared anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val today = LocalDate.now(); val zone = ZoneId.systemDefault()
                val sunset = Solar.sunset(today, p, zone); val dusk = Solar.dusk(today, p, zone)
                Text("%.3f, %.3f".format(p.lat, p.lon), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Today: sunset ${sunset?.let(::formatMinutes) ?: "—"}, dusk ${dusk?.let(::formatMinutes) ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text("Use my location") }
                TextButton(onClick = { editing = true }) { Text("Enter by hand") }
            }
        }
    }

    if (editing) {
        var lat by remember { mutableStateOf(place?.lat?.toString() ?: "") }
        var lon by remember { mutableStateOf(place?.lon?.toString() ?: "") }
        val la = lat.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
        val lo = lon.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Your location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Latitude and longitude, e.g. 40.71 and -74.01 for New York. Long-press your house in Google Maps to copy them.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(lat, { lat = it.trim() }, label = { Text("Latitude") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                    OutlinedTextField(lon, { lon = it.trim() }, label = { Text("Longitude") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                }
            },
            confirmButton = {
                TextButton(enabled = la != null && lo != null, onClick = {
                    scope.launch { container.store.setLocation(GeoPoint(la!!, lo!!)) }
                    editing = false; error = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}
