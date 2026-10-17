package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Applies WLED JSON state patches locally so the UI can update optimistically, and merges
 * outgoing patches so fast slider drags collapse into a single request.
 *
 * Mirrors WLED's own semantics: a `seg` *object* applies to all selected segments (or the main
 * segment when none is selected); a `seg` *array* targets segments by `id`; `stop: 0` deletes a
 * segment; an empty colour array leaves that colour slot unchanged.
 */
object StatePatch {

    /** Keys that are commands rather than state, so they are not applied locally. */
    private val COMMAND_KEYS = setOf("ps", "psave", "pdel", "rb", "v", "time", "pl", "np", "ib", "sb", "n", "ql")

    fun apply(state: JsonObject, patch: JsonObject): JsonObject {
        val m = state.toMutableMap()
        for ((k, v) in patch) {
            val old = m[k]
            when {
                k in COMMAND_KEYS -> Unit
                k == "seg" -> m["seg"] = applySegments(state, old as? JsonArray ?: JsonArray(emptyList()), v)
                v is JsonObject && old is JsonObject -> m[k] = deepMerge(old, v)
                else -> m[k] = v
            }
        }
        return JsonObject(m)
    }

    private fun applySegments(state: JsonObject, segs: JsonArray, patch: JsonElement): JsonArray {
        val list = segs.mapNotNull { it as? JsonObject }.toMutableList()
        fun indexOfId(id: Int) = list.indexOfFirst { it.int("id") == id }

        fun applyTo(id: Int, p: JsonObject) {
            val idx = indexOfId(id)
            if (idx >= 0) {
                if (p.containsKey("stop") && p.int("stop") == 0) list.removeAt(idx)
                else list[idx] = mergeSegment(list[idx], p)
            } else if (p.int("stop") > 0) {
                val base = list.firstOrNull() ?: JsonObject(emptyMap())
                val seeded = base.toMutableMap().apply { put("id", JsonPrimitive(id)); put("n", JsonPrimitive("")) }
                list.add(mergeSegment(JsonObject(seeded), p))
            }
        }

        when (patch) {
            is JsonObject -> {
                if (patch.containsKey("id")) {
                    applyTo(patch.int("id"), patch)
                } else {
                    val selected = list.filter { it.bool("sel") }.map { it.int("id") }
                    val targets = selected.ifEmpty { listOf(state.int("mainseg")) }
                    targets.forEach { applyTo(it, patch) }
                }
            }
            is JsonArray -> patch.forEachIndexed { i, el ->
                val p = el as? JsonObject ?: return@forEachIndexed
                applyTo(if (p.containsKey("id")) p.int("id") else i, p)
            }
            else -> Unit
        }
        list.sortBy { it.int("id") }
        return JsonArray(list)
    }

    private fun mergeSegment(seg: JsonObject, p: JsonObject): JsonObject {
        val m = seg.toMutableMap()
        for ((k, v) in p) {
            val old = m[k]
            m[k] = if (k == "col" && old is JsonArray && v is JsonArray) mergeColors(old, v) else v
        }
        m["len"] = JsonPrimitive((m["stop"].asInt() - m["start"].asInt()).coerceAtLeast(0))
        return JsonObject(m)
    }

    /** Colour slots: an empty array in [b] keeps the slot from [a]. */
    private fun mergeColors(a: JsonArray, b: JsonArray): JsonArray {
        val n = maxOf(a.size, b.size)
        return JsonArray(List(n) { i ->
            val nb = b.getOrNull(i)
            if (nb == null || (nb is JsonArray && nb.isEmpty())) a.getOrNull(i) ?: JsonArray(emptyList()) else nb
        })
    }

    private fun deepMerge(a: JsonObject, b: JsonObject): JsonObject {
        val m = a.toMutableMap()
        for ((k, v) in b) {
            val old = m[k]
            m[k] = if (old is JsonObject && v is JsonObject) deepMerge(old, v) else v
        }
        return JsonObject(m)
    }

    /** Two outgoing patches can be merged unless their `seg` shapes differ or either is a command. */
    fun canMerge(a: JsonObject, b: JsonObject): Boolean {
        if (a.keys.any { it in COMMAND_KEYS } || b.keys.any { it in COMMAND_KEYS }) return false
        val sa = a["seg"]; val sb = b["seg"]
        if (sa == null || sb == null) return true
        if (sa is JsonObject && sb is JsonObject) return sa["id"] == sb["id"]
        return sa is JsonArray && sb is JsonArray
    }

    fun mergePatches(a: JsonObject, b: JsonObject): JsonObject {
        val m = a.toMutableMap()
        for ((k, v) in b) {
            val old = m[k]
            m[k] = when {
                k == "seg" && old is JsonArray && v is JsonArray -> mergeSegArrays(old, v)
                k == "col" && old is JsonArray && v is JsonArray -> mergeColors(old, v)
                old is JsonObject && v is JsonObject -> mergePatches(old, v)
                else -> v
            }
        }
        return JsonObject(m)
    }

    private fun mergeSegArrays(a: JsonArray, b: JsonArray): JsonArray {
        val out = a.mapNotNull { it as? JsonObject }.toMutableList()
        for (el in b) {
            val p = el as? JsonObject ?: continue
            val idx = out.indexOfFirst { it["id"] != null && it["id"] == p["id"] }
            if (idx >= 0) out[idx] = mergePatches(out[idx], p) else out.add(p)
        }
        return JsonArray(out)
    }
}
