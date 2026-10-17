package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// ---------- Lenient JSON helpers (WLED field types vary between firmware versions) ----------

fun JsonElement?.asInt(default: Int = 0): Int {
    val p = this as? JsonPrimitive ?: return default
    return p.intOrNull ?: p.doubleOrNull?.toInt() ?: p.booleanOrNull?.let { if (it) 1 else 0 } ?: default
}

fun JsonElement?.asBool(default: Boolean = false): Boolean {
    val p = this as? JsonPrimitive ?: return default
    return p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: default
}

fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull

fun JsonObject.int(key: String, default: Int = 0) = this[key].asInt(default)
fun JsonObject.bool(key: String, default: Boolean = false) = this[key].asBool(default)
fun JsonObject.str(key: String): String? = this[key].asString()
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

// ---------- Colors ----------

data class WledColor(val r: Int, val g: Int, val b: Int, val w: Int = 0) {
    /** Opaque ARGB int of the RGB part. */
    val rgb: Int get() = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    fun toJson(withWhite: Boolean) = buildJsonArray {
        add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
        if (withWhite) add(JsonPrimitive(w))
    }

    companion object {
        val BLACK = WledColor(0, 0, 0, 0)

        fun fromRgb(rgb: Int, w: Int = 0) =
            WledColor((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, w)

        fun parse(el: JsonElement?): WledColor = when (el) {
            is JsonArray -> WledColor(
                el.getOrNull(0).asInt(), el.getOrNull(1).asInt(),
                el.getOrNull(2).asInt(), el.getOrNull(3).asInt()
            )
            is JsonPrimitive -> el.contentOrNull?.let { hex ->
                val v = hex.removePrefix("#").toLongOrNull(16) ?: return@let BLACK
                if (hex.removePrefix("#").length > 6) {
                    WledColor(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt(), ((v shr 24) and 0xFF).toInt())
                } else fromRgb(v.toInt())
            } ?: BLACK
            else -> BLACK
        }
    }
}

// ---------- State ----------

data class Segment(
    val id: Int,
    val start: Int,
    val stop: Int,
    val grp: Int,
    val spc: Int,
    val of: Int,
    val on: Boolean,
    val bri: Int,
    val cct: Int,
    val name: String?,
    val colors: List<WledColor>,
    val fx: Int,
    val sx: Int,
    val ix: Int,
    val c1: Int,
    val c2: Int,
    val c3: Int,
    val o1: Boolean,
    val o2: Boolean,
    val o3: Boolean,
    val pal: Int,
    val sel: Boolean,
    val rev: Boolean,
    val mi: Boolean,
    val frz: Boolean,
) {
    val len: Int get() = (stop - start).coerceAtLeast(0)
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: "Segment $id"
    fun color(slot: Int): WledColor = colors.getOrNull(slot) ?: WledColor.BLACK

    companion object {
        fun parse(o: JsonObject) = Segment(
            id = o.int("id"),
            start = o.int("start"),
            stop = o.int("stop"),
            grp = o.int("grp", 1),
            spc = o.int("spc"),
            of = o.int("of"),
            on = o.bool("on", true),
            bri = o.int("bri", 255),
            cct = o.int("cct", 127),
            name = o.str("n"),
            colors = o.arr("col")?.map { WledColor.parse(it) } ?: emptyList(),
            fx = o.int("fx"),
            sx = o.int("sx", 128),
            ix = o.int("ix", 128),
            c1 = o.int("c1", 128),
            c2 = o.int("c2", 128),
            c3 = o.int("c3", 16),
            o1 = o.bool("o1"),
            o2 = o.bool("o2"),
            o3 = o.bool("o3"),
            pal = o.int("pal"),
            sel = o.bool("sel"),
            rev = o.bool("rev"),
            mi = o.bool("mi"),
            frz = o.bool("frz"),
        )
    }
}

data class Nightlight(val on: Boolean, val dur: Int, val mode: Int, val tbri: Int, val rem: Int)

data class WledState(
    val on: Boolean,
    val bri: Int,
    val transition: Int,
    val ps: Int,
    val pl: Int,
    val nl: Nightlight,
    val udpSend: Boolean,
    val udpRecv: Boolean,
    val lor: Int,
    val mainseg: Int,
    val segments: List<Segment>,
) {
    /** The segment that the colour / effect controls reflect: first selected, else the main segment. */
    val activeSegment: Segment?
        get() = segments.firstOrNull { it.sel }
            ?: segments.firstOrNull { it.id == mainseg }
            ?: segments.firstOrNull()

    companion object {
        fun parse(o: JsonObject): WledState {
            val nl = o.obj("nl")
            val udpn = o.obj("udpn")
            return WledState(
                on = o.bool("on"),
                bri = o.int("bri", 128),
                transition = o.int("transition", 7),
                ps = o.int("ps", -1),
                pl = o.int("pl", -1),
                nl = Nightlight(
                    on = nl?.bool("on") ?: false,
                    dur = nl?.int("dur", 60) ?: 60,
                    mode = nl?.int("mode", 1) ?: 1,
                    tbri = nl?.int("tbri") ?: 0,
                    rem = nl?.int("rem", -1) ?: -1,
                ),
                udpSend = udpn?.bool("send") ?: false,
                udpRecv = udpn?.bool("recv") ?: false,
                lor = o.int("lor"),
                mainseg = o.int("mainseg"),
                segments = o.arr("seg")
                    ?.mapNotNull { (it as? JsonObject)?.let(Segment::parse) }
                    ?.filter { it.stop > 0 }
                    ?.sortedBy { it.id }
                    ?: emptyList(),
            )
        }
    }
}

// ---------- Info ----------

data class WledInfo(
    val name: String,
    val version: String,
    val ledCount: Int,
    val hasWhite: Boolean,
    val hasCct: Boolean,
    val powerMa: Int,
    val maxPowerMa: Int,
    val fps: Int,
    val maxSegments: Int,
    val ip: String,
    val mac: String,
    val arch: String,
    val freeHeap: Int,
    val uptimeSec: Int,
    val wifiSignal: Int,
    val rssi: Int,
    val wifiChannel: Int,
    val live: Boolean,
    val liveMode: String?,
    val liveIp: String?,
    val udpPort: Int,
    val brand: String,
    val product: String,
) {
    companion object {
        fun parse(o: JsonObject): WledInfo {
            val leds = o.obj("leds")
            val wifi = o.obj("wifi")
            // "lc" is a capability bitmask: bit0 RGB, bit1 white channel, bit2 CCT.
            val lc = leds?.get("lc")?.asInt(-1) ?: -1
            val hasWhite = if (lc >= 0) lc and 0x02 != 0 else (leds?.bool("rgbw") == true || leds?.bool("wv") == true)
            val hasCct = if (lc >= 0) lc and 0x04 != 0 else leds?.bool("cct") == true
            return WledInfo(
                name = o.str("name") ?: "WLED",
                version = o.str("ver") ?: "?",
                ledCount = leds?.int("count") ?: 0,
                hasWhite = hasWhite,
                hasCct = hasCct,
                powerMa = leds?.int("pwr") ?: 0,
                maxPowerMa = leds?.int("maxpwr") ?: 0,
                fps = leds?.int("fps") ?: 0,
                maxSegments = leds?.int("maxseg", 16) ?: 16,
                ip = o.str("ip") ?: "",
                mac = o.str("mac") ?: "",
                arch = o.str("arch") ?: "",
                freeHeap = o.int("freeheap"),
                uptimeSec = o.int("uptime"),
                wifiSignal = wifi?.int("signal") ?: 0,
                rssi = wifi?.int("rssi") ?: 0,
                wifiChannel = wifi?.int("channel") ?: 0,
                live = o.bool("live"),
                liveMode = o.str("lm"),
                liveIp = o.str("lip"),
                udpPort = o.int("udpport"),
                brand = o.str("brand") ?: "",
                product = o.str("product") ?: "",
            )
        }
    }
}

// ---------- Effects metadata (/json/fxdata) ----------

data class EffectMeta(
    /** Labels for speed, intensity, custom1..3; null means the control is unused by this effect. */
    val sliders: List<String?>,
    /** Labels for checkbox options 1..3; null means unused. */
    val options: List<String?>,
    /** Labels for the three colour slots; null means unused. */
    val colors: List<String?>,
    val usesPalette: Boolean,
    val is2D: Boolean,
    val isAudio: Boolean,
) {
    companion object {
        val SLIDER_KEYS = listOf("sx", "ix", "c1", "c2", "c3")
        val OPTION_KEYS = listOf("o1", "o2", "o3")
        private val DEFAULT_SLIDERS = listOf("Speed", "Intensity", "Custom 1", "Custom 2", "Custom 3")
        private val DEFAULT_OPTIONS = listOf("Option 1", "Option 2", "Option 3")
        private val DEFAULT_COLORS = listOf("Primary", "Secondary", "Tertiary")

        val UNKNOWN = EffectMeta(
            sliders = listOf("Speed", "Intensity", null, null, null),
            options = listOf(null, null, null),
            colors = DEFAULT_COLORS,
            usesPalette = true, is2D = false, isAudio = false,
        )

        fun parse(fx: Int, data: String?): EffectMeta {
            if (data == null) return UNKNOWN
            if (data.isEmpty()) {
                return if (fx == 0) UNKNOWN.copy(sliders = List(5) { null }, usesPalette = false) else UNKNOWN
            }
            val parts = data.split(';')
            val params = parts[0].split(',')
            fun label(i: Int, defaults: List<String>, offset: Int): String? {
                val raw = params.getOrNull(i + offset)?.trim() ?: return null
                return when {
                    raw.isEmpty() -> null
                    raw == "!" -> defaults[i]
                    else -> raw
                }
            }
            val sliders = List(5) { label(it, DEFAULT_SLIDERS, 0) }
            val options = List(3) { label(it, DEFAULT_OPTIONS, 5) }
            val colors = if (parts.size < 2) DEFAULT_COLORS else {
                val c = parts[1].split(',')
                List(3) { i ->
                    val raw = c.getOrNull(i)?.trim()
                    when {
                        raw.isNullOrEmpty() -> null
                        raw == "!" -> DEFAULT_COLORS[i]
                        else -> raw
                    }
                }
            }
            val usesPalette = parts.size < 3 || parts[2].isNotBlank()
            val flags = parts.getOrNull(3) ?: ""
            return EffectMeta(
                sliders = sliders,
                options = options,
                colors = colors,
                usesPalette = usesPalette,
                is2D = flags.contains('2'),
                isAudio = flags.contains('v') || flags.contains('f'),
            )
        }
    }
}

/** WLED reserves some effect / palette slots with placeholder names. */
fun isRealName(name: String) = name.isNotBlank() && name != "RSVD" && name != "-"

// ---------- Presets ----------

data class Preset(val id: Int, val name: String, val isPlaylist: Boolean, val quickLabel: String?)

// ---------- Palette previews (/json/palx) ----------

/** One gradient stop. Exactly one of [color] / [slot] / [random] describes the colour. */
data class PaletteStop(val pos: Float, val color: Int? = null, val slot: Int? = null, val random: Boolean = false)
