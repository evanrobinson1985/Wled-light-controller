package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * WLED's XML state (what it publishes over MQTT on "[topic]/v"):
 * `<vs><ac>128</ac><cl>255</cl><cl>160</cl><cl>0</cl><cs>…</cs>…<fx>0</fx><sx>128</sx>…</vs>`.
 */
object WledXml {
    private val tag = Regex("<(\\w+)>([^<]*)</\\1>")

    /** The XML as a JSON state patch (power, brightness, colours, effect, palette, nightlight), or null if it isn't WLED's. */
    fun statePatch(xml: String): JsonObject? {
        val values = HashMap<String, MutableList<String>>()
        tag.findAll(xml).forEach { m -> values.getOrPut(m.groupValues[1]) { ArrayList() } += m.groupValues[2].trim() }
        fun int(k: String, i: Int = 0) = values[k]?.getOrNull(i)?.toIntOrNull()
        val ac = int("ac") ?: return null
        fun color(k: String, white: Int?) = buildJsonArray {
            repeat(3) { add(int(k, it) ?: 0) }
            if (white != null && white >= 0) add(white)
        }
        return buildJsonObject {
            // WLED reports brightness 0 when off.
            put("on", ac > 0)
            if (ac > 0) put("bri", ac)
            putJsonObject("seg") {
                if (values["cl"]?.size == 3) put("col", buildJsonArray {
                    add(color("cl", int("wv")))
                    if (values["cs"]?.size == 3) add(color("cs", int("ws")))
                })
                int("fx")?.let { put("fx", it) }
                int("sx")?.let { put("sx", it) }
                int("ix")?.let { put("ix", it) }
                int("fp")?.let { put("pal", it) }
            }
            int("nl")?.let { putJsonObject("nl") { put("on", it == 1) } }
        }
    }

    /** The preset WLED says is running (0 = none). */
    fun preset(xml: String): Int = Regex("<ps>(-?\\d+)</ps>").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
