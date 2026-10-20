package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * WLED's live view: the colours its LEDs are showing right now. Over the WebSocket (after
 * sending {"lv":true}) it arrives as binary frames: 'L', a version byte (2 = 2D, followed by
 * width and height), then R, G, B per LED. Over HTTP, /json/live gives {"leds":["RRGGBB",…],"n":step}.
 * Long strips are sampled (every n-th LED), so [expand] spreads the samples back over every LED.
 */
object LiveView {
    /** Colours (0xRRGGBB) from a binary live frame, or null if it isn't one. */
    fun parseBinary(b: ByteArray): IntArray? {
        if (b.size < 2 || b[0] != 'L'.code.toByte()) return null
        val start = if (b[1].toInt() == 2) 4 else 2
        if (b.size < start) return null
        val n = (b.size - start) / 3
        return IntArray(n) { i ->
            val o = start + i * 3
            ((b[o].toInt() and 0xFF) shl 16) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() and 0xFF)
        }
    }

    /** Colours from /json/live, or null if it isn't that. */
    fun parseJson(o: JsonObject): IntArray? {
        val leds = o["leds"] as? JsonArray ?: return null
        return IntArray(leds.size) { i ->
            val hex = (leds[i] as? JsonPrimitive)?.content.orEmpty()
            // Usually RRGGBB; some builds send WWRRGGBB: keep the colour, add the white in.
            val v = hex.toLongOrNull(16) ?: 0L
            if (hex.length > 6) {
                val w = ((v shr 24) and 0xFF).toInt()
                val r = (((v shr 16) and 0xFF).toInt() + w).coerceAtMost(255)
                val g = (((v shr 8) and 0xFF).toInt() + w).coerceAtMost(255)
                val bl = ((v and 0xFF).toInt() + w).coerceAtMost(255)
                (r shl 16) or (g shl 8) or bl
            } else v.toInt() and 0xFFFFFF
        }
    }

    /** One colour per LED for a strip of [count] LEDs from [samples] spread evenly along it. */
    fun expand(samples: IntArray, count: Int): IntArray {
        if (count <= 0 || samples.isEmpty()) return IntArray(0)
        if (samples.size == count) return samples
        return IntArray(count) { i -> samples[(i.toLong() * samples.size / count).toInt().coerceIn(0, samples.lastIndex)] }
    }
}
