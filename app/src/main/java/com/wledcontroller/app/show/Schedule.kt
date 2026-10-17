package com.wledcontroller.app.show

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Shows played back to back. */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val sequenceIds: List<String>,
    val loop: Boolean = true,
    /** Pause between shows, seconds. */
    val gapSec: Int = 3,
)

/** When a playlist runs: on [days] (1 = Monday … 7 = Sunday) from [startMin] to [endMin] (minutes after midnight). */
@Serializable
data class ScheduleEntry(
    val id: String,
    val playlistId: String,
    val days: Set<Int> = (1..7).toSet(),
    val startMin: Int = 17 * 60,
    val endMin: Int = 22 * 60,
    val lightsOffAtEnd: Boolean = false,
    val enabled: Boolean = true,
) {
    private val crossesMidnight get() = endMin <= startMin

    /** Whether the window is open at [now]. A window past midnight belongs to the day it started. */
    fun isActive(now: LocalDateTime): Boolean {
        if (!enabled) return false
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = now.dayOfWeek.minus(1).value
        return if (!crossesMidnight) today in days && minute >= startMin && minute < endMin
        else (today in days && minute >= startMin) || (yesterday in days && minute < endMin)
    }

    /** When the current window closes (assumes [isActive]). */
    fun endTime(now: LocalDateTime): LocalDateTime {
        val minute = now.hour * 60 + now.minute
        val base = now.truncatedTo(ChronoUnit.DAYS).plusMinutes(endMin.toLong())
        return if (crossesMidnight && minute >= startMin) base.plusDays(1) else base
    }

    /** Next time the window opens after [now], or null if it never will. */
    fun nextStart(now: LocalDateTime): LocalDateTime? {
        if (!enabled || days.isEmpty()) return null
        for (d in 0..7) {
            val day = now.truncatedTo(ChronoUnit.DAYS).plusDays(d.toLong())
            if (day.dayOfWeek.value !in days) continue
            val start = day.plusMinutes(startMin.toLong())
            if (start.isAfter(now)) return start
        }
        return null
    }

    fun describe(): String {
        val dayText = when (days) {
            (1..7).toSet() -> "Every day"
            (1..5).toSet() -> "Weekdays"
            setOf(6, 7) -> "Weekends"
            else -> days.sorted().joinToString(" ") { DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
        }
        return "$dayText · ${formatMinutes(startMin)}–${formatMinutes(endMin)}"
    }
}

fun formatMinutes(min: Int): String {
    val h = min / 60 % 24; val m = min % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}
