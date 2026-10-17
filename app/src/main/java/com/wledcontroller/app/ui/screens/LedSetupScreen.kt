@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wledcontroller.app.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wledcontroller.app.LocalAppContainer
import com.wledcontroller.app.data.DeviceController
import com.wledcontroller.app.data.LightProfile
import com.wledcontroller.app.data.WledColor
import com.wledcontroller.app.data.arr
import com.wledcontroller.app.data.asInt
import com.wledcontroller.app.data.bool
import com.wledcontroller.app.data.int
import com.wledcontroller.app.data.obj
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** WLED bus type codes (const.h) for the chips people actually buy. */
private val LedTypes = listOf(
    22 to "WS281x — WS2812B / WS2811",
    24 to "WS2811 400 kHz",
    30 to "SK6812 / WS2814 RGBW",
    31 to "TM1814 RGBW",
    27 to "APA106 / PL9823",
    25 to "TM1829",
    26 to "UCS8903",
    29 to "UCS8904 RGBW",
    28 to "FW1906 GRBCW",
    32 to "WS2805 RGBCW",
    33 to "TM1914",
    50 to "WS2801 (data + clock)",
    51 to "APA102 / SK9822 (data + clock)",
    52 to "LPD8806 (data + clock)",
    53 to "P9813 (data + clock)",
    54 to "LPD6803 (data + clock)",
)

private val ColorOrders = listOf("GRB", "RGB", "BRG", "RBG", "BGR", "GBR")

private fun typeLabel(type: Int) = LedTypes.firstOrNull { it.first == type }?.second ?: "Type $type"

/** One LED output being edited. Unknown fields of [raw] are sent back untouched. */
private class OutputEdit(val raw: JsonObject) {
    var type by mutableIntStateOf(raw.int("type", 22))
    var count by mutableStateOf(raw.int("len", 30).toString())
    var start by mutableStateOf(raw.int("start").toString())
    var pins by mutableStateOf(raw.arr("pin")?.joinToString(", ") { it.asInt().toString() } ?: "2")
    var order by mutableIntStateOf(raw.int("order") and 0x0F)
    var reverse by mutableStateOf(raw.bool("rev"))
    var skip by mutableStateOf(raw.int("skip").toString())

    val pinList get() = pins.split(',', ' ').mapNotNull { it.trim().toIntOrNull() }
    val valid get() = (count.toIntOrNull() ?: 0) in 1..8192 && start.toIntOrNull() != null &&
        pinList.isNotEmpty() && pinList.all { it in 0..48 } && (skip.toIntOrNull() ?: -1) >= 0

    fun toJson(): JsonObject {
        val m = raw.toMutableMap()
        m["type"] = JsonPrimitive(type)
        m["len"] = JsonPrimitive(count.toInt())
        m["start"] = JsonPrimitive(start.toInt())
        m["pin"] = JsonArray(pinList.map { JsonPrimitive(it) })
        // Upper bits of "order" hold the white-channel swap on newer firmware: keep them.
        m["order"] = JsonPrimitive((raw.int("order") and 0xF0) or order)
        m["rev"] = JsonPrimitive(reverse)
        m["skip"] = JsonPrimitive(skip.toInt())
        return JsonObject(m)
    }
}

@Composable
fun LedSetupScreen(deviceId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val controller = container.devices.get(deviceId)
    if (controller == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("LED setup") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        }) { p -> Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Device not found") } }
        return
    }
    LedSetupContent(controller, onBack)
}

@Composable
private fun LedSetupContent(controller: DeviceController, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val profiles by container.store.lightProfiles.collectAsStateWithLifecycle(emptyMap())
    val deviceProfile = LightProfile.parse(profiles[LightProfile.deviceKey(controller.id)])

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var ledConfig by remember { mutableStateOf<JsonObject?>(null) }
    val outputs = remember { mutableStateListOf<OutputEdit>() }
    var maxPower by remember { mutableStateOf("") }
    var maPerLed by remember { mutableStateOf("") }
    var confirmSave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true; error = null
        try {
            val led = container.api.config(controller.device.host).obj("hw")?.obj("led")
                ?: throw IllegalStateException("This firmware doesn't expose its LED configuration.")
            ledConfig = led
            outputs.clear()
            led.arr("ins")?.forEach { (it as? JsonObject)?.let { o -> outputs.add(OutputEdit(o)) } }
            maxPower = led.int("maxpwr").toString()
            maPerLed = led.int("ledma", 55).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't read the configuration."
        }
        loading = false
    }

    val canSave = !loading && ledConfig != null && outputs.isNotEmpty() && outputs.all { it.valid } &&
        maxPower.toIntOrNull() != null && maPerLed.toIntOrNull() != null && !saving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("LED setup"); Text(controller.device.name, style = MaterialTheme.typography.labelMedium) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Button(onClick = { confirmSave = true }, enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (saving) "Saving…" else "Save & reboot")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupCard("Light type") {
                Text(
                    "Tunes effect suggestions, speeds and holiday colours for this device. You can also set it per segment in the Segments tab.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LightProfilePicker(deviceProfile, inheritLabel = null) { p ->
                    scope.launch { container.store.setLightProfile(LightProfile.deviceKey(controller.id), p) }
                }
                deviceProfile?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it.description, style = MaterialTheme.typography.bodySmall)
                }
            }

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> SetupCard("Couldn't load LED settings") {
                    Text(error ?: "", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { reloadKey++ }) { Text("Try again") }
                }
                else -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(16.dp)) {
                            Icon(Icons.Outlined.WarningAmber, null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "These settings must match your wiring. Most bullet / pixel strings are WS2811 with RGB order; most strips are WS2812B with GRB order. If something goes wrong you can always fix it from the WLED web interface.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    outputs.forEachIndexed { i, out ->
                        OutputCard(i, out, canRemove = outputs.size > 1, onRemove = { outputs.removeAt(i) })
                    }
                    OutlinedButton(
                        onClick = {
                            val last = outputs.lastOrNull()
                            val nextStart = last?.let { (it.start.toIntOrNull() ?: 0) + (it.count.toIntOrNull() ?: 0) } ?: 0
                            outputs.add(OutputEdit(buildJsonObject {
                                put("type", 22); put("len", 50); put("start", nextStart)
                                put("pin", JsonArray(listOf(JsonPrimitive(-1))))
                                put("order", 0); put("rev", false); put("skip", 0)
                            }).apply { pins = "" })
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add output") }

                    SetupCard("Power limit") {
                        Text(
                            "WLED dims the lights automatically to stay under your power supply's limit. 0 turns the limiter off.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SetupNumberField("Max current (mA)", maxPower, { maxPower = it }, Modifier.weight(1f))
                            SetupNumberField("mA per LED", maPerLed, { maPerLed = it }, Modifier.weight(1f))
                        }
                        Text(
                            "Typical: 55 mA for 5 V strips, 15–20 mA for 12 V bullets.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SetupCard("Check colour order") {
                        Text(
                            "Tap a colour. If the lights show a different one, change the colour order above.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Red" to WledColor(255, 0, 0), "Green" to WledColor(0, 255, 0), "Blue" to WledColor(0, 0, 255)).forEach { (label, c) ->
                                FilledTonalButton(
                                    onClick = { controller.setEffect(0); controller.setColor(0, c) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(Modifier.size(12.dp).padding(0.dp)) {
                                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawCircle(Color(c.rgb)) }
                                    }
                                    Spacer(Modifier.width(6.dp)); Text(label)
                                }
                            }
                        }
                    }
                    val total = outputs.sumOf { it.count.toIntOrNull() ?: 0 }
                    Text("Total: $total LEDs", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }

    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Save and reboot?") },
            text = { Text("${controller.device.name} will restart with the new LED settings. Lights go dark for a few seconds.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSave = false
                    saving = true
                    scope.launch {
                        try {
                            val body = buildJsonObject {
                                putJsonObject("hw") {
                                    putJsonObject("led") {
                                        put("maxpwr", maxPower.toInt())
                                        put("ledma", maPerLed.toInt())
                                        put("ins", JsonArray(outputs.map { it.toJson() }))
                                    }
                                }
                            }
                            container.api.postConfig(controller.device.host, body)
                            delay(600)
                            controller.reboot()
                            delay(500)
                            onBack()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "Saving failed: ${e.message}. If the device has a settings PIN, unlock it in the WLED web interface first."
                        } finally {
                            saving = false
                        }
                    }
                }) { Text("Save & reboot") }
            },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OutputCard(index: Int, out: OutputEdit, canRemove: Boolean, onRemove: () -> Unit) {
    var typeMenu by remember { mutableStateOf(false) }
    val clockType = out.type in 48..63
    SetupCard("Output ${index + 1}", trailing = {
        if (canRemove) IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Remove output") }
    }) {
        Text("LED chip", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(typeLabel(out.type), Modifier.weight(1f)); Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                LedTypes.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { out.type = code; typeMenu = false })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetupNumberField("LED count", out.count, { out.count = it }, Modifier.weight(1f))
            SetupNumberField("Start index", out.start, { out.start = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = out.pins,
                onValueChange = { out.pins = it.filter { c -> c.isDigit() || c == ',' || c == ' ' }.take(12) },
                label = { Text(if (clockType) "Data, clock pins" else "Data pin") },
                singleLine = true,
                isError = out.pinList.isEmpty(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
            SetupNumberField("Skip first", out.skip, { out.skip = it }, Modifier.weight(1f))
        }
        Text("Colour order", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorOrders.forEachIndexed { i, label ->
                FilterChip(selected = out.order == i, onClick = { out.order = i }, label = { Text(label) })
            }
        }
        Text("Controller position", style = MaterialTheme.typography.labelLarge)
        // The first LED sits next to the controller. With the controller on the right, WLED
        // reverses the output so animations still run left → right as you face the lights.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !out.reverse,
                onClick = { out.reverse = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Left end") }
            SegmentedButton(
                selected = out.reverse,
                onClick = { out.reverse = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Right end") }
        }
        ControllerDiagram(controllerOnRight = out.reverse)
        Text(
            "Looking at the lights, which end is the controller plugged into? Effects will then run left → right.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Row of bulbs with the controller box at one end and an arrow showing the effect direction. */
@Composable
private fun ControllerDiagram(controllerOnRight: Boolean) {
    val box = MaterialTheme.colorScheme.primary
    val wire = MaterialTheme.colorScheme.outline
    val bulb = MaterialTheme.colorScheme.tertiary
    val arrow = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(vertical = 4.dp)
    ) {
        val h = size.height
        val boxW = 28.dp.toPx()
        val boxX = if (controllerOnRight) size.width - boxW else 0f
        val lineY = h * 0.35f
        val startX = if (controllerOnRight) 0f else boxW
        val endX = if (controllerOnRight) size.width - boxW else size.width
        drawLine(wire, androidx.compose.ui.geometry.Offset(startX, lineY), androidx.compose.ui.geometry.Offset(endX, lineY), 2.dp.toPx())
        drawRoundRect(
            box,
            topLeft = androidx.compose.ui.geometry.Offset(boxX, lineY - 9.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(boxW, 18.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        )
        val n = 9
        val gap = (endX - startX) / (n + 1)
        for (i in 1..n) drawCircle(bulb, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(startX + gap * i, lineY))
        // Effect direction: always left → right.
        val ay = h * 0.85f
        val ax0 = size.width * 0.3f
        val ax1 = size.width * 0.7f
        drawLine(arrow, androidx.compose.ui.geometry.Offset(ax0, ay), androidx.compose.ui.geometry.Offset(ax1, ay), 1.5.dp.toPx())
        drawLine(arrow, androidx.compose.ui.geometry.Offset(ax1, ay), androidx.compose.ui.geometry.Offset(ax1 - 6.dp.toPx(), ay - 4.dp.toPx()), 1.5.dp.toPx())
        drawLine(arrow, androidx.compose.ui.geometry.Offset(ax1, ay), androidx.compose.ui.geometry.Offset(ax1 - 6.dp.toPx(), ay + 4.dp.toPx()), 1.5.dp.toPx())
    }
}

@Composable
private fun SetupCard(title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                trailing()
            }
            content()
        }
    }
}

@Composable
private fun SetupNumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(5)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        isError = value.toIntOrNull() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/** Chips for choosing a light type. [inheritLabel] non-null adds a "Same as device" option. */
@Composable
fun LightProfilePicker(current: LightProfile?, inheritLabel: String?, onPick: (LightProfile?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (inheritLabel != null) {
            FilterChip(selected = current == null, onClick = { onPick(null) }, label = { Text(inheritLabel) })
        }
        LightProfile.entries.forEach { p ->
            FilterChip(selected = current == p, onClick = { onPick(p) }, label = { Text(p.label) })
        }
    }
}
