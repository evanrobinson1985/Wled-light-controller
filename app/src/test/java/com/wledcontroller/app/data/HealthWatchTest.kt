package com.wledcontroller.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthWatchTest {
    private fun kinds(events: List<HealthWatch.Event>) = events.map { it.kind }

    @Test
    fun offlineAfterTwoMissesThenBack() {
        val w = HealthWatch()
        assertTrue(w.read("d", "Roof", HealthWatch.Reading(true, 100, 80), 0).isEmpty())
        assertTrue(w.read("d", "Roof", HealthWatch.Reading(false), 20_000).isEmpty()) // one blip isn't enough
        assertEquals(listOf(HealthWatch.Event.Kind.OFFLINE), kinds(w.read("d", "Roof", HealthWatch.Reading(false), 40_000)))
        assertTrue(w.read("d", "Roof", HealthWatch.Reading(false), 60_000).isEmpty()) // said once
        // Back, with a fresh uptime: that's the comeback, not a separate restart warning.
        assertEquals(listOf(HealthWatch.Event.Kind.BACK), kinds(w.read("d", "Roof", HealthWatch.Reading(true, 5, 80), 80_000)))
    }

    @Test
    fun noticesARestartBetweenReadings() {
        val w = HealthWatch()
        w.read("d", "Tree", HealthWatch.Reading(true, 3600, 80), 0)
        w.read("d", "Tree", HealthWatch.Reading(true, 3620, 80), 20_000)
        val e = w.read("d", "Tree", HealthWatch.Reading(true, 12, 80), 40_000)
        assertEquals(listOf(HealthWatch.Event.Kind.RESTARTED), kinds(e))
        assertTrue(e.single().message.startsWith("Tree restarted"))
    }

    @Test
    fun weakSignalAfterAFewReadingsAndNotTooOften() {
        val w = HealthWatch()
        val weak = HealthWatch.Reading(true, 100, 15)
        assertTrue(w.read("d", "Bush", weak, 0).isEmpty())
        assertTrue(w.read("d", "Bush", weak, 20_000).isEmpty())
        assertEquals(listOf(HealthWatch.Event.Kind.WEAK_SIGNAL), kinds(w.read("d", "Bush", weak.copy(uptimeSec = 140), 40_000)))
        assertTrue(w.read("d", "Bush", weak.copy(uptimeSec = 160), 60_000).isEmpty())
    }
}
