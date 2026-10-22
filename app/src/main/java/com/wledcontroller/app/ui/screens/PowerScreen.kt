@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.PowerPlan

private fun amps(a: Float) = "%.1f A".format(a)

/** Power planning for each controller: current, supply size, and where to inject power. */
@Composable
fun PowerScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("power_plan", Context.MODE_PRIVATE) }
    val map by container.store.houseMap.collectAsStateWithLifecycle(HouseMap())
    val controllers by container.devices.list.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Power planner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Estimates from the usual rules of thumb: full white is the most a run can draw; shows average about a third of that. Supplies get 20% headroom. Long runs need power fed in part-way (\"injection\") or the far end fades and turns pink.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            controllers.forEach { c ->
                val snap by c.snapshot.collectAsStateWithLifecycle()
                val id = c.device.id
                var volts by remember(id) { mutableStateOf(PowerPlan.Volts.entries.firstOrNull { it.name == prefs.getString(id, null) } ?: PowerPlan.Volts.V12) }
                val runs = map.runs.filter { it.deviceId == id }
                // LEDs on the controller that no prop covers still draw power.
                val extra = ((snap.info?.ledCount ?: 0) - runs.sumOf { it.count }).coerceAtLeast(0)
                val plan = remember(runs, extra, volts) { PowerPlan.plan(runs, extra, volts) }
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.device.name, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PowerPlan.Volts.entries.forEach { v ->
                                FilterChip(selected = volts == v, onClick = { volts = v; prefs.edit().putString(id, v.name).apply() }, label = { Text(v.label) })
                            }
                        }
                        Text("${plan.leds} pixels · full white ${amps(plan.fullAmps)} · show ${amps(plan.showAmps)}")
                        if (plan.supplies.isNotEmpty()) Text(
                            "Supply: " + (if (plan.supplies.size == 1) "${plan.supplies[0]} W" else "${plan.supplies.size} × ${plan.supplies[0]} W") +
                                " (needs ${plan.wattsNeeded} W at full white with headroom)",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        snap.info?.let { info ->
                            if (info.powerMa > 0) Text(
                                "Right now WLED estimates ${amps(info.powerMa / 1000f)}" +
                                    (if (info.maxPowerMa > 0) ", with its limiter at ${amps(info.maxPowerMa / 1000f)}" else ", with no current limit set"),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (info.maxPowerMa > 0 && info.maxPowerMa < plan.showAmps * 1000) Text(
                                "WLED's current limit is below a typical show's draw, so it will dim the lights.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                            )
                        }
                        plan.runs.forEach { r ->
                            Row {
                                Text(
                                    "• ${r.name}: ${r.leds} px, ${amps(r.fullAmps)} max" +
                                        if (r.injections > 0) " — inject power ${r.injections}× (every ~${volts.injectEvery} px)" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (r.injections > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            if (controllers.isEmpty()) Text("No controllers yet.")
            Spacer(Modifier.height(24.dp))
        }
    }
}
