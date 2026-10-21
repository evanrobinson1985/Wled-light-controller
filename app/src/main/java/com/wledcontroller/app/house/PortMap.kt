package com.wledcontroller.app.house

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One output (port) of a controller: its LEDs [start] until [start] + [length], on [label]. */
data class Port(val start: Int, val length: Int, val label: String) {
    val end get() = start + length
}

/** A stretch of a controller's LEDs: a prop, unused LEDs, or two props on the same LEDs. */
data class PortSpan(val start: Int, val end: Int, val kind: Kind, val names: List<String>) {
    enum class Kind { PROP, UNUSED, OVERLAP, PAST_END }
    val count get() = end - start
}

/**
 * What's wired where on a controller, like xLights' controller visualiser: its props laid out
 * along its LEDs, with gaps, props sharing LEDs, and props running past its last LED.
 */
object PortMap {

    /** The controller's outputs from WLED's config (hw.led.ins), or one covering everything. */
    fun ports(cfg: JsonObject?, ledCount: Int): List<Port> {
        val ins = ((cfg?.get("hw") as? JsonObject)?.get("led") as? JsonObject)?.get("ins") as? JsonArray
        val list = ins?.mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: return@mapIndexedNotNull null
            val start = o["start"]?.jsonPrimitive?.intOrNull ?: return@mapIndexedNotNull null
            val len = o["len"]?.jsonPrimitive?.intOrNull ?: return@mapIndexedNotNull null
            val pin = (o["pin"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.intOrNull
            Port(start, len, "Output ${i + 1}" + (pin?.let { " (GPIO $it)" } ?: ""))
        }.orEmpty().sortedBy { it.start }
        return list.ifEmpty { if (ledCount > 0) listOf(Port(0, ledCount, "All LEDs")) else emptyList() }
    }

    /** The stretches along a controller with [ledCount] LEDs (0 = unknown) for its [runs]. */
    fun spans(runs: List<LightRun>, ledCount: Int): List<PortSpan> {
        val bounds = (runs.flatMap { listOf(it.startLed, it.endLed) } + 0 + ledCount).filter { it >= 0 }.distinct().sorted()
        val out = ArrayList<PortSpan>()
        for ((a, b) in bounds.zipWithNext()) {
            if (b <= a) continue
            val on = runs.filter { it.startLed < b && it.endLed > a }.map { it.name }
            val kind = when {
                ledCount in 1..a -> PortSpan.Kind.PAST_END
                on.size > 1 -> PortSpan.Kind.OVERLAP
                on.size == 1 -> PortSpan.Kind.PROP
                else -> PortSpan.Kind.UNUSED
            }
            // Joining stretches that are the same prop (or the same gap) again.
            val last = out.lastOrNull()
            if (last != null && last.kind == kind && last.names == on && last.end == a) out[out.lastIndex] = last.copy(end = b)
            else out += PortSpan(a, b, kind, on)
        }
        return out
    }

    /** Which port each prop starts on, for listing them by port. */
    fun portOf(run: LightRun, ports: List<Port>): Port? = ports.firstOrNull { run.startLed >= it.start && run.startLed < it.end }

    /** Props that cross from one port onto the next (fine for WLED, but worth knowing when wiring). */
    fun crossesPorts(run: LightRun, ports: List<Port>): Boolean {
        val p = portOf(run, ports) ?: return false
        return run.endLed > p.end && ports.size > 1
    }
}
