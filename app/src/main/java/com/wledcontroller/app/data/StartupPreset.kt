package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Keeps each controller starting up with whatever was last chosen in the app: a preset or
 * playlist that was picked becomes its boot preset; anything else (an effect, colours, a scene)
 * is saved to a "Startup" preset of its own, which becomes the boot preset.
 */
object StartupPreset {
    const val NAME = "Startup (from app)"
    private const val PREFERRED_SLOT = 250

    sealed interface Action
    /** Boot with this existing preset or playlist. */
    data class UsePreset(val id: Int) : Action
    /** Save what's showing to the Startup preset and boot with that. */
    data object SaveCurrent : Action

    /** Segment fields that only pick or name segments; changing them alone isn't a new look. */
    private val NOT_A_LOOK = setOf("id", "sel", "n")

    /** What a command the user sent means for start-up, or null if it doesn't change it (power, brightness …). */
    fun actionFor(patch: JsonObject): Action? {
        (patch["ps"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..250 }?.let { return UsePreset(it) }
        if (patch.keys.any { it in setOf("psave", "pdel", "rb") }) return null
        val segs = when (val s = patch["seg"]) {
            is JsonObject -> listOf(s)
            is JsonArray -> s.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        return if (segs.any { seg -> seg.keys.any { it !in NOT_A_LOOK } }) SaveCurrent else null
    }

    /** The preset slot for the Startup preset: 250, unless that holds one of the user's own. */
    fun slot(presets: List<Preset>?): Int {
        val taken = presets.orEmpty().associateBy { it.id }
        for (id in PREFERRED_SLOT downTo 200) {
            val p = taken[id]
            if (p == null || p.name == NAME) return id
        }
        return PREFERRED_SLOT
    }
}
