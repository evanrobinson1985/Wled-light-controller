package com.wledcontroller.app.sim

import com.wledcontroller.app.data.StatePatch
import com.wledcontroller.app.data.WledColor
import com.wledcontroller.app.data.arr
import com.wledcontroller.app.data.bool
import com.wledcontroller.app.data.int
import com.wledcontroller.app.data.obj
import com.wledcontroller.app.data.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.CopyOnWriteArrayList

/** What's saved for a simulated controller. JSON parts are kept as text in WLED's own shapes. */
@Serializable
data class SimDeviceData(
    val host: String,
    val name: String,
    val seed: Int,
    val createdAt: Long,
    val state: String,
    val cfg: String,
    val presets: String,
    val ledmap: String? = null,
)

/** A reply to an HTTP request. */
class SimResponse(val code: Int, val body: String, val type: String = "application/json")

/**
 * One simulated WLED controller: answers the JSON API like the real firmware, runs effects,
 * presets, playlists, the nightlight, transitions and the power limiter, accepts realtime (DDP)
 * frames, and drives other simulated controllers through network outputs like a real master.
 */
class SimDevice(data: SimDeviceData, private val hub: SimHub) {

    val host: String = data.host
    @Volatile var name: String = data.name
        private set
    val seed = data.seed
    private val createdAt = data.createdAt
    private var bootAt = System.currentTimeMillis()

    private val lock = Any()
    private var state: JsonObject = parse(data.state) ?: defaultState(150)
    private var cfg: JsonObject = parse(data.cfg) ?: defaultCfg(data.name, 150)
    private var presets: JsonObject = parse(data.presets) ?: JsonObject(mapOf("0" to JsonObject(emptyMap())))
    private var ledmap: JsonObject? = data.ledmap?.let(::parse)

    // Realtime (DDP) input.
    private var rtFrame: IntArray? = null
    private var rtUntil = 0L
    private var rtFrom = ""

    // Transition from the last frame shown.
    private var lastFrame = IntArray(0)
    private var transFrom: IntArray? = null
    private var transStart = 0L
    private var transMs = 0L

    // Nightlight and playlist progress.
    private var nlStart = 0L
    private var nlStartBri = 128
    private var playlist: JsonObject? = null
    private var playlistId = -1
    private var playIndex = 0
    private var playNextAt = 0L
    private val frozenAt = HashMap<Int, Long>()

    private var cacheAt = -1L
    private var cache = IntArray(0)
    @Volatile var powerMa = 0
        private set

    /** Open (simulated) WebSocket clients. */
    internal val sockets = CopyOnWriteArrayList<SimSocket>()

    fun data() = synchronized(lock) {
        SimDeviceData(host, name, seed, createdAt, state.toString(), cfg.toString(), presets.toString(), ledmap?.toString())
    }

    // ------------------------------------------------------------------ layout

    private fun buses(): List<JsonObject> = cfg.obj("hw")?.obj("led")?.arr("ins")?.mapNotNull { it as? JsonObject } ?: emptyList()
    private fun isNetwork(b: JsonObject) = b.int("type") in 80..95

    /** LEDs driven by every output, local and network (WLED's "total"). */
    val totalLeds get() = synchronized(lock) { buses().maxOfOrNull { it.int("start") + it.int("len") } ?: 0 }

    /** LEDs physically wired to this controller (what the simulator draws for it). */
    val localLeds get() = synchronized(lock) { buses().filterNot(::isNetwork).maxOfOrNull { it.int("start") + it.int("len") } ?: 0 }

    val hasWhite get() = synchronized(lock) { buses().any { it.int("type") in setOf(30, 31, 32) } }

    /** Logical length effects run over: a 2D map's width, else every LED. */
    private fun logicalLen(): Int {
        val map = ledmap
        val w = map?.int("width") ?: 0
        return if (w > 0 && (map?.int("height") ?: 0) > 0) w else buses().maxOfOrNull { it.int("start") + it.int("len") } ?: 0
    }

    // ------------------------------------------------------------------ HTTP

    fun handle(method: String, path: String, body: String?): SimResponse {
        val p = path.substringBefore('?')
        val query = path.substringAfter('?', "")
        return when {
            method == "GET" && (p == "/json" || p == "/json/") -> ok(full())
            method == "GET" && p == "/json/state" -> ok(synchronized(lock) { stateOut() })
            method == "GET" && p == "/json/info" -> ok(info())
            method == "GET" && p == "/json/si" -> ok(buildJsonObject { put("state", synchronized(lock) { stateOut() }); put("info", info()) })
            method == "GET" && p == "/json/eff" -> ok(JsonArray(SimCatalog.effects.map(::JsonPrimitive)))
            method == "GET" && p == "/json/pal" -> ok(JsonArray(SimCatalog.palettes.map { JsonPrimitive(it.first) }))
            method == "GET" && p == "/json/fxdata" -> ok(JsonArray(SimCatalog.effects.indices.map { JsonPrimitive(SimCatalog.fxData(it)) }))
            method == "GET" && p == "/json/palx" -> ok(palx(query.substringAfter("page=", "0").toIntOrNull() ?: 0))
            method == "GET" && p == "/json/cfg" -> ok(synchronized(lock) { cfg })
            method == "GET" && p == "/json/nodes" -> ok(buildJsonObject { putJsonArray("nodes") {} })
            method == "GET" && p == "/json/live" -> ok(buildJsonObject {
                val f = frame(System.currentTimeMillis())
                putJsonArray("leds") { f.take(256).forEach { add("%06X".format(it)) } }
                put("n", 1)
            })
            method == "GET" && p == "/presets.json" -> ok(synchronized(lock) { presets })
            method == "GET" && p == "/ledmap.json" -> synchronized(lock) { ledmap }?.let { ok(it) } ?: SimResponse(404, "Not found", "text/plain")
            method == "POST" && (p == "/json/state" || p == "/json") -> {
                val patch = parse(body.orEmpty()) ?: return SimResponse(400, """{"error":9}""")
                val verbose = patch.bool("v")
                applyState(patch)
                if (verbose) ok(synchronized(lock) { stateOut() }) else ok(buildJsonObject { put("success", true) })
            }
            method == "POST" && p == "/json/cfg" -> {
                val patch = parse(body.orEmpty()) ?: return SimResponse(400, """{"error":9}""")
                applyConfig(patch)
                ok(buildJsonObject { put("success", true) })
            }
            method == "POST" && p == "/upload" -> {
                val (file, content) = parseUpload(body.orEmpty())
                if (file.endsWith("ledmap.json")) applyLedmap(parse(content))
                SimResponse(200, "File Uploaded!", "text/plain")
            }
            method == "GET" && (p == "/" || p.endsWith(".htm")) -> SimResponse(200, "<html><body>Simulated WLED controller</body></html>", "text/html")
            else -> SimResponse(404, "Not found", "text/plain")
        }
    }

    /** A message from a WebSocket client: a state change, like a POST. */
    internal fun handleSocket(text: String) {
        val patch = parse(text) ?: return
        applyState(patch)
    }

    private fun ok(el: JsonElement) = SimResponse(200, el.toString())

    private fun full() = synchronized(lock) {
        buildJsonObject {
            put("state", stateOut())
            put("info", info())
            put("effects", JsonArray(SimCatalog.effects.map(::JsonPrimitive)))
            put("palettes", JsonArray(SimCatalog.palettes.map { JsonPrimitive(it.first) }))
        }
    }

    private fun palx(page: Int): JsonObject {
        val perPage = 8
        val all = SimCatalog.palettes
        val maxPage = (all.size - 1) / perPage
        return buildJsonObject {
            put("m", maxPage)
            putJsonObject("p") {
                for (id in page * perPage until minOf(all.size, (page + 1) * perPage)) {
                    val stops = all[id].second
                    putJsonArray(id.toString()) {
                        when (id) {
                            0, 1 -> { add("r"); add("r"); add("r") }
                            2 -> { add("c1") }
                            3 -> { add("c1"); add("c1"); add("c2"); add("c2") }
                            4 -> { add("c3"); add("c2"); add("c1") }
                            5 -> { add("c1"); add("c1"); add("c1"); add("c2"); add("c2"); add("c2"); add("c3"); add("c3"); add("c3") }
                            else -> stops.forEach { (pos, rgb) ->
                                add(buildJsonArray { add(pos); add((rgb shr 16) and 0xFF); add((rgb shr 8) and 0xFF); add(rgb and 0xFF) })
                            }
                        }
                    }
                }
            }
        }
    }

    fun info(): JsonObject = synchronized(lock) {
        val now = System.currentTimeMillis()
        val live = now < rtUntil && state.int("lor") == 0
        val count = buses().maxOfOrNull { it.int("start") + it.int("len") } ?: 0
        val segCount = state.arr("seg")?.size ?: 1
        buildJsonObject {
            put("ver", "0.15.0")
            put("vid", 2412100)
            putJsonObject("leds") {
                put("count", count)
                put("pwr", powerMa)
                put("fps", 42)
                put("maxpwr", cfg.obj("hw")?.obj("led")?.int("maxpwr") ?: 0)
                put("maxseg", 32)
                put("lc", if (hasWhiteLocked()) 3 else 1)
                putJsonArray("seglc") { repeat(segCount) { add(if (hasWhiteLocked()) 3 else 1) } }
                put("rgbw", hasWhiteLocked())
                put("wv", if (hasWhiteLocked()) 1 else 0)
                put("cct", 0)
            }
            put("str", false)
            put("name", name)
            put("udpport", 21324)
            put("live", live)
            put("liveseg", -1)
            put("lm", if (live) "DDP" else "")
            put("lip", if (live) rtFrom else "")
            put("ws", sockets.size)
            put("fxcount", SimCatalog.effects.size)
            put("palcount", SimCatalog.palettes.size)
            put("cpalcount", 0)
            putJsonObject("wifi") {
                put("bssid", "00:00:00:00:00:00"); put("rssi", -42); put("signal", 100); put("channel", 6)
            }
            putJsonObject("fs") { put("u", 12); put("t", 983); put("pmt", 0) }
            put("ndc", 0)
            put("arch", "esp32")
            put("core", "v4.4.4")
            put("freeheap", 180_000 - count * 12)
            put("uptime", ((now - bootAt) / 1000).toInt())
            put("opt", 79)
            put("brand", "WLED")
            put("product", "Simulator")
            put("mac", "5113" + "%08x".format(seed))
            put("ip", host)
        }
    }

    private fun hasWhiteLocked() = buses().any { it.int("type") in setOf(30, 31, 32) }

    /** State as reported: live values for the nightlight countdown and playlist. */
    private fun stateOut(): JsonObject {
        val m = state.toMutableMap()
        val nl = state.obj("nl")
        if (nl != null) {
            val rem = if (nl.bool("on")) ((nl.int("dur", 60) * 60_000L - (System.currentTimeMillis() - nlStart)) / 1000).coerceAtLeast(0) else -1
            m["nl"] = JsonObject(nl.toMutableMap().apply { put("rem", JsonPrimitive(rem)) })
        }
        m["pl"] = JsonPrimitive(playlistId)
        return JsonObject(m)
    }

    // ------------------------------------------------------------------ state changes

    fun applyState(input: JsonObject, fromPlaylist: Boolean = false) {
        var changed = false
        synchronized(lock) {
            val before = state
            var patch = input
            // "t" toggles.
            if ((patch["on"] as? JsonPrimitive)?.content == "t") patch = patch.with("on", JsonPrimitive(!state.bool("on")))
            patch.int("pdel", 0).takeIf { it > 0 }?.let { presets = JsonObject(presets - it.toString()) }
            patch.int("psave", 0).takeIf { it > 0 }?.let { savePreset(it, patch) }
            if (patch.containsKey("psave") || patch.containsKey("pdel")) { changed = true; return@synchronized }

            if (patch.bool("rb")) { bootAt = System.currentTimeMillis(); rtUntil = 0; transFrom = null }
            if (patch.containsKey("np") && playlist != null) playNextAt = 0L
            if (!fromPlaylist && (patch.containsKey("seg") || patch.containsKey("ps"))) stopPlaylist()

            val ps = patch.int("ps", 0)
            if (ps > 0) {
                val preset = presets.obj(ps.toString())
                val pl = preset?.obj("playlist")
                if (pl != null && !fromPlaylist) {
                    startPlaylist(ps, pl)
                } else if (preset != null) {
                    val content = JsonObject(preset.filterKeys { it !in setOf("n", "ql", "playlist", "ib", "sb") })
                    state = StatePatch.apply(state, content)
                    state = state.with("ps", JsonPrimitive(ps))
                }
            }

            val nl = patch.obj("nl")
            if (nl?.bool("on") == true && state.obj("nl")?.bool("on") != true) { nlStart = System.currentTimeMillis(); nlStartBri = state.int("bri", 128) }

            val clean = JsonObject(patch.filterKeys { it !in setOf("v", "time", "tt", "rb", "np", "ps", "psave", "pdel", "n", "ql", "ib", "sb") })
            state = StatePatch.apply(state, clean)
            if (ps <= 0 && (patch.containsKey("seg") || patch.containsKey("bri") || patch.containsKey("on")) && !fromPlaylist) state = state.with("ps", JsonPrimitive(-1))
            fixSegments()

            if (state != before) {
                changed = true
                val tt = patch.int("tt", -1).takeIf { it >= 0 } ?: state.int("transition", 7)
                if (tt > 0 && lastFrame.isNotEmpty()) {
                    transFrom = lastFrame.copyOf(); transStart = System.currentTimeMillis(); transMs = tt * 100L
                }
                cacheAt = -1
            }
        }
        if (changed) { notifyClients(); hub.changed(this) }
    }

    private fun savePreset(id: Int, patch: JsonObject) {
        val ib = patch.bool("ib", true); val sb = patch.bool("sb", true)
        val content = buildJsonObject {
            put("n", patch.str("n") ?: "Preset $id")
            patch.str("ql")?.takeIf { it.isNotBlank() }?.let { put("ql", it) }
            val pl = patch.obj("playlist")
            if (pl != null) {
                put("playlist", pl)
            } else {
                put("on", state.bool("on"))
                if (ib) put("bri", state.int("bri"))
                put("transition", state.int("transition", 7))
                put("mainseg", state.int("mainseg"))
                put("seg", JsonArray(state.arr("seg")?.mapNotNull { it as? JsonObject }?.map { s ->
                    JsonObject(s.filterKeys { k -> sb || k !in setOf("start", "stop", "len", "grp", "spc", "of") })
                } ?: emptyList()))
            }
        }
        presets = JsonObject(presets.toMutableMap().apply { put(id.toString(), content) })
    }

    private fun startPlaylist(id: Int, pl: JsonObject) {
        playlist = pl; playlistId = id; playIndex = 0; playNextAt = 0L
    }

    private fun stopPlaylist() { playlist = null; playlistId = -1 }

    /** Keeps segments inside the strip and at least one segment present, as the firmware does. */
    private fun fixSegments() {
        val len = logicalLen()
        val segs = state.arr("seg")?.mapNotNull { it as? JsonObject }.orEmpty()
        val fixed = segs.mapNotNull { s ->
            val start = s.int("start").coerceIn(0, len)
            val stop = s.int("stop").coerceIn(0, len)
            if (stop <= start) null
            else if (start == s.int("start") && stop == s.int("stop")) s
            else JsonObject(s.toMutableMap().apply { put("start", JsonPrimitive(start)); put("stop", JsonPrimitive(stop)); put("len", JsonPrimitive(stop - start)) })
        }.ifEmpty {
            val base = segs.firstOrNull() ?: defaultSegment(len)
            listOf(JsonObject(base.toMutableMap().apply {
                put("id", JsonPrimitive(0)); put("start", JsonPrimitive(0)); put("stop", JsonPrimitive(len)); put("len", JsonPrimitive(len))
            }))
        }
        if (fixed != segs) state = state.with("seg", JsonArray(fixed))
    }

    fun applyConfig(patch: JsonObject) {
        synchronized(lock) {
            val oldLen = logicalLen()
            cfg = deepMerge(cfg, patch)
            patch.obj("id")?.str("name")?.let { name = it }
            // Recount like WLED does after saving LED settings.
            val led = cfg.obj("hw")?.obj("led")
            if (led != null) {
                val total = buses().maxOfOrNull { it.int("start") + it.int("len") } ?: 0
                cfg = deepMerge(cfg, buildJsonObject { putJsonObject("hw") { putJsonObject("led") { put("total", total) } } })
            }
            growMainSegment(oldLen)
            fixSegments()
            cacheAt = -1
        }
        notifyClients(); hub.changed(this)
    }

    private fun applyLedmap(map: JsonObject?) {
        synchronized(lock) {
            val oldLen = logicalLen()
            ledmap = map?.takeIf { it.arr("map") != null }
            val w = ledmap?.int("width") ?: 0
            // An identity map (how the app removes one) is the same as none.
            val m = ledmap?.arr("map")
            if (w <= 0 && m != null && m.withIndex().all { (i, v) -> (v as? JsonPrimitive)?.content == i.toString() }) ledmap = null
            growMainSegment(oldLen)
            fixSegments()
            cacheAt = -1
        }
        notifyClients(); hub.changed(this)
    }

    /** A single segment that covered the whole strip keeps covering it when the length changes. */
    private fun growMainSegment(oldLen: Int) {
        val newLen = logicalLen()
        val segs = state.arr("seg")?.mapNotNull { it as? JsonObject }.orEmpty()
        if (segs.size == 1 && segs[0].int("start") == 0 && segs[0].int("stop") == oldLen && newLen != oldLen) {
            state = state.with("seg", JsonArray(listOf(JsonObject(segs[0].toMutableMap().apply {
                put("stop", JsonPrimitive(newLen)); put("len", JsonPrimitive(newLen))
            }))))
        }
    }

    fun rename(newName: String) {
        synchronized(lock) { name = newName; cfg = deepMerge(cfg, buildJsonObject { putJsonObject("id") { put("name", newName) } }) }
        notifyClients(); hub.changed(this)
    }

    // ------------------------------------------------------------------ realtime

    /** A realtime frame (3 bytes per LED from LED 0), as DDP delivers it. */
    fun realtime(rgb: ByteArray, from: String) {
        val n = rgb.size / 3
        val f = IntArray(n) { ((rgb[it * 3].toInt() and 0xFF) shl 16) or ((rgb[it * 3 + 1].toInt() and 0xFF) shl 8) or (rgb[it * 3 + 2].toInt() and 0xFF) }
        realtime(f, from)
    }

    internal fun realtime(frame: IntArray, from: String) {
        val wasLive: Boolean
        synchronized(lock) {
            wasLive = System.currentTimeMillis() < rtUntil
            rtFrame = frame; rtFrom = from
            rtUntil = System.currentTimeMillis() + 2_500
            cacheAt = -1
        }
        if (!wasLive) notifyClients()
    }

    // ------------------------------------------------------------------ time-based behaviour

    /** Runs the nightlight, playlists and realtime timeout. Called a few times a second. */
    internal fun tick(now: Long) {
        var changed = false
        var advance: Pair<Int, Long>? = null
        synchronized(lock) {
            val nl = state.obj("nl")
            if (nl?.bool("on") == true) {
                val dur = nl.int("dur", 60).coerceAtLeast(1) * 60_000L
                val f = ((now - nlStart).toFloat() / dur).coerceIn(0f, 1f)
                val target = nl.int("tbri")
                if (f >= 1f) {
                    state = state.with("nl", JsonObject(nl.toMutableMap().apply { put("on", JsonPrimitive(false)) }))
                    state = if (target == 0) state.with("on", JsonPrimitive(false)) else state.with("bri", JsonPrimitive(target))
                    changed = true
                } else if (nl.int("mode", 1) != 0) {
                    val bri = (nlStartBri + (target - nlStartBri) * f).toInt().coerceIn(1, 255)
                    if (bri != state.int("bri")) { state = state.with("bri", JsonPrimitive(bri)); changed = true }
                }
            }
            val pl = playlist
            if (pl != null && now >= playNextAt) {
                val ids = pl.arr("ps")?.map { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }.orEmpty()
                if (ids.isEmpty()) stopPlaylist() else {
                    if (playNextAt != 0L) playIndex++
                    val repeat = pl.int("repeat", 0)
                    if (playIndex >= ids.size) {
                        if (repeat == 1 || (repeat > 1 && playIndex >= ids.size * repeat)) {
                            val end = pl.int("end", 0)
                            stopPlaylist()
                            if (end > 0) advance = end to 0L
                        } else playIndex %= ids.size
                    }
                    if (playlist != null) {
                        val durs = pl.arr("dur")?.map { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: 100 }
                        val d = (durs?.getOrNull(playIndex % ids.size) ?: durs?.firstOrNull() ?: (pl.int("dur", 100))).coerceAtLeast(1)
                        playNextAt = now + d * 100L
                        advance = ids[playIndex % ids.size] to 0L
                    }
                }
            }
            if (rtUntil in 1..now) { rtUntil = 0; rtFrame = null; changed = true }
        }
        advance?.let { (ps, _) ->
            applyState(buildJsonObject { put("ps", ps) }, fromPlaylist = playlist != null)
            synchronized(lock) { if (playlist != null) state = state.with("ps", JsonPrimitive(ps)) }
        }
        if (changed) { synchronized(lock) { cacheAt = -1 }; notifyClients(); hub.changed(this) }
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Every LED's colour (0xRRGGBB) at [now], indexed like the controller's LEDs, including
     * network outputs (whose part is also passed on to those controllers).
     */
    fun frame(now: Long): IntArray {
        val out: IntArray
        val forward = ArrayList<Pair<List<Int>, IntArray>>()
        synchronized(lock) {
            if (now - cacheAt in 0..9) return cache
            val total = buses().maxOfOrNull { it.int("start") + it.int("len") } ?: 0
            out = IntArray(total)
            val live = now < rtUntil && state.int("lor") == 0
            val rt = rtFrame
            if (live && rt != null) {
                System.arraycopy(rt, 0, out, 0, minOf(rt.size, total))
            } else if (state.bool("on")) {
                renderEffects(now, out)
                val bri = state.int("bri", 128) / 255f
                for (i in out.indices) out[i] = SimEffects.scale(out[i], bri)
            }
            limitPower(out)
            // Fade from the previous look.
            val from = transFrom
            if (from != null) {
                val f = ((now - transStart).toFloat() / transMs).coerceIn(0f, 1f)
                if (f >= 1f) transFrom = null
                else for (i in out.indices) out[i] = SimEffects.blend(from.getOrElse(i) { 0 }, out[i], f)
            }
            for (b in buses().filter(::isNetwork)) {
                val start = b.int("start"); val len = b.int("len")
                val ip = b.arr("pin")?.map { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }.orEmpty()
                if (ip.size == 4 && start < total) forward += ip to out.copyOfRange(start, minOf(total, start + len))
            }
            lastFrame = out
            cache = out; cacheAt = now
        }
        for ((ip, part) in forward) hub.forward(ip, part, host)
        return out
    }

    private fun renderEffects(now: Long, out: IntArray) {
        val map = ledmap
        val width = map?.int("width") ?: 0
        val height = map?.int("height") ?: 0
        val cells = map?.arr("map")?.map { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: -1 }
        val is2D = width > 0 && height > 0 && cells != null
        val logical = IntArray(if (is2D) width else out.size)

        for (s in state.arr("seg")?.mapNotNull { it as? JsonObject }.orEmpty()) {
            if (!s.bool("on", true)) continue
            val start = s.int("start"); val stop = s.int("stop").coerceAtMost(logical.size)
            val len = stop - start
            if (len <= 0) continue
            val grp = s.int("grp", 1).coerceAtLeast(1); val spc = s.int("spc").coerceAtLeast(0)
            val step = grp + spc
            val full = (len + step - 1) / step
            val mirror = s.bool("mi")
            val vlen = if (mirror) (full + 1) / 2 else full
            val id = s.int("id")
            val t = if (s.bool("frz")) frozenAt.getOrPut(id) { now } else { frozenAt.remove(id); now }
            val cols = s.arr("col")
            val colors = IntArray(3) { i ->
                val c = WledColor.parse(cols?.getOrNull(i))
                if (c.w > 0) SimEffects.blend(c.rgb and 0xFFFFFF, 0xFFFFFF, c.w / 255f).let { mixed -> SimEffects.maxRgb(mixed, c.rgb and 0xFFFFFF) } else c.rgb and 0xFFFFFF
            }
            val params = SegParams(
                len = vlen, fx = s.int("fx"), sx = s.int("sx", 128), ix = s.int("ix", 128),
                c1 = s.int("c1", 128), c2 = s.int("c2", 128), c3 = s.int("c3", 16),
                colors = colors, pal = s.int("pal"), seed = seed * 31 + id,
            )
            val buf = IntArray(vlen)
            SimEffects.render(params, t, buf)
            val segBri = s.int("bri", 255) / 255f
            val rev = s.bool("rev"); val offset = s.int("of")
            fun put(v: Int, c: Int) {
                for (g in 0 until grp) {
                    var idx = v * step + g
                    if (idx >= len) break
                    idx = (idx + offset) % len
                    if (rev) idx = len - 1 - idx
                    logical[start + idx] = c
                }
            }
            for (v in 0 until vlen) {
                val c = if (segBri < 1f) SimEffects.scale(buf[v], segBri) else buf[v]
                put(v, c)
                if (mirror) put(full - 1 - v, c)
            }
        }

        if (is2D) {
            // 1D effects on a 2D map run across its width, the same down each column.
            for (i in cells!!.indices) {
                val phys = cells[i]
                if (phys in out.indices) out[phys] = logical[i % width]
            }
        } else {
            System.arraycopy(logical, 0, out, 0, minOf(logical.size, out.size))
        }
    }

    /** WLED's automatic brightness limiter: scale everything down to stay within the power supply. */
    private fun limitPower(out: IntArray) {
        val led = cfg.obj("hw")?.obj("led")
        val perLed = (led?.int("ledma", 55) ?: 55).coerceAtLeast(1)
        val max = led?.int("maxpwr", 0) ?: 0
        var sum = 0L
        for (c in out) sum += ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        val idle = out.size
        val draw = (sum * perLed / 765).toInt() + idle
        if (max > 0 && draw > max) {
            val k = ((max - idle).coerceAtLeast(0).toFloat() / (draw - idle).coerceAtLeast(1))
            for (i in out.indices) out[i] = SimEffects.scale(out[i], k)
            powerMa = max
        } else powerMa = draw
    }

    // ------------------------------------------------------------------ clients

    private fun notifyClients() {
        if (sockets.isEmpty()) return
        val msg = buildJsonObject { put("state", synchronized(lock) { stateOut() }); put("info", info()) }.toString()
        sockets.forEach { it.push(msg) }
    }

    internal fun welcome(): String = buildJsonObject { put("state", synchronized(lock) { stateOut() }); put("info", info()) }.toString()

    /** Short description for the simulator screen: what's showing now. */
    fun summary(): String = synchronized(lock) {
        when {
            System.currentTimeMillis() < rtUntil && state.int("lor") == 0 -> "Live from ${rtFrom.ifBlank { "the app" }}"
            !state.bool("on") -> "Off"
            else -> {
                val segs = state.arr("seg")?.mapNotNull { it as? JsonObject }.orEmpty()
                val fx = segs.map { SimCatalog.effects.getOrElse(it.int("fx")) { "?" } }.distinct()
                val pl = if (playlistId > 0) " · playlist" else ""
                fx.joinToString(", ") + pl
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

        private fun JsonObject.with(key: String, value: JsonElement) = JsonObject(toMutableMap().apply { put(key, value) })

        private fun deepMerge(a: JsonObject, b: JsonObject): JsonObject {
            val m = a.toMutableMap()
            for ((k, v) in b) {
                val old = m[k]
                m[k] = if (old is JsonObject && v is JsonObject) deepMerge(old, v) else v
            }
            return JsonObject(m)
        }

        /** The file name and contents of the first file in a multipart upload. */
        private fun parseUpload(body: String): Pair<String, String> {
            val name = Regex("""filename="([^"]*)"""").find(body)?.groupValues?.get(1) ?: ""
            val start = body.indexOf("\r\n\r\n").takeIf { it >= 0 }?.plus(4) ?: return name to ""
            val boundary = body.substringBefore("\r\n")
            val end = body.indexOf("\r\n$boundary", start).takeIf { it >= 0 } ?: body.length
            return name to body.substring(start, end)
        }

        fun defaultSegment(len: Int, color: Int = 0xFFA000, fx: Int = 0, pal: Int = 0) = buildJsonObject {
            put("id", 0); put("start", 0); put("stop", len); put("len", len)
            put("grp", 1); put("spc", 0); put("of", 0); put("on", true); put("frz", false)
            put("bri", 255); put("cct", 127); put("set", 0); put("n", "")
            put("col", buildJsonArray {
                add(buildJsonArray { add((color shr 16) and 0xFF); add((color shr 8) and 0xFF); add(color and 0xFF) })
                add(buildJsonArray { add(0); add(0); add(0) })
                add(buildJsonArray { add(0); add(0); add(0) })
            })
            put("fx", fx); put("sx", 128); put("ix", 128); put("pal", pal)
            put("c1", 128); put("c2", 128); put("c3", 16)
            put("sel", true); put("rev", false); put("mi", false)
            put("o1", false); put("o2", false); put("o3", false); put("si", 0); put("m12", 0)
        }

        fun defaultState(len: Int, seg: JsonObject = defaultSegment(len)) = buildJsonObject {
            put("on", true); put("bri", 128); put("transition", 7); put("ps", -1); put("pl", -1)
            putJsonObject("nl") { put("on", false); put("dur", 60); put("mode", 1); put("tbri", 0); put("rem", -1) }
            putJsonObject("udpn") { put("send", false); put("recv", true); put("sgrp", 1); put("rgrp", 1) }
            put("lor", 0); put("mainseg", 0)
            put("seg", JsonArray(listOf(seg)))
        }

        /** WS2812B-style strip ([type] 22) or RGBW ([type] 30) on one output. */
        fun defaultCfg(name: String, len: Int, type: Int = 22) = buildJsonObject {
            putJsonArray("rev") { add(1); add(0) }
            putJsonObject("id") { put("mdns", "wled-sim"); put("name", name) }
            putJsonObject("hw") {
                putJsonObject("led") {
                    put("total", len); put("maxpwr", 0); put("ledma", 55); put("cct", false); put("cr", false)
                    put("cb", 0); put("fps", 42); put("rgbwm", 255); put("ld", false)
                    putJsonArray("ins") {
                        add(buildJsonObject {
                            put("start", 0); put("len", len); putJsonArray("pin") { add(16) }; put("order", 0)
                            put("rev", false); put("skip", 0); put("type", type); put("ref", false); put("rgbwm", 0); put("freq", 0)
                        })
                    }
                }
            }
            putJsonObject("light") {
                put("scale-bri", 100); put("pal-mode", 0)
                putJsonObject("tr") { put("mode", true); put("dur", 7); put("pal", 0) }
            }
            putJsonObject("def") { put("ps", 0); put("on", true); put("bri", 128) }
        }
    }
}
