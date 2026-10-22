package com.wledcontroller.app.house

import com.wledcontroller.app.show.Raster
import com.wledcontroller.app.show.Rasters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LiveTextTest {
    @Test
    fun wordsForEachKind() {
        val today = LocalDate.of(2026, 12, 13)
        assertEquals("12 days until Christmas", LiveText(kind = LiveText.Kind.COUNTDOWN).text(today))
        assertEquals("1 day until Christmas", LiveText(kind = LiveText.Kind.COUNTDOWN, countdownTo = "2026-12-14").text(today))
        assertEquals("It's Christmas!", LiveText(kind = LiveText.Kind.COUNTDOWN, countdownTo = "2026-12-13").text(today))
        assertEquals("7:05 PM", LiveText(kind = LiveText.Kind.CLOCK).text(today, LocalTime.of(19, 5)))
        assertEquals("38°F", LiveText(kind = LiveText.Kind.TEMPERATURE).text(today, temperatureF = 37.6f))
        assertEquals(LocalDate.of(2027, 12, 25), LiveText.nextChristmas(LocalDate.of(2026, 12, 26)))
    }

    @Test
    fun textLightsOnlyTheChosenProps() {
        Rasters.textMaker = { Raster(4, 1, IntArray(4) { 0xFFFFFFFF.toInt() }) }
        try {
            val panel = LightRun("p", "Panel", "d", 0, 100, points = listOf(MapPoint(0f, 0f), MapPoint(0.5f, 0.5f)),
                prop = PropType.MATRIX, model = PropModel(ModelKind.MATRIX, strings = 10, nodes = 10))
            val roof = LightRun("r", "Roof", "d", 100, 10, points = listOf(MapPoint(0.6f, 0f), MapPoint(1f, 0f)))
            val map = HouseMap(aspect = 1f, runs = listOf(panel, roof))
            val leds = map.placedLeds()
            val colors = IntArray(leds.size) { 0x0000FF }
            LiveTextOverlay(map, leds).apply(colors, LiveText(runIds = listOf("p"), color = 0xFFFF0000.toInt(), speed = 0f), "HI", 0f)
            val panelLeds = leds.indices.filter { leds[it].runIndex == 0 }
            assertTrue(panelLeds.any { colors[it] == 0xFF0000 })
            assertTrue(leds.indices.filter { leds[it].runIndex == 1 }.all { colors[it] == 0x0000FF })
        } finally {
            Rasters.textMaker = null
        }
    }
}
