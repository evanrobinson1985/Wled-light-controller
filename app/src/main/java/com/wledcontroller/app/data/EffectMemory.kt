package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Each effect's own settings (speed, intensity, the extra sliders and options), remembered per
 * controller, so choosing an effect again brings back exactly how it was set rather than WLED's
 * defaults for it.
 */
object EffectMemory {
    private val SLIDERS = listOf("sx", "ix", "c1", "c2", "c3")
    private val OPTIONS = listOf("o1", "o2", "o3")

    fun key(deviceId: String, fx: Int) = "$deviceId#$fx"

    /** The settings showing on [seg] now, to remember for its effect. */
    fun values(seg: Segment): Map<String, Int> = mapOf(
        "sx" to seg.sx, "ix" to seg.ix, "c1" to seg.c1, "c2" to seg.c2, "c3" to seg.c3,
        "o1" to if (seg.o1) 1 else 0, "o2" to if (seg.o2) 1 else 0, "o3" to if (seg.o3) 1 else 0,
    )

    /**
     * The command that plays effect [fx] with its [saved] settings. With none saved yet, only
     * [defaultSpeed] (from the light type) is set, if there is one.
     */
    fun patch(fx: Int, saved: Map<String, Int>?, defaultSpeed: Int? = null): JsonObject = buildJsonObject {
        put("on", true)
        putJsonObject("seg") {
            put("fx", fx)
            if (saved != null) {
                SLIDERS.forEach { k -> saved[k]?.let { put(k, it) } }
                OPTIONS.forEach { k -> saved[k]?.let { put(k, it != 0) } }
            } else if (defaultSpeed != null) {
                put("sx", defaultSpeed)
            }
        }
    }
}
