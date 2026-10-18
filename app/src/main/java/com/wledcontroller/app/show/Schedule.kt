package com.wledcontroller.app.show

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId

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

/** What a schedule time is measured from. */
@Serializable
enum class TimeKind(val label: String) {
    CLOCK("Time"),
    SUNSET("Sunset"),
    DUSK("Dusk"),
    SUNRISE("Sunrise"),
}

/**
 * When a playlist runs: on [days] (1 = Monday … 7 = Sunday), optionally only between
 * [seasonStart] and [seasonEnd] ("MM-dd", may wrap round the new year), from a start to an end
 * time. Each time is a clock time ([startMin] / [endMin], minutes after midnight) or relative to
 * sunset, dusk or sunrise plus an offset in minutes (needs the show's location).
 */
@Serializable
data class ScheduleEntry(
    val id: String,
    val playlistId: String,
    val days: Set<Int> = (1..7).toSet(),
    val startMin: Int = 17 * 60,
    val endMin: Int = 22 * 60,
    val lightsOffAtEnd: Boolean = false,
    val enabled: Boolean = true,
    val startKind: TimeKind = TimeKind.CLOCK,
    val startOffsetMin: Int = 0,
    val endKind: TimeKind = TimeKind.CLOCK,
    val endOffsetMin: Int = 0,
    val seasonStart: String? = null,
    val seasonEnd: String? = null,
) {
    private fun resolve(kind: TimeKind, clock: Int, offset: Int, date: LocalDate, place: GeoPoint?, zone: ZoneId): Int {
        if (kind == TimeKind.CLOCK || place == null) return clock
        val base = when (kind) {
            TimeKind.SUNSET -> Solar.sunset(date, place, zone)
            TimeKind.DUSK -> Solar.dusk(date, place, zone)
            TimeKind.SUNRISE -> Solar.sunrise(date, place, zone)
            TimeKind.CLOCK -> clock
        } ?: return clock
        return (base + offset).coerceIn(0, 2 * 1440 - 1)
    }

    /** The window for [date] in minutes after that day's midnight; the end may run past midnight. */
    fun window(date: LocalDate, place: GeoPoint? = null, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
        val s = resolve(startKind, startMin, startOffsetMin, date, place, zone)
        // Sunrise ends the night: it's the next morning's.
        val e = if (endKind == TimeKind.SUNRISE) resolve(endKind, endMin, endOffsetMin, date.plusDays(1), place, zone) + 1440
        else resolve(endKind, endMin, endOffsetMin, date, place, zone)
        return s to if (e <= s) e + 1440 else e
    }

    /** Whether [date] is in the season (always, when no season is set). */
    fun inSeason(date: LocalDate): Boolean {
        val from = seasonStart?.let(::parseMonthDay) ?: return true
        val to = seasonEnd?.let(::parseMonthDay) ?: return true
        val d = MonthDay.from(date)
        return if (!from.isAfter(to)) !d.isBefore(from) && !d.isAfter(to) else !d.isBefore(from) || !d.isAfter(to)
    }

    private fun runsOn(date: LocalDate) = enabled && date.dayOfWeek.value in days && inSeason(date)

    /** Whether the window is open at [now]. A window past midnight belongs to the day it started. */
    fun isActive(now: LocalDateTime, place: GeoPoint? = null, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        openWindowEnd(now, place, zone) != null

    /** When the current window closes (assumes [isActive]). */
    fun endTime(now: LocalDateTime, place: GeoPoint? = null, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        openWindowEnd(now, place, zone) ?: now

    private fun openWindowEnd(now: LocalDateTime, place: GeoPoint?, zone: ZoneId): LocalDateTime? {
        val minute = now.hour * 60 + now.minute
        for (back in 0..1) {
            val day = now.toLocalDate().minusDays(back.toLong())
            if (!runsOn(day)) continue
            val (s, e) = window(day, place, zone)
            val m = minute + back * 1440
            if (m in s until e) return day.atStartOfDay().plusMinutes(e.toLong())
        }
        return null
    }

    /** Next time the window opens after [now], or null if it never will. */
    fun nextStart(now: LocalDateTime, place: GeoPoint? = null, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        if (!enabled || days.isEmpty()) return null
        for (d in 0..370) {
            val day = now.toLocalDate().plusDays(d.toLong())
            if (!runsOn(day)) continue
            val start = day.atStartOfDay().plusMinutes(window(day, place, zone).first.toLong())
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
        fun time(kind: TimeKind, clock: Int, offset: Int) =
            if (kind == TimeKind.CLOCK) formatMinutes(clock) else kind.label + formatOffset(offset)
        val season = if (seasonStart != null && seasonEnd != null) " · ${formatMonthDay(seasonStart)}–${formatMonthDay(seasonEnd)}" else ""
        return "$dayText · ${time(startKind, startMin, startOffsetMin)}–${time(endKind, endMin, endOffsetMin)}$season"
    }
}

/** " + 30 min", " − 1 h 15 min", or "" for no offset. */
fun formatOffset(min: Int): String {
    if (min == 0) return ""
    val a = kotlin.math.abs(min)
    val text = if (a >= 60) "${a / 60} h" + (if (a % 60 != 0) " ${a % 60} min" else "") else "$a min"
    return (if (min > 0) " + " else " − ") + text
}

fun parseMonthDay(s: String): MonthDay? = runCatching { MonthDay.parse("--$s") }.getOrNull()

fun formatMonthDay(s: String): String =
    parseMonthDay(s)?.let { java.time.format.DateTimeFormatter.ofPattern("MMM d").format(it.atYear(2024)) } ?: s

fun formatMinutes(min: Int): String {
    val h = min / 60 % 24; val m = min % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}
