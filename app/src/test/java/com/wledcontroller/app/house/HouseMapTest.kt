package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HouseMapTest {

    private fun close(expected: MapPoint, actual: MapPoint?) {
        assertEquals(expected.x, actual!!.x, 1e-4f)
        assertEquals(expected.y, actual.y, 1e-4f)
    }

    @Test
    fun tracedLedsSpreadEvenlyByDistance() {
        // An L: 0.5 across (×2 aspect = 1.0 long), then 1.0 down. 5 LEDs → every 0.5.
        val run = LightRun("r", "Roof", "d", 0, 5, points = listOf(MapPoint(0f, 0f), MapPoint(0.5f, 0f), MapPoint(0.5f, 1f)))
        val p = run.tracedPositions(aspect = 2f)
        assertEquals(5, p.size)
        close(MapPoint(0f, 0f), p[0])
        close(MapPoint(0.25f, 0f), p[1])
        close(MapPoint(0.5f, 0f), p[2])
        close(MapPoint(0.5f, 0.5f), p[3])
        close(MapPoint(0.5f, 1f), p[4])
    }

    @Test
    fun perLedPointsOverrideTraceAndMissingStayDark() {
        val run = LightRun("r", "Bush", "d", 10, 3, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f)), ledPoints = listOf(MapPoint(0.2f, 0.2f), null))
        val p = run.ledPositions(1f)
        assertEquals(3, p.size)
        close(MapPoint(0.2f, 0.2f), p[0])
        assertNull(p[1]); assertNull(p[2])
        val placed = HouseMap(runs = listOf(run)).placedLeds()
        assertEquals(1, placed.size)
        assertEquals(10, placed[0].index)
    }

    @Test
    fun fillGapsInterpolatesInsideTheSpanOnly() {
        val filled = listOf(null, MapPoint(0f, 0f), null, null, MapPoint(0.3f, 0.6f), null).fillGaps()
        assertNull(filled[0])
        close(MapPoint(0.1f, 0.2f), filled[2])
        close(MapPoint(0.2f, 0.4f), filled[3])
        assertNull(filled[5])
    }
}
