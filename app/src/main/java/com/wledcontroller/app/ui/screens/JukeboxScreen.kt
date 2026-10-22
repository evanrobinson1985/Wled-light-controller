@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.show.Jukebox
import com.wledcontroller.app.show.JukeboxServer
import com.wledcontroller.app.show.ShowRunnerService
import com.wledcontroller.app.ui.components.SectionTitle
import kotlinx.coroutines.delay

/** A QR code for [text] as a black-on-white bitmap [size] pixels square. */
internal fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

@Suppress("DEPRECATION")
private fun hotspotNameAndPassword(r: WifiManager.LocalOnlyHotspotReservation): Pair<String, String?> =
    if (Build.VERSION.SDK_INT >= 30) {
        val c = r.softApConfiguration
        val name = if (Build.VERSION.SDK_INT >= 33) c.wifiSsid?.toString()?.trim('"') else c.ssid
        (name ?: "Lights") to c.passphrase
    } else {
        val c = r.wifiConfiguration
        (c?.SSID?.trim('"') ?: "Lights") to c?.preSharedKey?.trim('"')
    }

/** The phone's own address on the Wi-Fi it's joined, or null. */
@Suppress("DEPRECATION")
private fun wifiAddress(context: Context): String? {
    val ip = context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ipAddress ?: return null
    if (ip == 0) return null
    return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
}

/** The text a phone's camera reads to join a Wi-Fi network. */
internal fun wifiQrText(ssid: String, password: String?): String {
    fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
    return if (password.isNullOrEmpty()) "WIFI:T:nopass;S:${esc(ssid)};;" else "WIFI:T:WPA;S:${esc(ssid)};P:${esc(password)};;"
}

/** Saves a printable sign with both QR codes to Pictures › WLED Controller. Returns where. */
private fun saveSign(context: Context, title: String, wifi: Pair<String, String?>?, url: String): String? {
    val w = 1240; val h = 1754
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFF0B0D12.toInt())
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 84f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    val mid = Paint(big).apply { textSize = 52f; isFakeBoldText = false }
    val small = Paint(big).apply { textSize = 40f; isFakeBoldText = false; color = 0xFFBBC4D6.toInt() }
    c.drawText(title, w / 2f, 150f, big)
    c.drawText("Pick the next show — and hear it on your phone!", w / 2f, 230f, mid)
    var y = 300f
    if (wifi != null) {
        c.drawText("1. Point your camera here to join the Wi-Fi", w / 2f, y + 40f, mid)
        c.drawBitmap(qrBitmap(wifiQrText(wifi.first, wifi.second), 520), (w - 520) / 2f, y + 70f, null)
        c.drawText("Wi-Fi: ${wifi.first}" + (wifi.second?.let { "   Password: $it" } ?: ""), w / 2f, y + 640f, small)
        y += 700f
        c.drawText("2. Then scan this to choose a show", w / 2f, y + 40f, mid)
    } else {
        c.drawText("Scan to choose a show", w / 2f, y + 40f, mid)
    }
    c.drawBitmap(qrBitmap(url, 520), (w - 520) / 2f, y + 70f, null)
    c.drawText(url, w / 2f, y + 640f, small)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Light show sign.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WLED Controller")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return "Pictures › WLED Controller"
}

/**
 * The viewer jukebox (like Remote Falcon): visitors scan a QR code on a sign, pick or vote for
 * the next show from their phones, and can listen to the song on their phones in time with the
 * lights. The phone can make its own hotspot for them, or use a guest Wi-Fi.
 */
@Composable
fun JukeboxScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val jukebox = container.jukebox
    val server = container.jukeboxServer
    val shows by container.sequences.all.collectAsStateWithLifecycle()
    val runner by container.showRunner.state.collectAsStateWithLifecycle()
    val running = runner.running && runner.mode == "jukebox"

    val chosen = remember { mutableStateListOf<String>().apply { addAll(jukebox.songs.map { it.id }) } }
    LaunchedEffect(shows) { if (chosen.isEmpty()) chosen.addAll(shows.filter { it.audioFile != null && it.tracks.isNotEmpty() || it.fseqFile != null }.map { it.id }) }
    var mode by remember { mutableStateOf(jukebox.mode) }
    var idle by remember { mutableStateOf(jukebox.playWhenIdle) }
    var cooldown by remember { mutableIntStateOf((jukebox.cooldownMs / 1000).toInt()) }
    var title by remember { mutableStateOf(container.jukeboxTitle) }
    var useHotspot by remember { mutableStateOf(true) }
    var hotspot by remember { mutableStateOf<WifiManager.LocalOnlyHotspotReservation?>(null) }
    var hotspotInfo by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) { while (running) { delay(2000); tick++ } }

    fun startHotspot() {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        runCatching {
            wm.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    hotspot = reservation
                    hotspotInfo = hotspotNameAndPassword(reservation)
                }
                override fun onFailed(reason: Int) { error = "The phone couldn't start a hotspot (it may need Location turned on, or another hotspot is on). Use your Wi-Fi instead." }
            }, Handler(Looper.getMainLooper()))
        }.onFailure { error = "The phone couldn't start a hotspot: ${it.message}" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startHotspot() else error = "Without that permission the phone can't make a hotspot. Use your Wi-Fi instead."
    }

    fun start() {
        error = null
        jukebox.songs = shows.filter { it.id in chosen }.map { Jukebox.Song(it.id, it.name, it.durationMs, it.audioFile != null) }
        if (jukebox.songs.isEmpty()) { error = "Choose at least one show."; return }
        jukebox.mode = mode; jukebox.playWhenIdle = idle; jukebox.cooldownMs = cooldown * 1000L
        container.jukeboxTitle = title.ifBlank { "Light show" }
        jukebox.clear()
        runCatching { server.start() }.onFailure { error = "Couldn't start the web page: ${it.message}"; return }
        if (useHotspot && hotspot == null) {
            permission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ShowRunnerService.startJukebox(context)
    }

    fun stop() {
        ShowRunnerService.stop(context)
        server.stop()
        hotspot?.close(); hotspot = null; hotspotInfo = null
    }

    val addresses = remember(tick, hotspotInfo, running) { JukeboxServer.localAddresses() }
    // On the hotspot, the phone's address there; otherwise its address on the Wi-Fi.
    val home = remember(tick) { wifiAddress(context) }
    val address = if (hotspotInfo != null) addresses.firstOrNull { it != home } ?: home else home ?: addresses.firstOrNull()
    val url = address?.let { "http://$it:${server.port}" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viewer jukebox") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Visitors scan a QR code on a sign, pick (or vote for) the next show on their phones, and can listen to the song on their phones in time with the lights.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (running && url != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Now playing: " + (runner.nowPlaying ?: "—"), fontWeight = FontWeight.SemiBold)
                        val coming = remember(tick) { if (jukebox.mode == Jukebox.Mode.VOTES) jukebox.voteCounts().entries.joinToString { (id, n) -> "${jukebox.songs.firstOrNull { it.id == id }?.title} ($n)" } else jukebox.queued().joinToString { it.title } }
                        Text((if (jukebox.mode == Jukebox.Mode.VOTES) "Votes: " else "Coming up: ") + coming.ifEmpty { "nothing yet" }, style = MaterialTheme.typography.bodySmall)
                        Text("${server.listeners} phones on the page", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            hotspotInfo?.let { wifi ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("1. Join Wi-Fi", style = MaterialTheme.typography.labelMedium)
                                    val bmp = remember(wifi) { qrBitmap(wifiQrText(wifi.first, wifi.second), 400).asImageBitmap() }
                                    Image(bmp, "Wi-Fi QR code", Modifier.size(140.dp).background(Color.White))
                                    Text(wifi.first, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(if (hotspotInfo != null) "2. Open the page" else "Scan to pick a show", style = MaterialTheme.typography.labelMedium)
                                val bmp = remember(url) { qrBitmap(url, 400).asImageBitmap() }
                                Image(bmp, "Page QR code", Modifier.size(140.dp).background(Color.White))
                                Text(url, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedButton(onClick = {
                            note = runCatching { saveSign(context, container.jukeboxTitle, hotspotInfo, url) }.getOrNull()
                                ?.let { "Sign saved to $it — print it and put it by the road." } ?: "Couldn't save the sign."
                        }) { Text("Save a printable sign") }
                        note?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Button(onClick = { stop() }, modifier = Modifier.fillMaxWidth()) { Text("Stop the jukebox") }
                if (addresses.size > 1) Text("Other addresses: " + addresses.filter { it != address }.joinToString { "http://$it:${server.port}" }, style = MaterialTheme.typography.labelSmall)
            } else {
                OutlinedTextField(title, { title = it.take(40) }, label = { Text("Name on the page and sign") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SectionTitle("Shows they can pick")
                shows.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.id in chosen, onCheckedChange = { on -> if (on) chosen.add(s.id) else chosen.remove(s.id) })
                        Text(s.name + if (s.audioFile == null && s.fseqFile == null) " (no song)" else "", Modifier.weight(1f))
                    }
                }
                SectionTitle("How it picks")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Jukebox.Mode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                }
                Text(
                    if (mode == Jukebox.Mode.VOTES) "Each show plays the one with the most votes next." else "Shows play in the order they're asked for.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Each phone can pick once every", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30 to "30 s", 60 to "1 min", 180 to "3 min", 300 to "5 min").forEach { (sec, l) ->
                        FilterChip(selected = cooldown == sec, onClick = { cooldown = sec }, label = { Text(l) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep playing shows when nobody picks", Modifier.weight(1f))
                    Switch(checked = idle, onCheckedChange = { idle = it })
                }
                SectionTitle("How visitors connect")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = useHotspot, onClick = { useHotspot = true }, label = { Text("Phone's own hotspot") })
                    FilterChip(selected = !useHotspot, onClick = { useHotspot = false }, label = { Text("My (guest) Wi-Fi") })
                }
                Text(
                    if (useHotspot) "The phone makes a Wi-Fi network just for visitors (no internet). The sign gets a QR code to join it. Keep the phone near the road."
                    else "Visitors join your Wi-Fi (a guest network is best) and scan the page's QR code.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("Start the jukebox") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
