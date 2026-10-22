package com.wledcontroller.app.data

/**
 * Watches controllers during shows and says when something goes wrong: one stops answering,
 * comes back, restarts by itself (often a power or memory problem), or has a weak Wi-Fi signal.
 * Fed one reading per controller every so often; pure logic, so it can be tested.
 */
class HealthWatch(
    /** Missed readings in a row before a controller counts as offline. */
    private val missesToOffline: Int = 2,
    private val weakSignal: Int = 25,
    /** Weak readings in a row before saying so, and how long before saying it again. */
    private val weakReadings: Int = 3,
    private val weakRepeatMs: Long = 60 * 60_000L,
) {
    data class Reading(val online: Boolean, val uptimeSec: Int? = null, val signal: Int? = null)

    data class Event(val atMs: Long, val deviceId: String, val name: String, val kind: Kind) {
        enum class Kind { OFFLINE, BACK, RESTARTED, WEAK_SIGNAL }
        val message: String get() = when (kind) {
            Kind.OFFLINE -> "$name stopped answering — check its power and Wi-Fi."
            Kind.BACK -> "$name is back."
            Kind.RESTARTED -> "$name restarted by itself — often a power supply, wiring or memory problem."
            Kind.WEAK_SIGNAL -> "$name has a weak Wi-Fi signal — it may stutter during shows."
        }
    }

    private class State(var misses: Int = 0, var offline: Boolean = false, var uptime: Int? = null, var weak: Int = 0, var lastWeakAt: Long = Long.MIN_VALUE / 2)
    private val states = HashMap<String, State>()

    /** Takes one reading for [deviceId]; returns anything worth telling. */
    @Synchronized
    fun read(deviceId: String, name: String, r: Reading, nowMs: Long): List<Event> {
        val s = states.getOrPut(deviceId) { State() }
        val out = ArrayList<Event>()
        if (!r.online) {
            s.misses++
            if (!s.offline && s.misses >= missesToOffline) { s.offline = true; out += Event(nowMs, deviceId, name, Event.Kind.OFFLINE) }
            return out
        }
        if (s.offline) out += Event(nowMs, deviceId, name, Event.Kind.BACK)
        // Uptime going backwards without us seeing it go offline: it restarted.
        val up = r.uptimeSec
        if (up != null && s.uptime != null && up + 5 < s.uptime!! && !s.offline) out += Event(nowMs, deviceId, name, Event.Kind.RESTARTED)
        s.misses = 0; s.offline = false; s.uptime = up
        val sig = r.signal
        if (sig != null && sig in 1 until weakSignal) {
            s.weak++
            if (s.weak >= weakReadings && nowMs - s.lastWeakAt >= weakRepeatMs) { s.lastWeakAt = nowMs; out += Event(nowMs, deviceId, name, Event.Kind.WEAK_SIGNAL) }
        } else s.weak = 0
        return out
    }

    @Synchronized
    fun reset() = states.clear()
}
