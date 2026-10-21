@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.house.DdpSender
import com.wledcontroller.app.house.TestPattern
import com.wledcontroller.app.ui.components.GradientSlider
import com.wledcontroller.app.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Test mode, as in xLights: runs a test pattern straight on the chosen controllers to check
 * wiring, colour order, LED counts and power. They go back to what they were doing a few
 * seconds after it stops.
 */
@Composable
fun TestLightsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val controllers by container.devices.list.collectAsStateWithLifecycle()
    val chosen = remember { mutableStateListOf<String>() }
    var pattern by remember { mutableStateOf(TestPattern.CHASE) }
    var brightness by remember { mutableFloatStateOf(0.4f) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        // Nothing else may stream to the lights while testing.
        container.house.stop()
        container.player.stopAll()
        withContext(Dispatchers.IO) {
            DdpSender().use { ddp ->
                var frame = 0
                while (isActive) {
                    for (c in controllers) {
                        if (c.device.id !in chosen) continue
                        val count = c.snapshot.value.info?.ledCount?.takeIf { it > 0 } ?: continue
                        runCatching { ddp.send(c.device.host, pattern.frame(count, frame, brightness)) }
                    }
                    frame++
                    delay(1000L / TestPattern.FPS)
                }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test lights") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Check wiring, colour order, LED counts and power on your controllers. Each goes back to what it was doing a few seconds after you stop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("Controllers") {
                TextButton(onClick = {
                    if (chosen.size == controllers.size) chosen.clear() else { chosen.clear(); chosen.addAll(controllers.map { it.device.id }) }
                }) { Text(if (chosen.size == controllers.size && controllers.isNotEmpty()) "None" else "All") }
            }
            if (controllers.isEmpty()) Text("No controllers yet — add some on the home screen.", style = MaterialTheme.typography.bodyMedium)
            controllers.forEach { c ->
                val snap by c.snapshot.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = c.device.id in chosen, onCheckedChange = { on -> if (on) chosen.add(c.device.id) else chosen.remove(c.device.id) })
                    Column(Modifier.weight(1f)) {
                        Text(c.device.name)
                        Text(
                            "${c.device.host} · " + (snap.info?.ledCount?.let { "$it LEDs" } ?: "not reached yet"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Pattern")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TestPattern.entries.forEach { p -> FilterChip(selected = pattern == p, onClick = { pattern = p }, label = { Text(p.label) }) }
            }
            Text(pattern.help, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GradientSlider(brightness, { brightness = it }, label = "Brightness", valueText = "${(brightness * 100).toInt()}%", height = 34.dp)
            Text(
                "Full white on a long run draws a lot of power — start low.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { running = !running }, enabled = running || chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Stop test" else "Start test")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
