package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a controller's LEDs are showing, as WLED reports it: [colors] (0xRRGGBB) are every LED,
 * or for a long strip an even sample of them, in order; [at] when it arrived.
 */
class LiveLeds(val colors: IntArray, val at: Long = System.currentTimeMillis()) {
    /** [count] colours for a strip of that many LEDs, each from the nearest sampled one. */
    fun spread(count: Int): IntArray {
        if (colors.isEmpty() || count <= 0) return IntArray(count)
        if (colors.size == count) return colors
        return IntArray(count) { i -> colors[(i.toLong() * colors.size / count).toInt().coerceIn(0, colors.size - 1)] }
    }

    companion object {
        /**
         * WLED's live stream over its socket: "L", a version (2 for a matrix, with its width
         * and height next), then red, green and blue for each LED shown.
         */
        fun fromBinary(b: ByteArray): LiveLeds? {
            if (b.size < 2 || b[0] != 'L'.code.toByte()) return null
            val start = if (b[1].toInt() == 2) 4 else 2
            val n = (b.size - start) / 3
            if (n <= 0) return null
            return LiveLeds(IntArray(n) { i ->
                val o = start + i * 3
                ((b[o].toInt() and 0xFF) shl 16) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() and 0xFF)
            })
        }

        /** The same over HTTP (/json/live): {"leds": ["FF8800", …]}. */
        fun fromJson(e: JsonElement): LiveLeds? {
            val leds = ((e as? JsonObject)?.get("leds") as? JsonArray) ?: return null
            val colors = leds.mapNotNull { (it as? JsonPrimitive)?.content?.takeLast(6)?.toIntOrNull(16) }
            return if (colors.isEmpty()) null else LiveLeds(colors.toIntArray())
        }
    }
}
