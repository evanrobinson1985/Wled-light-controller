package com.wledcontroller.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SplitToggleChip
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt
import kotlin.math.sign

class MainActivity : ComponentActivity() {
    private val link by lazy { WatchLink(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WatchApp(link) } }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(link.listener)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(link.listener)
    }
}

@Composable
private fun WatchApp(link: WatchLink) {
    val nav = rememberSwipeDismissableNavController()
    val devices by link.devices.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { link.refreshDevices() }
    fun device(id: String?) = devices.firstOrNull { it.id == id }

    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(link, devices, onOpen = { nav.navigate("device/${it.id}") }) }
        composable("device/{id}") { e ->
            device(e.arguments?.getString("id"))?.let { d ->
                DeviceScreen(link, d, onEffects = { nav.navigate("effects/${d.id}") }, onColours = { nav.navigate("colours/${d.id}") }, onPresets = { nav.navigate("presets/${d.id}") })
            }
        }
        composable("effects/{id}") { e -> device(e.arguments?.getString("id"))?.let { d -> EffectsScreen(link, d, onDone = { nav.popBackStack() }) } }
        composable("colours/{id}") { e -> device(e.arguments?.getString("id"))?.let { d -> ColoursScreen(link, d, onDone = { nav.popBackStack() }) } }
        composable("presets/{id}") { e -> device(e.arguments?.getString("id"))?.let { d -> PresetsScreen(link, d, onDone = { nav.popBackStack() }) } }
    }
}

/** A scrolling list with the time on top and a scroll indicator, as on every Wear OS screen. */
@Composable
private fun WatchList(content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = state) }) {
        ScalingLazyColumn(state = state, modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun HomeScreen(link: WatchLink, devices: List<WatchDevice>, onOpen: (WatchDevice) -> Unit) {
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<String, LightState?>() }
    LaunchedEffect(devices) {
        while (true) {
            devices.forEach { d -> states[d.id] = runCatching { link.state(d) }.getOrNull() }
            delay(5000)
        }
    }
    WatchList {
        item { ListHeader { Text("WLED") } }
        if (devices.isEmpty()) {
            item {
                Text(
                    "Open WLED Controller on your phone to send your controllers here.",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            item { Chip(label = { Text("Check again") }, onClick = { scope.launch { link.refreshDevices() } }) }
        }
        items(devices, key = { it.id }) { d ->
            val st = states[d.id]
            SplitToggleChip(
                checked = st?.on == true,
                onCheckedChange = { on ->
                    st?.let { states[d.id] = it.copy(on = on) }
                    scope.launch { runCatching { link.send(d, buildJsonObject { put("on", on) }) } }
                },
                label = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(
                        when {
                            !states.containsKey(d.id) -> "…"
                            st == null -> "Not reachable"
                            !st.on -> "Off"
                            else -> "${(st.bri * 100 / 255).coerceAtLeast(1)}%"
                        },
                    )
                },
                onClick = { onOpen(d) },
                toggleControl = { Switch(checked = st?.on == true) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (devices.isNotEmpty()) {
            item {
                Chip(
                    label = { Text("All off") },
                    icon = { Icon(Icons.Filled.PowerSettingsNew, null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = {
                        devices.forEach { d -> states[d.id]?.let { states[d.id] = it.copy(on = false) } }
                        scope.launch { devices.forEach { d -> runCatching { link.send(d, buildJsonObject { put("on", false) }) } } }
                    },
                )
            }
        }
    }
}

/** One controller: power, brightness (turn the bezel) and ways to its effects, colours and presets. */
@Composable
private fun DeviceScreen(link: WatchLink, d: WatchDevice, onEffects: () -> Unit, onColours: () -> Unit, onPresets: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<LightState?>(null) }
    var effectName by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var bri by remember { mutableIntStateOf(128) }
    LaunchedEffect(d.id) {
        while (true) {
            runCatching { link.state(d) }
                .onSuccess { s -> state = s; bri = s.bri; failed = false }
                .onFailure { failed = true }
            if (effectName == null) state?.let { s -> effectName = runCatching { link.effects(d).getOrNull(s.fx) }.getOrNull() }
            delay(4000)
        }
    }
    // Brightness changes go out as they happen, at most every 100 ms.
    LaunchedEffect(d.id) {
        snapshotFlow { bri }.drop(1).conflate().collect { b ->
            runCatching { link.send(d, buildJsonObject { put("bri", b); put("on", true) }) }
            delay(100)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(timeText = { TimeText() }) {
        Column(
            Modifier.fillMaxSize()
                .onRotaryScrollEvent { e ->
                    // The bezel sets the brightness.
                    bri = (bri + 8 * e.verticalScrollPixels.sign.toInt()).coerceIn(1, 255)
                    state = state?.copy(on = true)
                    true
                }
                .focusRequester(focus)
                .focusable()
                .padding(top = 28.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.title3)
            val s = state
            if (s == null) {
                if (failed) Text("Not reachable", style = MaterialTheme.typography.caption1) else CircularProgressIndicator(Modifier.size(28.dp))
                return@Column
            }
            Button(
                onClick = {
                    val on = !s.on
                    state = s.copy(on = on)
                    scope.launch { runCatching { link.send(d, buildJsonObject { put("on", on) }) } }
                },
                colors = if (s.on) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(ButtonDefaults.DefaultButtonSize),
            ) { Icon(Icons.Filled.PowerSettingsNew, if (s.on) "Turn off" else "Turn on") }
            InlineSlider(
                value = bri.toFloat(),
                onValueChange = { bri = it.roundToInt().coerceIn(1, 255); state = state?.copy(on = true) },
                valueRange = 1f..255f,
                steps = 15,
                increaseIcon = { Icon(Icons.Filled.Add, "Brighter") },
                decreaseIcon = { Icon(Icons.Filled.Remove, "Dimmer") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${(bri * 100 / 255).coerceAtLeast(1)}%" + (effectName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.caption2, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallAction(Icons.Filled.AutoAwesome, "Effects", onEffects)
                SmallAction(Icons.Filled.Palette, "Colours", onColours)
                SmallAction(Icons.Filled.Bookmarks, "Presets", onPresets)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.size(ButtonDefaults.SmallButtonSize)) {
        Icon(icon, label)
    }
}

/** Every effect, favourites (from the phone) first; tapping one plays it. */
@Composable
private fun EffectsScreen(link: WatchLink, d: WatchDevice, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val favorites by link.favorites.collectAsStateWithLifecycle()
    var names by remember { mutableStateOf<List<String>?>(null) }
    var current by remember { mutableIntStateOf(-1) }
    LaunchedEffect(d.id) {
        names = runCatching { link.effects(d) }.getOrNull() ?: emptyList()
        current = runCatching { link.state(d).fx }.getOrDefault(-1)
    }
    WatchList {
        item { ListHeader { Text("Effects") } }
        val list = names
        if (list == null) { item { CircularProgressIndicator(Modifier.size(28.dp)) }; return@WatchList }
        // WLED marks unused slots "RSVD" or "-".
        val shown = list.withIndex().filter { (_, n) -> n.isNotBlank() && n != "RSVD" && n != "-" }
            .sortedWith(compareBy({ it.value !in favorites }, { it.index != 0 }, { it.value.lowercase() }))
        items(shown, key = { it.index }) { (i, name) ->
            val star: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? =
                if (name in favorites) { { Icon(Icons.Filled.Star, "Favourite", Modifier.size(18.dp)) } } else null
            Chip(
                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = star,
                colors = if (i == current) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                onClick = {
                    current = i
                    scope.launch {
                        runCatching { link.send(d, buildJsonObject { put("on", true); putJsonObject("seg") { put("fx", i) } }) }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val Swatches = listOf(
    "Red" to 0xFF0000, "Orange" to 0xFF6A00, "Yellow" to 0xFFD000, "Green" to 0x00FF20,
    "Cyan" to 0x00E0FF, "Blue" to 0x0030FF, "Purple" to 0x8A00FF, "Pink" to 0xFF2A9A,
    "Warm white" to 0xFFB46B, "White" to 0xFFFFFF, "Cool white" to 0xCFE6FF, "Magenta" to 0xFF00E0,
)

/** Colour swatches; tapping one sets the main colour. */
@Composable
private fun ColoursScreen(link: WatchLink, d: WatchDevice, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    WatchList {
        item { ListHeader { Text("Colours") } }
        items(Swatches.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, rgb) ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or rgb))
                            .clickable(onClickLabel = name) {
                                scope.launch {
                                    runCatching {
                                        link.send(d, buildJsonObject {
                                            put("on", true)
                                            putJsonObject("seg") {
                                                putJsonArray("col") { add(buildJsonArray { add(rgb shr 16 and 0xFF); add(rgb shr 8 and 0xFF); add(rgb and 0xFF) }) }
                                            }
                                        })
                                    }
                                    onDone()
                                }
                            },
                    )
                }
            }
        }
    }
}

/** The controller's presets and playlists; tapping one plays it. */
@Composable
private fun PresetsScreen(link: WatchLink, d: WatchDevice, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var presets by remember { mutableStateOf<List<Triple<Int, String, Boolean>>?>(null) }
    LaunchedEffect(d.id) { presets = runCatching { link.presets(d) }.getOrNull() ?: emptyList() }
    WatchList {
        item { ListHeader { Text("Presets") } }
        val list = presets
        when {
            list == null -> item { CircularProgressIndicator(Modifier.size(28.dp)) }
            list.isEmpty() -> item { Text("No presets saved on this controller.", textAlign = TextAlign.Center, style = MaterialTheme.typography.body2) }
            else -> items(list, key = { it.first }) { (id, name, playlist) ->
                val kind: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? =
                    if (playlist) { { Text("Playlist") } } else null
                Chip(
                    label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = kind,
                    colors = ChipDefaults.secondaryChipColors(),
                    onClick = {
                        scope.launch {
                            runCatching { link.send(d, buildJsonObject { put("ps", id) }) }
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
