package com.wledcontroller.app.data

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

data class DeviceSnapshot(
    val conn: ConnState = ConnState.CONNECTING,
    /** True while a WebSocket push channel is open (instant two-way sync). */
    val live: Boolean = false,
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
) {
    @Volatile var device: SavedDevice = device
        internal set
    val id get() = device.id
    private val host get() = device.host

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

    private suspend fun runLoop() {
        var backoff = 1000L
        while (currentCoroutineContext().isActive) {
            try {
                handleMessage(api.get(host, "/json").jsonObject)
                _snapshot.update { it.copy(conn = ConnState.ONLINE) }
                if (_snapshot.value.fxData.isEmpty()) scope.launch { loadEffectData() }
                backoff = 1000L
                val opened = runSocketSession()
                if (!opened) {
                    // Device refused a socket (WLED allows only a few clients): poll for a while instead.
                    repeat(10) {
                        delay(3000)
                        handleMessage(api.get(host, "/json/si").jsonObject)
                        _snapshot.update { it.copy(conn = ConnState.ONLINE) }
                    }
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

    /** Suspends while the socket is open. Returns whether it ever opened. */
    private suspend fun runSocketSession(): Boolean {
        val opened = AtomicBoolean(false)
        val closed = CompletableDeferred<Unit>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                socketOpen.set(true)
                _snapshot.update { it.copy(live = true, conn = ConnState.ONLINE) }
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

    suspend fun refreshState() {
        runCatching { handleMessage(api.get(host, "/json/si").jsonObject) }
            .onSuccess { _snapshot.update { it.copy(conn = ConnState.ONLINE) } }
    }

    suspend fun loadEffectData() {
        runCatching { api.effectData(host) }.onSuccess { list -> _snapshot.update { it.copy(fxData = list) } }
    }

    suspend fun loadPresets() {
        runCatching { api.presets(host) }.onSuccess { list -> _snapshot.update { it.copy(presets = list) } }
    }

    suspend fun loadPalettePreviews() {
        if (_snapshot.value.palettePreviews.isNotEmpty()) return
        runCatching { api.palettePreviews(host) }.onSuccess { map -> _snapshot.update { it.copy(palettePreviews = map) } }
    }

    // ------------------------------------------------------------------ convenience commands

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
