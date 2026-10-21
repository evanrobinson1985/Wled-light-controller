@file:OptIn(ExperimentalMaterial3Api::class)

package com.wledcontroller.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.Port
import com.wledcontroller.app.house.PortMap
import com.wledcontroller.app.house.PortSpan
import com.wledcontroller.app.ui.components.SectionTitle

private val RunColors = listOf(0xFF4FC3F7, 0xFFFFB74D, 0xFF81C784, 0xFFE57373, 0xFFBA68C8, 0xFFFFF176, 0xFF4DB6AC, 0xFFF06292).map { Color(it) }

/**
 * Every controller with its props laid out along its LEDs and outputs, like xLights' controller
 * visualiser: what's wired where, unused LEDs, props sharing LEDs and props past the end.
 */
@Composable
fun ControllerMapScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val controllers by container.devices.list.collectAsStateWithLifecycle()
    val outputs by container.store.outputs.collectAsStateWithLifecycle(emptyList())
    // Each WLED controller's outputs, read from its config.
    val ports = remember { mutableStateMapOf<String, List<Port>>() }
    LaunchedEffect(controllers) {
        for (c in controllers) {
            if (c.device.id in ports) continue
            val cfg = runCatching { container.api.config(c.device.host) }.getOrNull()
            ports[c.device.id] = PortMap.ports(cfg, c.snapshot.value.info?.ledCount ?: 0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controller map") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Each controller's LEDs from first to last, with the props on them and its outputs. Grey is unused, red is a problem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            controllers.forEach { c ->
                val snap by c.snapshot.collectAsStateWithLifecycle()
                val count = snap.info?.ledCount ?: 0
                ControllerCard(c.device.name, c.device.host, count, map.runs.filter { it.deviceId == c.device.id }, ports[c.device.id] ?: PortMap.ports(null, count))
            }
            outputs.forEach { o ->
                val perUniverse = (o.channelsPerUniverse / 3).coerceAtLeast(1)
                val universes = List(o.universes) { u -> Port(u * perUniverse, minOf(perUniverse, o.ledCount - u * perUniverse), "Universe ${o.startUniverse + u}") }
                ControllerCard(o.name, "${o.protocol.name} · ${o.host}", o.ledCount, map.runs.filter { it.deviceId == o.id }, universes)
            }
            val orphans = map.runs.filter { r -> controllers.none { it.device.id == r.deviceId } && outputs.none { it.id == r.deviceId } }
            if (orphans.isNotEmpty()) {
                SectionTitle("Props without a controller")
                orphans.forEach { Text("${it.name} · ${it.count} LEDs", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ControllerCard(name: String, address: String, ledCount: Int, runs: List<com.wledcontroller.app.house.LightRun>, ports: List<Port>) {
    val spans = remember(runs, ledCount) { PortMap.spans(runs, ledCount) }
    val total = maxOf(ledCount, runs.maxOfOrNull { it.endLed } ?: 0).coerceAtLeast(1)
    val colorOf = runs.mapIndexed { i, r -> r.name to RunColors[i % RunColors.size] }.toMap()
    val unused = MaterialTheme.colorScheme.surfaceContainerHighest
    val problem = MaterialTheme.colorScheme.error
    val portLine = MaterialTheme.colorScheme.onSurface
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Text("$address · " + (if (ledCount > 0) "$ledCount LEDs" else "LED count unknown (not reached yet)"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val used = runs.sumOf { it.count }
                if (ledCount > 0) Text("${(used * 100 / ledCount).coerceAtMost(999)}% used", style = MaterialTheme.typography.labelMedium)
            }
            // The LEDs as a bar: props in colour, gaps grey, problems red; outputs marked underneath.
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val barH = size.height * 0.62f
                fun x(led: Int) = led.toFloat() / total * size.width
                for (s in spans) {
                    val col = when (s.kind) {
                        PortSpan.Kind.PROP -> colorOf[s.names.first()] ?: Color.Gray
                        PortSpan.Kind.UNUSED -> unused
                        PortSpan.Kind.OVERLAP, PortSpan.Kind.PAST_END -> problem
                    }
                    drawRoundRect(col, Offset(x(s.start), 0f), Size((x(s.end) - x(s.start) - 1f).coerceAtLeast(1f), barH), CornerRadius(4f))
                }
                for (p in ports) {
                    drawLine(portLine, Offset(x(p.start), barH + 3f), Offset(x(p.end) - 2f, barH + 3f), 2.dp.toPx())
                    drawLine(portLine, Offset(x(p.start), barH), Offset(x(p.start), size.height), 1.dp.toPx())
                }
            }
            ports.forEach { p ->
                val on = runs.filter { PortMap.portOf(it, ports) == p }.sortedBy { it.startLed }
                Text("${p.label}: LEDs ${p.start}–${p.end - 1}", style = MaterialTheme.typography.labelLarge)
                if (on.isEmpty()) Text("  Nothing mapped here.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                on.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Canvas(Modifier.size(10.dp)) { drawCircle(colorOf[r.name] ?: Color.Gray) }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${r.name} · LEDs ${r.startLed}–${r.endLed - 1} (${r.count})" + if (PortMap.crossesPorts(r, ports)) " · runs on into the next output" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            spans.filter { it.kind == PortSpan.Kind.OVERLAP }.forEach {
                Text("LEDs ${it.start}–${it.end - 1} are used by ${it.names.joinToString(" and ")}.", style = MaterialTheme.typography.labelSmall, color = problem)
            }
            spans.filter { it.kind == PortSpan.Kind.PAST_END }.forEach {
                Text("${it.names.joinToString()} runs ${it.count} LEDs past the controller's last LED.", style = MaterialTheme.typography.labelSmall, color = problem)
            }
        }
    }
}
