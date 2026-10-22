@file:OptIn(ExperimentalMaterial3Api::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.Firmware
import com.wledcontroller.app.data.isSimulatedHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()

/** Downloads [url] to [to]. */
private fun download(url: String, to: File) {
    http.newCall(Request.Builder().url(url).build()).execute().use { r ->
        require(r.isSuccessful) { "Download failed (${r.code})" }
        to.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
    }
}

/** Sends a firmware file to WLED's update page; the controller installs it and restarts. */
private fun upload(host: String, bin: File) {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("update", bin.name, bin.asRequestBody("application/octet-stream".toMediaType()))
        .build()
    http.newCall(Request.Builder().url("http://${host.trim().removePrefix("http://").trimEnd('/')}/update").post(body).build()).execute().use { r ->
        require(r.isSuccessful) { "The controller refused the update (${r.code})" }
    }
}

/** Every controller's WLED version, and updating them to the newest official release together. */
@Composable
fun FirmwareScreen(onBack: () -> Unit, onOpenWebUi: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val controllers by container.devices.list.collectAsStateWithLifecycle()
    val real = controllers.filter { !isSimulatedHost(it.device.host) }
    var latest by remember { mutableStateOf<Firmware.Release?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }
    // Each controller's build name ("ESP32", "ESP8266" …), from its info.
    val builds = remember { mutableStateMapOf<String, String?>() }
    val status = remember { mutableStateMapOf<String, String>() }
    var confirm by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(real.map { it.device.id }) {
        for (c in real) if (c.device.id !in builds) {
            builds[c.device.id] = runCatching { (container.api.get(c.device.host, "/json/info") as? JsonObject)?.get("release")?.jsonPrimitive?.content }.getOrNull()
        }
    }
    fun check() = scope.launch {
        checkError = null
        latest = withContext(Dispatchers.IO) {
            runCatching { http.newCall(Request.Builder().url(Firmware.LATEST_URL).header("Accept", "application/vnd.github+json").build()).execute().use { it.body?.string() } }
                .getOrNull()?.let(Firmware::parseRelease)
        }
        if (latest == null) checkError = "Couldn't reach GitHub to check for updates."
    }
    LaunchedEffect(Unit) { check() }

    fun update(ids: List<String>) = container.scope.launch {
        val rel = latest ?: return@launch
        for (id in ids) {
            val c = real.firstOrNull { it.device.id == id } ?: continue
            val asset = Firmware.assetFor(rel, builds[id])
            if (asset == null) { status[id] = "No matching file — use its own update page."; continue }
            status[id] = "Downloading ${asset.name}…"
            runCatching {
                withContext(Dispatchers.IO) {
                    val f = File(context.cacheDir, asset.name)
                    download(asset.url, f)
                    status[id] = "Installing — don't unplug it…"
                    upload(c.device.host, f)
                    f.delete()
                }
            }.onSuccess { status[id] = "Updated to ${rel.version}. It restarts by itself." }
                .onFailure { status[id] = "Update failed: ${it.message}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controller updates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                latest?.let { "Newest WLED: ${it.version}" } ?: checkError ?: "Checking for the newest WLED…",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Updates install the official WLED file that matches each controller. Keep them powered until they restart. Your settings stay. Custom builds aren't touched — use their own update page.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val rel = latest
            val outdated = real.filter { c -> val v = c.snapshot.value.info?.version; rel != null && v != null && Firmware.isNewer(rel.version, v) && Firmware.assetFor(rel, builds[c.device.id]) != null }
            if (outdated.isNotEmpty()) Button(onClick = { confirm = outdated.map { it.device.id } }, modifier = Modifier.fillMaxWidth()) { Text("Update all ${outdated.size}") }
            real.forEach { c ->
                val snap by c.snapshot.collectAsStateWithLifecycle()
                val v = snap.info?.version
                val newer = rel != null && v != null && Firmware.isNewer(rel.version, v)
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.device.name, fontWeight = FontWeight.SemiBold)
                                Text("WLED ${v ?: "?"} · ${builds[c.device.id] ?: snap.info?.arch ?: "?"}" + if (newer) " · update available" else if (v != null && rel != null) " · up to date" else "",
                                    style = MaterialTheme.typography.labelSmall, color = if (newer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (newer && rel != null && Firmware.assetFor(rel, builds[c.device.id]) != null) OutlinedButton(onClick = { confirm = listOf(c.device.id) }) { Text("Update") }
                            else if (newer) TextButton(onClick = { onOpenWebUi(c.device.id) }) { Text("Its update page") }
                        }
                        status[c.device.id]?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            if (real.isEmpty()) Text("No controllers to update.")
            Spacer(Modifier.height(24.dp))
        }
    }

    confirm?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Update ${if (ids.size == 1) "this controller" else "${ids.size} controllers"}?") },
            text = { Text("Each downloads WLED ${latest?.version}, installs it and restarts (about a minute). Don't unplug them meanwhile. Best done when no show is running.") },
            confirmButton = { TextButton(onClick = { confirm = null; update(ids) }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
