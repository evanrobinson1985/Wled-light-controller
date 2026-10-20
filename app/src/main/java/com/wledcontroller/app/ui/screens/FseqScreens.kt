@file:OptIn(ExperimentalMaterial3Api::class)

package com.wledcontroller.app.ui.screens

import com.wledcontroller.app.ui.components.LocalPixelStyle
import com.wledcontroller.app.ui.components.drawPixel
import com.wledcontroller.app.ui.components.neighbourGaps
import com.wledcontroller.app.ui.components.pixelRadius
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.WledApi
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.placedLeds
import com.wledcontroller.app.show.AudioAnalyzer
import com.wledcontroller.app.show.AudioEnvelope
import com.wledcontroller.app.show.FseqMapping
import com.wledcontroller.app.show.FseqReader
import com.wledcontroller.app.show.Sequence
import com.wledcontroller.app.show.XlightsNetworks
import com.wledcontroller.app.show.formatTime
import com.wledcontroller.app.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A show imported from xLights: map controllers onto its channels, preview, play. */
@Composable
internal fun FseqShowEditor(initial: Sequence, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = container.player
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val photo = rememberPhoto(map.photoPath)
    val devices by container.devices.list.collectAsStateWithLifecycle()

    var seq by remember(initial.id) { mutableStateOf(initial) }
    LaunchedEffect(seq) {
        if (seq === initial) return@LaunchedEffect
        delay(600)
        container.sequences.save(seq)
    }
    val path = seq.fseqFile.orEmpty()
    val readerResult by produceState<Result<FseqReader>?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching { FseqReader.open(path) } }
    }
    val reader = readerResult?.getOrNull()

    DisposableEffect(Unit) {
        player.seek(0)
        onDispose { player.stopAll(); player.loadAudio(null) }
    }
    LaunchedEffect(seq) { player.sequence = seq }
    var envelope by remember(seq.audioFile) { mutableStateOf<AudioEnvelope?>(null) }
    LaunchedEffect(seq.audioFile) {
        val audio = seq.audioFile
        withContext(Dispatchers.IO) { player.loadAudio(audio) }
        envelope = audio?.let { withContext(Dispatchers.Default) { runCatching { AudioAnalyzer.envelope(it) }.getOrNull() } }
    }
    val pos by player.position.collectAsStateWithLifecycle()
    val playing by player.playing.collectAsStateWithLifecycle()
    val live by player.live.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(live, playing) {
        view.keepScreenOn = live || playing
        onDispose { view.keepScreenOn = false }
    }

    // House preview: each mapped LED takes its colour from the current frame.
    val leds = remember(map) { map.placedLeds() }
    val gaps = remember(leds, map.aspect) { neighbourGaps(leds, map.aspect) }
    val pixel = LocalPixelStyle.current
    val frameIndex = reader?.let { (pos / it.stepMs).toInt() } ?: 0
    val colors by produceState(IntArray(0), frameIndex, reader, seq.fseqMap, leds) {
        val r = reader ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val f = r.frame(frameIndex)
            IntArray(leds.size) { i ->
                val l = leds[i]
                val m = seq.fseqMap.firstOrNull { it.enabled && it.deviceId == l.deviceId && l.index < it.ledCount }
                if (m == null) 0 else {
                    val ch = m.startChannel - 1 + l.index * 3
                    (r.channel(f, ch) shl 16) or (r.channel(f, ch + 1) shl 8) or r.channel(f, ch + 2)
                }
            }
        }
    }

    var showMusic by remember { mutableStateOf(false) }
    var networksMessage by remember { mutableStateOf<String?>(null) }
    val networksPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { XlightsNetworks.parse(it) } }
            }
            result.onSuccess { controllers ->
                var matched = 0
                val newMap = devices.map { d ->
                    val ip = WledApi.normalizeHost(d.device.host).substringBefore(':')
                    val c = controllers.firstOrNull { it.ip == ip }
                    val old = seq.fseqMap.firstOrNull { it.deviceId == d.id }
                    if (c != null) {
                        matched++
                        FseqMapping(d.id, c.startChannel, c.channels / 3, true)
                    } else old?.copy(enabled = false) ?: FseqMapping(d.id, 1, d.snapshot.value.info?.ledCount ?: 0, false)
                }
                seq = seq.copy(fseqMap = newMap)
                val unmatched = controllers.filter { c -> devices.none { WledApi.normalizeHost(it.device.host).substringBefore(':') == c.ip } }
                networksMessage = "Matched $matched of ${devices.size} controllers by IP." +
                    if (unmatched.isNotEmpty()) " In xLights but not in this app: ${unmatched.joinToString { "${it.name} (${it.ip})" }}." else ""
            }.onFailure { networksMessage = "Couldn't read that file: ${it.message}" }
        }
    }

    fun mappingFor(id: String) = seq.fseqMap.firstOrNull { it.deviceId == id }
    fun setMapping(m: FseqMapping) {
        seq = seq.copy(fseqMap = seq.fseqMap.filterNot { it.deviceId == m.deviceId } + m)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seq.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
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
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                readerResult == null -> Text("Reading sequence…")
                reader == null -> Text(
                    "Couldn't read this sequence: ${readerResult?.exceptionOrNull()?.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("xLights sequence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatTime(reader.durationMs)} · ${1000 / reader.stepMs} fps · ${"%,d".format(reader.totalChannels)} channels · " +
                                    "FSEQ v${reader.majorVersion} ${reader.compressionName}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            reader.mediaFile?.let { mf ->
                                Text(
                                    if (seq.audioFile == null) "Made for \"$mf\" — add it with the ♪ button so it plays along."
                                    else "Song: ${seq.audioTitle ?: mf}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (seq.audioFile == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (leds.isNotEmpty()) {
                        HouseCanvas(map, photo, Modifier.heightIn(max = 220.dp)) {
                            leds.forEachIndexed { i, l ->
                                if (!l.hidden) drawPixel(colors.getOrElse(i) { 0 }, Offset(l.x * size.width, l.y * size.height), pixelRadius(pixel, gaps[i]), pixel.glowAmount())
                            }
                        }
                    } else {
                        Text(
                            "Map your lights on the house map to see a preview here. Playback on the lights works either way.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = { if (playing) player.pause() else player.play() }) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play")
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${formatTime(pos, true)} / ${formatTime(seq.durationMs)}", style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = if (seq.durationMs > 0) pos.toFloat() / seq.durationMs else 0f,
                        onValueChange = { player.seek((it * seq.durationMs).toLong()) },
                    )
                    if (!live) {
                        Text(
                            "Turn on Live to send it to your lights.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SectionTitle("Controllers")
                    Text(
                        "Where each controller's lights are in the sequence — the start channel and pixel count xLights uses for it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { networksPicker.launch(arrayOf("*/*")) }) { Text("Import xlights_networks.xml") }
                        OutlinedButton(onClick = {
                            var next = 1
                            seq = seq.copy(fseqMap = devices.map { d ->
                                val leds2 = mappingFor(d.id)?.ledCount?.takeIf { it > 0 } ?: d.snapshot.value.info?.ledCount ?: 0
                                FseqMapping(d.id, next, leds2, true).also { next += leds2 * 3 }
                            })
                            networksMessage = null
                        }) { Text("In order") }
                    }
                    networksMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    devices.forEach { d ->
                        val m = mappingFor(d.id) ?: FseqMapping(d.id, 1, d.snapshot.value.info?.ledCount ?: 0, false)
                        MappingRow(d.device.name, m, reader.totalChannels) { setMapping(it) }
                    }
                }
            }
            Spacer(Modifier.width(1.dp).heightIn(min = 32.dp))
        }
    }

    if (showMusic) {
        MusicSheet(seq = seq, envelope = envelope, selectedTrack = null, onDismiss = { showMusic = false }, onChange = { seq = it.copy(durationMs = maxOf(it.durationMs, reader?.durationMs ?: 0)) })
    }
}

@Composable
private fun MappingRow(name: String, m: FseqMapping, totalChannels: Int, onChange: (FseqMapping) -> Unit) {
    var start by remember(m.deviceId, m.startChannel) { mutableStateOf(m.startChannel.toString()) }
    var count by remember(m.deviceId, m.ledCount) { mutableStateOf(m.ledCount.toString()) }
    val outside = m.enabled && m.endChannel > totalChannels
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (!m.enabled) "Not in this sequence" else "Channels ${"%,d".format(m.startChannel)}–${"%,d".format(m.endChannel)}" +
                            if (outside) " · past the end of the sequence" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (outside) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = m.enabled, onCheckedChange = { onChange(m.copy(enabled = it)) })
            }
            if (m.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        start,
                        { v -> start = v.filter(Char::isDigit).take(7); start.toIntOrNull()?.takeIf { it >= 1 }?.let { onChange(m.copy(startChannel = it)) } },
                        label = { Text("Start channel") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        count,
                        { v -> count = v.filter(Char::isDigit).take(5); count.toIntOrNull()?.let { onChange(m.copy(ledCount = it)) } },
                        label = { Text("Pixels") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
