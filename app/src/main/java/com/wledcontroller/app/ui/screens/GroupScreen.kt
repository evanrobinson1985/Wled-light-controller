@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.data.EffectMemory
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.DeviceGroup
import com.wledcontroller.app.data.WledColor
import com.wledcontroller.app.data.isRealName
import com.wledcontroller.app.data.profileFor
import com.wledcontroller.app.ui.components.ColorPickerPanel
import com.wledcontroller.app.ui.components.ColorSwatch
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.HolidayThemesSection
import com.wledcontroller.app.ui.components.QuickColors
import com.wledcontroller.app.ui.components.SectionTitle
import com.wledcontroller.app.ui.components.toComposeColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GroupScreen(groupId: String, onBack: () -> Unit, onOpenDevice: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val groups by container.store.groups.collectAsStateWithLifecycle(null)
    val all by container.devices.list.collectAsStateWithLifecycle()
    val group = groups?.firstOrNull { it.id == groupId }
    var editing by remember { mutableStateOf<DeviceGroup?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val members = all.filter { group != null && it.id in group.deviceIds }
    val snaps = members.map { it.snapshot.collectAsStateWithLifecycle().value }
    val online = members.zip(snaps).filter { it.second.online }
    /** Remembers each controller's settings for the effect it's playing, once the sliders rest. */
    fun rememberSettings() = online.forEach { (c, _) ->
        c.rememberEffectSettings { fx, values -> container.store.saveEffectSettings(c.id, fx, values) }
    }
    val lead = online.firstOrNull()?.second
    val anyOn = online.any { it.second.state?.on == true }
    val avgBri = online.mapNotNull { it.second.state?.bri }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 128f
    var effectQuery by rememberSaveable { mutableStateOf("") }
    var effectSettingsOpen by rememberSaveable { mutableStateOf(true) }
    val effectSettings by container.store.effectSettings.collectAsStateWithLifecycle(emptyMap())
    val profiles by container.store.lightProfiles.collectAsStateWithLifecycle(emptyMap())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (group != null) {
                        IconButton(onClick = { editing = group }) { Icon(Icons.Outlined.Edit, "Edit group") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete group") }
                    }
                    FilledIconToggleButton(
                        checked = anyOn,
                        onCheckedChange = { v -> online.forEach { it.first.setPower(v) } },
                        enabled = online.isNotEmpty(),
                    ) { Icon(Icons.Filled.PowerSettingsNew, "Power") }
                },
            )
        },
    ) { padding ->
        if (group == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (groups == null) "Loading…" else "Group not found")
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Text(
                "${online.size} of ${members.size} devices online · changes apply to all of them",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            GradientSlider(
                value = avgBri / 255f,
                onValueChange = { v -> online.forEach { it.first.setBrightness((v * 255).roundToInt().coerceAtLeast(1)) } },
                label = "Brightness",
                icon = Icons.Filled.Brightness6,
                valueText = "${(avgBri * 100 / 255).roundToInt()}%",
                enabled = online.isNotEmpty(),
            )

            SectionTitle("Colour")
            val leadColor = lead?.state?.activeSegment?.color(0) ?: WledColor(255, 160, 0)
            ColorPickerPanel(
                color = leadColor.rgb,
                onColorChange = { rgb ->
                    online.forEach { (c, s) ->
                        // Coming from a pure white, the white LED goes off (or the colour would still look white).
                        val cur = s.state?.activeSegment?.color(0)
                        val w = if (cur == null || (cur.r == 0 && cur.g == 0 && cur.b == 0)) 0 else cur.w
                        c.setColor(0, WledColor.fromRgb(rgb, w))
                    }
                },
                onCommit = { rgb -> scope.launch { container.store.addRecentColor(rgb) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickColors.forEach { c ->
                    ColorSwatch(c.toComposeColor(), onClick = { online.forEach { (d, _) -> d.setColor(0, WledColor.fromRgb(c)) } })
                }
            }

            Spacer(Modifier.height(16.dp))
            HolidayThemesSection(
                availableEffects = lead?.effects ?: emptyList(),
                // "Keep" names the effect when every light is playing the same one.
                currentEffect = online.map { (_, sn) -> sn.state?.activeSegment?.let { sn.effects.getOrNull(it.fx) } }.distinct().singleOrNull(),
                onApply = { colors, effect, theme ->
                    online.forEach { (c, s) ->
                        val p = profiles.profileFor(c.id, s.state?.activeSegment?.id)
                        val bands = theme.bands || p?.preferBands == true
                        c.applyTheme(colors, effect, bands, (theme.speed * (p?.speedScale ?: 1f)).toInt(), theme.intensity)
                    }
                },
            )

            if (lead != null) {
                val seg = lead.state?.activeSegment
                SectionTitle("Effect")
                SearchField(effectQuery, { effectQuery = it }, "Search effects")
                Spacer(Modifier.height(8.dp))
                val names = lead.effects.filter(::isRealName)
                    .filter { effectQuery.isBlank() || it.contains(effectQuery.trim(), true) }
                    .sortedWith(compareBy<String>({ it != "Solid" }, { it.lowercase() }))
                val currentName = seg?.let { lead.effects.getOrNull(it.fx) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.take(if (effectQuery.isBlank()) 40 else 200).forEach { name ->
                        val playing = name == currentName
                        SelectableRow(selected = playing, onClick = {
                            if (playing) effectSettingsOpen = !effectSettingsOpen
                            else {
                                // Effect ids can differ between firmware versions, so match by name.
                                // Each with the settings it had last time on that controller.
                                online.forEach { (c, s) ->
                                    s.effects.indexOf(name).takeIf { it >= 0 }?.let { fx -> c.playEffect(fx, effectSettings[EffectMemory.key(c.id, fx)]) }
                                }
                                effectSettingsOpen = true
                            }
                        }) {
                            Text(name, Modifier.weight(1f))
                            if (playing) Icon(if (effectSettingsOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.padding(end = 8.dp))
                        }
                        // The playing effect's settings, right under it.
                        AnimatedVisibility(visible = playing && effectSettingsOpen) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.padding(bottom = 4.dp),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GradientSlider(
                                        value = (seg?.sx ?: 128) / 255f,
                                        onValueChange = { v -> online.forEach { it.first.setSegValue("sx", (v * 255).roundToInt()) }; rememberSettings() },
                                        label = "Speed", height = 34.dp,
                                    )
                                    GradientSlider(
                                        value = (seg?.ix ?: 128) / 255f,
                                        onValueChange = { v -> online.forEach { it.first.setSegValue("ix", (v * 255).roundToInt()) }; rememberSettings() },
                                        label = "Intensity", height = 34.dp,
                                    )
                                }
                            }
                        }
                    }
                    if (effectQuery.isBlank() && names.size > 40) {
                        Text("Search to see all ${names.size} effects", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionTitle("Devices")
            members.zip(snaps).forEach { (c, s) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(s.conn)
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = { onOpenDevice(c.id) }, modifier = Modifier.weight(1f)) {
                        Text(c.device.name, Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
                    }
                    Switch(checked = s.online && s.state?.on == true, onCheckedChange = { c.setPower(it) }, enabled = s.online)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    editing?.let { g -> GroupEditorDialog(g, all, onDismiss = { editing = null }) }
    if (confirmDelete && group != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete group?") },
            text = { Text("\"${group.name}\" will be removed. The devices stay in the app.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onBack()
                    scope.launch { container.store.removeGroup(group.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Full WLED web UI for settings the native screens don't cover (Wi-Fi, LED hardware, usermods …). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebUiScreen(deviceId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val controller = container.devices.get(deviceId)
    var webView by remember { mutableStateOf<WebView?>(null) }
    val url = controller?.let { container.api.baseUrl(it.apiHost) + "/" }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(controller?.device?.name ?: "WLED") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Close") } },
                actions = { IconButton(onClick = { webView?.reload() }) { Icon(Icons.Filled.Refresh, "Reload") } },
            )
        },
    ) { padding ->
        if (url == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Device not found") }
        } else if (com.wledcontroller.app.sim.SimHub.isSimulated(controller?.device?.host)) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("This is a simulated controller, so it has no WLED web page. Everything it does is in the app's own screens.")
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl(url)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }
}
