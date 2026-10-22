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
import androidx.wear.compose.material.ToggleChip
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Home
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
        composable("home") {
            HomeScreen(
                link, devices, onOpen = { nav.navigate("device/${it.id}") },
                onHouse = { nav.navigate("house") }, onScenes = { nav.navigate("scenes") }, onGroups = { nav.navigate("groups") },
            )
        }
        composable("house") { HouseScreen(link) }
        composable("scenes") { ScenesScreen(link) }
        composable("groups") { GroupsScreen(link) }
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
private fun HomeScreen(
    link: WatchLink,
    devices: List<WatchDevice>,
    onOpen: (WatchDevice) -> Unit,
    onHouse: () -> Unit,
    onScenes: () -> Unit,
    onGroups: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scenes by link.scenes.collectAsStateWithLifecycle()
    val groups by link.groups.collectAsStateWithLifecycle()
    val states = remember { mutableStateMapOf<String, LightState?>() }
    LaunchedEffect(devices) {
        while (true) {
            devices.forEach { d -> states[d.id] = runCatching { link.state(d) }.getOrNull() }
            delay(5000)
        }
    }
    // With no controllers yet: ask the phone, and say what's missing if it can't answer.
    var checking by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Getting your controllers from the phone…") }
    suspend fun check() {
        checking = true
        link.refreshDevices(askPhone = true)
        hint = when {
            link.devices.value.isNotEmpty() -> ""
            !link.phoneConnected() -> "Your watch isn't connected to your phone. Check the Galaxy Wearable app, then try again."
            else -> "No controllers yet. Open WLED Controller on your phone (the newest version), add your lights there, then try again."
        }
        checking = false
    }
    LaunchedEffect(devices.isEmpty()) { if (devices.isEmpty()) check() }
    WatchList {
        item { ListHeader { Text("WLED Controller") } }
        if (devices.isEmpty()) {
            item {
                if (checking) CircularProgressIndicator(Modifier.size(28.dp))
                else Text(hint, textAlign = TextAlign.Center, style = MaterialTheme.typography.body2, modifier = Modifier.padding(horizontal = 8.dp))
            }
            item { Chip(label = { Text("Try again") }, enabled = !checking, onClick = { scope.launch { check() } }) }
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
            item { NavChip("Whole house", Icons.Filled.Home, onHouse) }
            if (scenes.isNotEmpty()) item { NavChip("Scenes", Icons.Filled.AutoAwesomeMosaic, onScenes) }
            if (groups.isNotEmpty()) item { NavChip("Groups", Icons.Filled.Workspaces, onGroups) }
        }
    }
}

@Composable
private fun NavChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Chip(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        colors = ChipDefaults.secondaryChipColors(),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The whole-house effect, which runs on the phone: play or stop it, pick the effect (holiday
 * shadows by holiday) and set its speed, size and (for shadow figures) how many.
 */
@Composable
private fun HouseScreen(link: WatchLink) {
    val scope = rememberCoroutineScope()
    val effects by link.houseEffects.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf<HouseStatus?>(null) }
    var noPhone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val s = link.house()
            if (s != null) { status = s; noPhone = false } else if (status == null) noPhone = true
            delay(3000)
        }
    }
    fun update(call: suspend () -> HouseStatus?) { scope.launch { call()?.let { status = it } } }
    WatchList {
        item { ListHeader { Text("Whole house") } }
        val st = status
        if (st == null) {
            item {
                if (noPhone) Text("Whole-house effects run on your phone: keep it nearby with the app installed.", textAlign = TextAlign.Center, style = MaterialTheme.typography.body2)
                else CircularProgressIndicator(Modifier.size(28.dp))
            }
            return@WatchList
        }
        val fx = effects.firstOrNull { it.name == st.effect }
        val shadow = st.shadow?.let { s -> effects.firstOrNull { it.name == s } }
        item {
            Chip(
                label = { Text(if (st.running) "Stop" else "Play") },
                secondaryLabel = { Text((fx?.label ?: st.effect) + (shadow?.let { " + ${it.label}" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(if (st.running) Icons.Filled.Stop else Icons.Filled.PlayArrow, null) },
                colors = if (st.running) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                onClick = { status = st.copy(running = !st.running); update { link.house(if (st.running) "stop" else "play") } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Text("Speed", style = MaterialTheme.typography.caption2) }
        item {
            InlineSlider(
                value = st.speed, onValueChange = { v -> status = st.copy(speed = v); update { link.house(speed = v) } },
                valueRange = 0f..1f, steps = 9,
                increaseIcon = { Icon(Icons.Filled.Add, "Faster") }, decreaseIcon = { Icon(Icons.Filled.Remove, "Slower") },
            )
        }
        item { Text("Size", style = MaterialTheme.typography.caption2) }
        item {
            InlineSlider(
                value = st.size, onValueChange = { v -> status = st.copy(size = v); update { link.house(size = v) } },
                valueRange = 0f..1f, steps = 9,
                increaseIcon = { Icon(Icons.Filled.Add, "Bigger") }, decreaseIcon = { Icon(Icons.Filled.Remove, "Smaller") },
            )
        }
        if (shadow?.plural != null) {
            // The holiday shadow's figures, apart from the effect underneath.
            val noun = (shadow.noun ?: "figure").replaceFirstChar(Char::uppercase)
            item { Text("$noun size", style = MaterialTheme.typography.caption2) }
            item {
                InlineSlider(
                    value = st.shadowSize, onValueChange = { v -> status = st.copy(shadowSize = v); update { link.house(shadowSize = v) } },
                    valueRange = 0f..1f, steps = 9,
                    increaseIcon = { Icon(Icons.Filled.Add, "Bigger") }, decreaseIcon = { Icon(Icons.Filled.Remove, "Smaller") },
                )
            }
            item { Text("$noun speed", style = MaterialTheme.typography.caption2) }
            item {
                InlineSlider(
                    value = st.shadowSpeed, onValueChange = { v -> status = st.copy(shadowSpeed = v); update { link.house(shadowSpeed = v) } },
                    valueRange = 0f..1f, steps = 9,
                    increaseIcon = { Icon(Icons.Filled.Add, "Faster") }, decreaseIcon = { Icon(Icons.Filled.Remove, "Slower") },
                )
            }
            item { Text("Number of ${shadow.plural}: ${st.count}", style = MaterialTheme.typography.caption2) }
            item {
                InlineSlider(
                    value = st.count.toFloat(), onValueChange = { v -> val n = v.roundToInt(); status = st.copy(count = n); update { link.house(count = n) } },
                    valueRange = 1f..8f, steps = 6,
                    increaseIcon = { Icon(Icons.Filled.Add, "More") }, decreaseIcon = { Icon(Icons.Filled.Remove, "Fewer") },
                )
            }
        }
        // Words on the matrices, over the effect.
        item { ListHeader { Text("Text on matrices") } }
        items(listOf("Merry Christmas!", "Happy Holidays!", "Happy New Year!", "Happy Halloween!", "Welcome!"), key = { "text-$it" }) { msg ->
            Chip(
                label = { Text(msg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = if (st.text == msg) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                onClick = { status = st.copy(text = msg); update { link.house(text = msg) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (st.text != null) item {
            Chip(label = { Text("Text off") }, onClick = { status = st.copy(text = null); update { link.house(text = "") } },
                colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
        }
        // Everyday effects first, then the holiday shadows by holiday, which play over the effect
        // (tap one again to turn it off).
        effects.groupBy { it.holiday ?: "" }.forEach { (holiday, list) ->
            item { ListHeader { Text(if (holiday.isEmpty()) "Effects" else "$holiday shadows") } }
            items(list, key = { it.name }) { e ->
                Chip(
                    label = { Text(e.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = if (e.name == st.effect || e.name == st.shadow) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    onClick = {
                        status = if (e.holiday == null) st.copy(effect = e.name, running = true)
                        else st.copy(shadow = if (st.shadow == e.name) null else e.name, running = true)
                        update { link.house("play", effect = e.name) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The phone's scenes; tapping one brings back every controller's look. */
@Composable
private fun ScenesScreen(link: WatchLink) {
    val scope = rememberCoroutineScope()
    val scenes by link.scenes.collectAsStateWithLifecycle()
    var applied by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    WatchList {
        item { ListHeader { Text("Scenes") } }
        items(scenes, key = { it.id }) { sc ->
            val note: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = when {
                applied == sc.id && failed -> { { Text("Needs the phone nearby") } }
                applied == sc.id -> { { Text("On") } }
                else -> null
            }
            Chip(
                label = { Text(sc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = note,
                colors = if (applied == sc.id && !failed) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                onClick = { applied = sc.id; failed = false; scope.launch { failed = !link.applyScene(sc.id) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The phone's groups, each switched on or off together. */
@Composable
private fun GroupsScreen(link: WatchLink) {
    val scope = rememberCoroutineScope()
    val groups by link.groups.collectAsStateWithLifecycle()
    val on = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(groups) { groups.forEach { g -> link.group(g.id)?.let { on[g.id] = it } } }
    WatchList {
        item { ListHeader { Text("Groups") } }
        items(groups, key = { it.id }) { g ->
            ToggleChip(
                checked = on[g.id] == true,
                onCheckedChange = { v -> on[g.id] = v; scope.launch { link.group(g.id, v) } },
                label = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text("${g.devices.size} controller${if (g.devices.size == 1) "" else "s"}") },
                toggleControl = { Switch(checked = on[g.id] == true) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val RING_DOTS = 48

/** [colors] as dots round the edge of the watch face, clockwise from the top. */
@Composable
private fun PreviewRing(colors: IntArray) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        if (colors.isEmpty()) return@Canvas
        val r = size.minDimension / 2f - 5.dp.toPx()
        val dot = 2.6.dp.toPx()
        val cx = size.width / 2f; val cy = size.height / 2f
        colors.forEachIndexed { i, c ->
            val a = (i.toFloat() / colors.size) * 2f * kotlin.math.PI.toFloat() - kotlin.math.PI.toFloat() / 2f
            val o = androidx.compose.ui.geometry.Offset(cx + r * kotlin.math.cos(a), cy + r * kotlin.math.sin(a))
            if (c and 0xFFFFFF == 0) drawCircle(Color(0xFF2A2F3A), dot * 0.7f, o)
            else drawCircle(Color(0xFF000000.toInt() or c), dot, o)
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
    // What the lights show, as the phone app previews it: a ring of dots round the watch face.
    var ring by remember { mutableStateOf<IntArray?>(null) }
    LaunchedEffect(d.id) {
        while (true) {
            ring = runCatching { link.preview(d, RING_DOTS) }.getOrNull()
            delay(150)
        }
    }

    Scaffold(timeText = { TimeText() }) {
        ring?.let { PreviewRing(it) }
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
    val effectSettings by link.effectSettings.collectAsStateWithLifecycle()
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
                        runCatching { link.send(d, WledParse.effectPatch(i, effectSettings["${d.id}#$i"])) }
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
