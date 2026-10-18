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

    @Test
    fun fixedSpacingPlacesLedsFromTheStart() {
        // Scale: the full picture width (aspect 2 → 2 picture heights) is 200 cm, so 100 cm per height.
        val map = HouseMap(aspect = 2f, scaleA = MapPoint(0f, 0.5f), scaleB = MapPoint(1f, 0.5f), scaleCm = 200f)
        assertEquals(100f, map.cmPerHeight!!, 1e-3f)
        // A 100 cm line (x 0 → 0.5); LEDs every 30 cm, 5 of them: the last runs on past the end.
        val run = LightRun("r", "Roof", "d", 0, 5, points = listOf(MapPoint(0f, 0f), MapPoint(0.5f, 0f)), spacingCm = 30f)
        val p = map.positions(run)
        close(MapPoint(0f, 0f), p[0])
        close(MapPoint(0.15f, 0f), p[1])
        close(MapPoint(0.45f, 0f), p[3])
        close(MapPoint(0.6f, 0f), p[4])
        // Without a scale the run spreads evenly as before.
        close(MapPoint(0.5f, 0f), run.ledPositions(2f)[4])
    }

    @Test
    fun lengthsReadNaturally() {
        assertEquals("4 in", formatLength(10.16f, inches = true))
        assertEquals("40 ft", formatLength(40 * 30.48f, inches = true))
        assertEquals("3 ft 6 in", formatLength(42 * 2.54f, inches = true))
        assertEquals("1 ft 6 in", formatLength(18 * 2.54f, inches = true))
        assertEquals("1.67 m", formatLength(166.7f, inches = false))
    }
}
