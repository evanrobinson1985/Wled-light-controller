package com.wledcontroller.app.show

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SolarScheduleTest {

    private val nyc = GeoPoint(40.7128, -74.0060)
    private val ny = ZoneId.of("America/New_York")

    @Test
    fun sunsetAndDuskInNewYork() {
        // Published times: Jun 21 sunset 8:31 PM EDT, dusk ~9:03 PM; Dec 21 sunset 4:32 PM EST.
        assertEquals(20 * 60 + 31f, Solar.sunset(LocalDate.of(2026, 6, 21), nyc, ny)!!.toFloat(), 4f)
        assertEquals(21 * 60 + 3f, Solar.dusk(LocalDate.of(2026, 6, 21), nyc, ny)!!.toFloat(), 4f)
        assertEquals(16 * 60 + 32f, Solar.sunset(LocalDate.of(2026, 12, 21), nyc, ny)!!.toFloat(), 4f)
        assertEquals(7 * 60 + 17f, Solar.sunrise(LocalDate.of(2026, 12, 21), nyc, ny)!!.toFloat(), 4f)
    }

    @Test
    fun sunsetInTokyoAndLondon() {
        // Tokyo Jun 21 sunset ~7:00 PM JST; London Dec 21 sunset ~3:54 PM GMT.
        assertEquals(19 * 60f, Solar.sunset(LocalDate.of(2026, 6, 21), GeoPoint(35.68, 139.69), ZoneId.of("Asia/Tokyo"))!!.toFloat(), 5f)
        assertEquals(15 * 60 + 54f, Solar.sunset(LocalDate.of(2026, 12, 21), GeoPoint(51.507, -0.128), ZoneId.of("Europe/London"))!!.toFloat(), 5f)
    }

    @Test
    fun startsFifteenMinutesAfterSunset() {
        val e = ScheduleEntry("a", "p", startKind = TimeKind.SUNSET, startOffsetMin = 15, endMin = 22 * 60)
        val day = LocalDate.of(2026, 12, 21)
        val (s, end) = e.window(day, nyc, ny)
        assertEquals(16 * 60 + 47f, s.toFloat(), 4f)
        assertEquals(22 * 60, end)
        assertFalse(e.isActive(LocalDateTime.of(2026, 12, 21, 16, 38), nyc, ny))
        assertTrue(e.isActive(LocalDateTime.of(2026, 12, 21, 16, 58), nyc, ny))
        // Without a location the clock time stands in.
        assertEquals(17 * 60, e.window(day).first)
    }

    @Test
    fun runsUntilSunrise() {
        val e = ScheduleEntry("a", "p", startMin = 20 * 60, endKind = TimeKind.SUNRISE)
        val (_, end) = e.window(LocalDate.of(2026, 12, 21), nyc, ny)
        assertEquals(1440 + 7 * 60 + 17f, end.toFloat(), 5f)
        assertTrue(e.isActive(LocalDateTime.of(2026, 12, 22, 6, 0), nyc, ny))
    }

    @Test
    fun seasonWrapsRoundTheNewYear() {
        val e = ScheduleEntry("a", "p", seasonStart = "11-25", seasonEnd = "01-06")
        assertTrue(e.inSeason(LocalDate.of(2026, 12, 31)))
        assertTrue(e.inSeason(LocalDate.of(2027, 1, 6)))
        assertFalse(e.inSeason(LocalDate.of(2027, 1, 7)))
        assertFalse(e.inSeason(LocalDate.of(2026, 11, 24)))
        assertFalse(e.isActive(LocalDateTime.of(2026, 7, 4, 18, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 25, 17, 0), e.nextStart(LocalDateTime.of(2026, 7, 4, 18, 0)))
        assertEquals("Every day · 5:00 PM–10:00 PM · Nov 25–Jan 6", e.describe())
    }
}
