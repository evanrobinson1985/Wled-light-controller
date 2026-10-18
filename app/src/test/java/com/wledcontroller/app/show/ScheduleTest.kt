package com.wledcontroller.app.show

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {

    private val evenings = ScheduleEntry(id = "a", playlistId = "p", startMin = 17 * 60, endMin = 22 * 60)

    // 4 Dec 2026 is a Friday (day 5).
    private val lateFriday = ScheduleEntry(id = "b", playlistId = "p", days = setOf(5), startMin = 22 * 60, endMin = 2 * 60)

    @Test
    fun dailyWindow() {
        assertTrue(evenings.isActive(LocalDateTime.of(2026, 12, 1, 18, 0)))
        assertFalse(evenings.isActive(LocalDateTime.of(2026, 12, 1, 22, 0)))
        assertFalse(evenings.isActive(LocalDateTime.of(2026, 12, 1, 16, 59)))
        assertEquals(LocalDateTime.of(2026, 12, 1, 22, 0), evenings.endTime(LocalDateTime.of(2026, 12, 1, 18, 0)))
        assertEquals(LocalDateTime.of(2026, 12, 2, 17, 0), evenings.nextStart(LocalDateTime.of(2026, 12, 1, 23, 0)))
    }

    @Test
    fun windowPastMidnightBelongsToTheDayItStarted() {
        assertTrue(lateFriday.isActive(LocalDateTime.of(2026, 12, 4, 23, 0)))
        assertEquals(LocalDateTime.of(2026, 12, 5, 2, 0), lateFriday.endTime(LocalDateTime.of(2026, 12, 4, 23, 0)))
        // Early Saturday is still Friday night's show.
        assertTrue(lateFriday.isActive(LocalDateTime.of(2026, 12, 5, 1, 0)))
        assertEquals(LocalDateTime.of(2026, 12, 5, 2, 0), lateFriday.endTime(LocalDateTime.of(2026, 12, 5, 1, 0)))
        // Saturday night isn't scheduled; nor is early Friday (Thursday night).
        assertFalse(lateFriday.isActive(LocalDateTime.of(2026, 12, 5, 23, 0)))
        assertFalse(lateFriday.isActive(LocalDateTime.of(2026, 12, 4, 1, 0)))
        assertEquals(LocalDateTime.of(2026, 12, 11, 22, 0), lateFriday.nextStart(LocalDateTime.of(2026, 12, 5, 3, 0)))
    }

    @Test
    fun disabledOrDaylessNeverRuns() {
        assertFalse(evenings.copy(enabled = false).isActive(LocalDateTime.of(2026, 12, 1, 18, 0)))
        assertNull(evenings.copy(days = emptySet()).nextStart(LocalDateTime.of(2026, 12, 1, 12, 0)))
    }
}
