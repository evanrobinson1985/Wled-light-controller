package com.wledcontroller.app.data

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnState { CONNECTING, ONLINE, OFFLINE }

/** How the app is reaching a controller: on the home network, at its remote address (VPN …) or through the MQTT broker. */
enum class Route(val label: String) { LOCAL("Home Wi-Fi"), REMOTE("Remote address"), MQTT("MQTT") }

data class DeviceSnapshot(
    val conn: ConnState = ConnState.CONNECTING,
    /** True while a WebSocket push channel is open (instant two-way sync). */
    val live: Boolean = false,
    val route: Route = Route.LOCAL,
    val state: WledState? = null,
    val info: WledInfo? = null,
    val effects: List<String> = emptyList(),
    val palettes: List<String> = emptyList(),
    val fxData: List<String> = emptyList(),
    val presets: List<Preset>? = null,
    val palettePreviews: Map<Int, List<PaletteStop>> = emptyMap(),
) {
    val online get() = conn == ConnState.ONLINE
    fun effectMeta(fx: Int): EffectMeta =
        if (fxData.isEmpty()) EffectMeta.UNKNOWN else EffectMeta.parse(fx, fxData.getOrNull(fx))
}

/**
 * Owns the connection to one WLED device. Keeps a WebSocket open for live state, falls back to
 * HTTP polling, applies changes optimistically and throttles outgoing commands.
 */
class DeviceController(
    device: SavedDevice,
    private val api: WledApi,
    private val wsClient: OkHttpClient,
    parentScope: CoroutineScope,
    private val mqtt: MqttLink? = null,
    private val cache: DeviceCache? = null,
) {
    @Volatile var device: SavedDevice = device
        internal set
    val id get() = device.id

    /** The address it last answered at (home or remote). */
    @Volatile private var activeHost: String? = null
    private val host get() = activeHost ?: device.host
    /** Where to send HTTP requests to it right now. */
    val apiHost: String get() = host
    /** Set while it's only reachable through the MQTT broker, on this topic. */
    @Volatile private var mqttTopic: String? = null
    private val viaMqtt get() = mqttTopic != null

    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _snapshot = MutableStateFlow(DeviceSnapshot())
    val snapshot: StateFlow<DeviceSnapshot> = _snapshot.asStateFlow()

    private val lock = Any()
    private var rawState = JsonObject(emptyMap())
    private var suppressUntil = 0L
    private var deferredIncoming: JsonObject? = null

    private val outQueue = ArrayDeque<JsonObject>()
    private var flushing = false
    private var lastSentAt = 0L

    private var runJob: Job? = null
    @Volatile private var socket: WebSocket? = null
    private val socketOpen = AtomicBoolean(false)

    // ------------------------------------------------------------------ lifecycle

    fun start() {
        if (runJob?.isActive == true) return
        runJob = scope.launch { runLoop() }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        socket?.cancel()
    }

    fun dispose() {
        stop()
        scope.coroutineContext[Job]?.cancel()
    }

    fun reconnect() {
        stop()
        _snapshot.update { it.copy(conn = ConnState.CONNECTING) }
        start()
    }

    /** Its home address, then its remote one if it has one. */
    private fun hosts(): List<String> {
        val home = device.host
        val remote = device.remoteHost?.trim()?.takeIf { it.isNotEmpty() && WledApi.normalizeHost(it) != WledApi.normalizeHost(home) }
        return listOfNotNull(home, remote)
    }

    /** The first address it answers at, with its full state taken in; null if none. */
    private suspend fun reachable(): String? {
        for (h in hosts()) {
            try {
                val o = api.get(h, "/json", api.reachClient).jsonObject
                handleMessage(o)
                cache?.save(id, DeviceCache.FULL, o)
                return h
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Try the next way in.
            }
        }
        return null
    }

    private suspend fun runLoop() {
        var backoff = 1000L
        while (currentCoroutineContext().isActive) {
            try {
                val h = reachable()
                val topic = device.mqttTopic?.takeIf { it.isNotBlank() }
                when {
                    h != null -> { backoff = 1000L; httpSession(h) }
                    topic != null && mqtt?.ready == true -> { backoff = 1000L; mqttSession(mqtt, topic) }
                    else -> throw java.io.IOException("Not reachable")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snapshot.update { it.copy(conn = ConnState.OFFLINE, live = false) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(15_000L)
            }
        }
    }

    /** Talks to it over HTTP and its WebSocket at [h] until the socket drops. */
    private suspend fun httpSession(h: String) {
        activeHost = h
        val route = if (h == device.host) Route.LOCAL else Route.REMOTE
        _snapshot.update { it.copy(conn = ConnState.ONLINE, route = route) }
        if (_snapshot.value.fxData.isEmpty()) scope.launch { loadEffectData() }
        val opened = runSocketSession()
        if (!opened) {
            // Device refused a socket (WLED allows only a few clients): poll for a while instead.
            repeat(10) {
                delay(3000)
                handleMessage(api.get(h, "/json/si").jsonObject)
                _snapshot.update { it.copy(conn = ConnState.ONLINE) }
            }
        }
    }

    /**
     * Reaches it through the MQTT broker: commands go to "[topic]/api" and its state comes back
     * on "[topic]/v". Ends when it can be reached directly again, or the broker drops.
     */
    private suspend fun mqttSession(link: MqttLink, topic: String) {
        loadCached()
        _snapshot.update { it.copy(conn = ConnState.CONNECTING, route = Route.MQTT, live = false) }
        mqttTopic = topic
        try {
            link.follow(topic) { sub, payload -> onMqtt(sub, payload) }
            _snapshot.update { it.copy(conn = if (it.conn == ConnState.OFFLINE) ConnState.OFFLINE else ConnState.ONLINE) }
            while (currentCoroutineContext().isActive) {
                delay(20_000)
                if (!link.isConnected) throw java.io.IOException("Lost the broker")
                // Home again (or the VPN came up): switch to the direct way.
                if (hosts().any { api.probe(it) != null }) return
            }
        } finally {
            mqttTopic = null
            withContext(NonCancellable) { runCatching { link.unfollow(topic) } }
        }
    }

    private fun onMqtt(sub: String, payload: String) {
        when (sub) {
            "status" -> _snapshot.update { it.copy(conn = if (payload.trim().equals("offline", true)) ConnState.OFFLINE else ConnState.ONLINE) }
            "v" -> {
                val patch = WledXml.statePatch(payload) ?: return
                val base = synchronized(lock) { rawState }.let { st ->
                    if ((st["seg"] as? JsonArray)?.isNotEmpty() == true) st
                    else JsonObject(st + mapOf(
                        "mainseg" to JsonPrimitive(0),
                        "seg" to buildJsonArray {
                            add(buildJsonObject {
                                put("id", 0); put("start", 0); put("stop", _snapshot.value.info?.ledCount ?: 0)
                                put("sel", true); put("on", true)
                            })
                        },
                    ))
                }
                val merged = StatePatch.apply(base, patch).let { JsonObject(it + ("ps" to JsonPrimitive(WledXml.preset(payload)))) }
                _snapshot.update { it.copy(conn = ConnState.ONLINE) }
                incomingState(merged)
            }
        }
    }

    /** What it last told the app at home: names, effect details and presets. */
    private fun loadCached() {
        val c = cache ?: return
        (c.load(id, DeviceCache.FULL) as? JsonObject)?.let { o ->
            val hasState = synchronized(lock) { rawState.isNotEmpty() }
            handleMessage(if (hasState) JsonObject(o - "state") else o)
        }
        if (_snapshot.value.fxData.isEmpty()) {
            (c.load(id, DeviceCache.FX_DATA) as? JsonArray)?.map { it.asString() ?: "" }?.let { fx -> _snapshot.update { it.copy(fxData = fx) } }
        }
        if (_snapshot.value.presets == null) {
            (c.load(id, DeviceCache.PRESETS) as? JsonObject)?.let { p -> _snapshot.update { it.copy(presets = api.parsePresets(p)) } }
        }
    }

    /** Suspends while the socket is open. Returns whether it ever opened. */
    private suspend fun runSocketSession(): Boolean {
        val opened = AtomicBoolean(false)
        val closed = CompletableDeferred<Unit>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                socketOpen.set(true)
                if (liveWanted) webSocket.send("""{"lv":true}""")
                _snapshot.update { it.copy(live = true, conn = ConnState.ONLINE) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                LiveView.parseBinary(bytes.toByteArray())?.let { colors -> _liveColors.value = colors; lastLiveAt = SystemClock.elapsedRealtime() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
                handleMessage(o)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                closed.complete(Unit)
            }
        }
        val request = Request.Builder().url("ws://${WledApi.normalizeHost(host)}/ws").build()
        val ws = com.wledcontroller.app.sim.SimHub.openSocket(host, request, listener) ?: wsClient.newWebSocket(request, listener)
        socket = ws
        try {
            closed.await()
        } finally {
            socketOpen.set(false)
            ws.cancel()
            if (socket === ws) socket = null
            _snapshot.update { it.copy(live = false) }
        }
        return opened.get()
    }

    // ------------------------------------------------------------------ incoming

    private fun handleMessage(o: JsonObject) {
        val info = o.obj("info")?.let { runCatching { WledInfo.parse(it) }.getOrNull() }
        val effects = o.arr("effects")?.map { it.asString() ?: "" }
        val palettes = o.arr("palettes")?.map { it.asString() ?: "" }
        if (info != null || effects != null || palettes != null) {
            _snapshot.update {
                it.copy(
                    info = info ?: it.info,
                    effects = effects ?: it.effects,
                    palettes = palettes ?: it.palettes,
                )
            }
        }
        o.obj("state")?.let { incomingState(it) }
    }

    private fun incomingState(st: JsonObject) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (now < suppressUntil) {
                // The user is mid-gesture: hold the echo so the controls don't jump back.
                deferredIncoming = st
                val wait = suppressUntil - now
                scope.launch {
                    delay(wait + 20)
                    val pending = synchronized(lock) {
                        if (SystemClock.elapsedRealtime() >= suppressUntil) deferredIncoming.also { deferredIncoming = null } else null
                    }
                    if (pending != null) incomingState(pending)
                }
                return
            }
            rawState = st
        }
        publish()
    }

    private fun publish() {
        val st = synchronized(lock) { rawState }
        val parsed = runCatching { WledState.parse(st) }.getOrNull() ?: return
        _snapshot.update { it.copy(state = parsed) }
    }

    // ------------------------------------------------------------------ outgoing

    /**
     * Sends a JSON state patch. Applied locally at once; rapid calls are merged and sent at most
     * every ~70 ms. Pass [optimistic] = false for commands whose result can't be predicted locally.
     */
    fun send(patch: JsonObject, optimistic: Boolean = true) {
        StartupPreset.actionFor(patch)?.let(::rememberForStartup)
        synchronized(lock) {
            if (optimistic) {
                rawState = StatePatch.apply(rawState, patch)
                suppressUntil = SystemClock.elapsedRealtime() + 450
            }
            val last = outQueue.lastOrNull()
            if (last != null && StatePatch.canMerge(last, patch)) {
                outQueue[outQueue.size - 1] = StatePatch.mergePatches(last, patch)
            } else {
                outQueue.addLast(patch)
            }
            if (!flushing) {
                flushing = true
                scope.launch { flushLoop() }
            }
        }
        if (optimistic) publish()
    }

    private suspend fun flushLoop() {
        while (true) {
            val wait = lastSentAt + 70 - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            val next = synchronized(lock) {
                outQueue.removeFirstOrNull().also { if (it == null) flushing = false }
            } ?: return
            lastSentAt = SystemClock.elapsedRealtime()
            val topic = mqttTopic
            if (topic != null) {
                try {
                    mqtt?.command(topic, next.toString())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _snapshot.update { it.copy(conn = ConnState.OFFLINE) }
                }
                continue
            }
            val viaSocket = socketOpen.get() && socket?.send(next.toString()) == true
            if (!viaSocket) {
                try {
                    api.postState(host, next)
                    scope.launch { delay(250); refreshState() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _snapshot.update { it.copy(conn = ConnState.OFFLINE) }
                }
            }
        }
    }

    // ------------------------------------------------------------------ live view

    private val _liveColors = MutableStateFlow<IntArray?>(null)
    /** What its LEDs are showing right now (sampled on long strips), while [setLiveView] is on. */
    val liveColors: StateFlow<IntArray?> = _liveColors.asStateFlow()
    @Volatile private var liveWanted = false
    @Volatile private var lastLiveAt = 0L
    private var liveJob: Job? = null

    /**
     * Starts or stops the live view. Over the WebSocket the controller pushes frames; when there's
     * no socket (or it stops sending), /json/live is polled instead.
     */
    fun setLiveView(on: Boolean) {
        if (on == liveWanted) return
        liveWanted = on
        if (socketOpen.get()) socket?.send(if (on) """{"lv":true}""" else """{"lv":false}""")
        liveJob?.cancel()
        if (!on) { _liveColors.value = null; return }
        liveJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                val pushed = socketOpen.get() && SystemClock.elapsedRealtime() - lastLiveAt < 1500
                if (!pushed && !viaMqtt) {
                    runCatching { api.get(host, "/json/live") as? JsonObject }.getOrNull()
                        ?.let(LiveView::parseJson)?.let { _liveColors.value = it }
                }
                delay(if (pushed) 1000 else 300)
            }
        }
    }

    // ------------------------------------------------------------------ start-up look

    /** Whether the controller should start up with what was last chosen here. */
    @Volatile var keepStartupLook = true
    private var startupJob: Job? = null
    @Volatile private var pendingStartup: StartupPreset.Action? = null
    /** The boot preset the controller has, once known, so it's only written when it changes. */
    @Volatile private var bootPreset: Int? = null

    /**
     * Makes [action] the start-up look once the user has stopped changing things for a few
     * seconds, so dragging a slider doesn't wear the controller's flash with a save per step.
     */
    private fun rememberForStartup(action: StartupPreset.Action) {
        if (!keepStartupLook) return
        pendingStartup = action
        startupJob?.cancel()
        startupJob = scope.launch {
            delay(STARTUP_SAVE_DELAY_MS)
            val a = pendingStartup ?: return@launch
            pendingStartup = null
            runCatching { applyStartup(a) }
        }
    }

    private suspend fun applyStartup(action: StartupPreset.Action) {
        val id = when (action) {
            is StartupPreset.UsePreset -> action.id
            StartupPreset.SaveCurrent -> {
                if (_snapshot.value.presets == null) loadPresets()
                val slot = StartupPreset.slot(_snapshot.value.presets)
                // Brightness and segment layout too, so it comes back exactly as it was.
                send(buildJsonObject {
                    put("psave", slot); put("n", StartupPreset.NAME); put("ib", true); put("sb", true)
                }, optimistic = false)
                slot
            }
        }
        // The boot preset is in the controller's settings, which only HTTP reaches.
        if (viaMqtt) return
        val current = bootPreset ?: api.config(host).obj("def")?.int("ps")
        if (current != id) {
            api.postConfig(host, buildJsonObject { putJsonObject("def") { put("ps", id) } })
        }
        bootPreset = id
    }

    suspend fun refreshState() {
        if (viaMqtt) return
        runCatching { handleMessage(api.get(host, "/json/si").jsonObject) }
            .onSuccess { _snapshot.update { it.copy(conn = ConnState.ONLINE) } }
    }

    suspend fun loadEffectData() {
        if (viaMqtt) { loadCached(); return }
        runCatching { api.effectData(host) }.onSuccess { list ->
            _snapshot.update { it.copy(fxData = list) }
            cache?.save(id, DeviceCache.FX_DATA, JsonArray(list.map { JsonPrimitive(it) }))
        }
    }

    suspend fun loadPresets() {
        if (viaMqtt) { loadCached(); return }
        runCatching { api.presetsJson(host) }.onSuccess { raw ->
            _snapshot.update { it.copy(presets = api.parsePresets(raw)) }
            cache?.save(id, DeviceCache.PRESETS, raw)
        }
    }

    suspend fun loadPalettePreviews() {
        if (_snapshot.value.palettePreviews.isNotEmpty() || viaMqtt) return
        runCatching { api.palettePreviews(host) }.onSuccess { map -> _snapshot.update { it.copy(palettePreviews = map) } }
    }

    // ------------------------------------------------------------------ convenience commands

    private companion object {
        const val STARTUP_SAVE_DELAY_MS = 4000L
    }

    private val withWhite get() = _snapshot.value.info?.hasWhite == true

    fun setPower(on: Boolean) = send(buildJsonObject { put("on", on) })

    fun setBrightness(bri: Int) = send(buildJsonObject { put("bri", bri.coerceIn(1, 255)); if (bri > 0) put("on", true) })

    fun setTransition(deciseconds: Int) = send(buildJsonObject { put("transition", deciseconds) })

    /** Sets one colour slot on the selected segments. */
    fun setColor(slot: Int, color: WledColor) = send(buildJsonObject {
        putJsonObject("seg") {
            put("col", buildJsonArray {
                repeat(slot) { add(JsonArray(emptyList())) }
                add(color.toJson(withWhite))
            })
        }
    })

    /** Sets a single numeric/boolean segment field (fx, sx, ix, pal, c1, o1, cct …) on the selected segments. */
    fun setSegValue(key: String, value: Int) = send(buildJsonObject { putJsonObject("seg") { put(key, value) } })
    fun setSegFlag(key: String, value: Boolean) = send(buildJsonObject { putJsonObject("seg") { put(key, value) } })

    fun setEffect(fx: Int) = send(buildJsonObject { putJsonObject("seg") { put("fx", fx) }; put("on", true) })
    fun setPalette(pal: Int) = send(buildJsonObject { putJsonObject("seg") { put("pal", pal) } })

    /** Patches a specific segment by id. */
    fun updateSegment(id: Int, fields: JsonObject) = send(buildJsonObject {
        put("seg", buildJsonArray { add(JsonObject(fields + ("id" to JsonPrimitive(id)))) })
    })

    /** Selects only [id] (or toggles it into the selection when [additive]). */
    fun selectSegment(id: Int, additive: Boolean) {
        val segs = _snapshot.value.state?.segments ?: return
        send(buildJsonObject {
            put("seg", buildJsonArray {
                segs.forEach { s ->
                    val sel = if (additive) (if (s.id == id) !s.sel else s.sel) else s.id == id
                    add(buildJsonObject { put("id", s.id); put("sel", sel) })
                }
            })
        })
    }

    fun addSegment() {
        val st = _snapshot.value.state ?: return
        val count = _snapshot.value.info?.ledCount ?: 0
        val nextId = (st.segments.maxOfOrNull { it.id } ?: -1) + 1
        val lastStop = st.segments.maxOfOrNull { it.stop } ?: 0
        val start = if (lastStop < count) lastStop else 0
        updateSegment(nextId, buildJsonObject { put("start", start); put("stop", count); put("on", true) })
    }

    fun deleteSegment(id: Int) = updateSegment(id, buildJsonObject { put("stop", 0) })

    /**
     * Applies a three-colour theme to the selected segments with WLED's built-in palettes that
     * use the segment colours, plus the first suggested effect this firmware has.
     */
    fun applyTheme(colors: List<Int>, effect: String?, bands: Boolean, speed: Int, intensity: Int) {
        val snap = _snapshot.value
        val fx = effect?.let { name -> snap.effects.indexOfFirst { it.equals(name, ignoreCase = true) } }?.takeIf { it >= 0 } ?: 0
        val palName = if (bands) "* Colors Only" else "* Color Gradient"
        val pal = snap.palettes.indexOfFirst { it.equals(palName, ignoreCase = true) }.takeIf { it >= 0 } ?: if (bands) 5 else 4
        send(buildJsonObject {
            put("on", true)
            putJsonObject("seg") {
                put("col", buildJsonArray { colors.take(3).forEach { add(WledColor.fromRgb(it).toJson(withWhite)) } })
                put("fx", fx)
                put("pal", pal)
                put("sx", speed)
                put("ix", intensity)
            }
        })
    }

    fun applyPreset(id: Int) = send(buildJsonObject { put("ps", id) }, optimistic = false)

    fun savePreset(id: Int, name: String, includeBrightness: Boolean, saveSegmentBounds: Boolean) = send(buildJsonObject {
        put("psave", id); put("n", name); put("ib", includeBrightness); put("sb", saveSegmentBounds)
    }, optimistic = false)

    fun deletePreset(id: Int) = send(buildJsonObject { put("pdel", id) }, optimistic = false)

    /** WLED nightlight modes: 0 instant, 1 fade, 2 colour fade, 3 sunrise. */
    fun setNightlight(on: Boolean, minutes: Int, mode: Int, targetBri: Int) = send(buildJsonObject {
        putJsonObject("nl") { put("on", on); put("dur", minutes); put("mode", mode); put("tbri", targetBri) }
        if (on) put("on", true)
    })

    fun setSync(send: Boolean, receive: Boolean) = send(buildJsonObject {
        putJsonObject("udpn") { put("send", send); put("recv", receive) }
    })

    fun overrideLive() = send(buildJsonObject { put("lor", 1) })

    fun reboot() = send(buildJsonObject { put("rb", true) }, optimistic = false)

    /** Full state suitable for storing in a scene. */
    fun captureState(): JsonObject {
        val st = synchronized(lock) { rawState }
        val drop = setOf("ps", "pl", "nl", "udpn", "lor", "time", "error", "pss")
        return JsonObject(st.filterKeys { it !in drop })
    }

    fun applyScene(state: String) {
        val o = runCatching { json.parseToJsonElement(state) as? JsonObject }.getOrNull() ?: return
        send(o)
    }
}
