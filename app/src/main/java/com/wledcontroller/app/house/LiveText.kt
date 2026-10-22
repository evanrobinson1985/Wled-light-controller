package com.wledcontroller.app.house

import com.wledcontroller.app.show.Rasters
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Words scrolling on matrices over the whole-house effect: a message, a countdown, the time or the temperature. */
@Serializable
data class LiveText(
    val kind: Kind = Kind.MESSAGE,
    val message: String = "Merry Christmas!",
    /** For countdowns: the day (yyyy-MM-dd) and what it is. */
    val countdownTo: String = "",
    val countdownName: String = "Christmas",
    /** Which props show it (ids of runs). */
    val runIds: List<String> = emptyList(),
    val color: Int = 0xFFFFFFFF.toInt(),
    /** 0 holds still, more scrolls faster. */
    val speed: Float = 0.5f,
) {
    @Serializable
    enum class Kind(val label: String) { MESSAGE("Message"), COUNTDOWN("Countdown"), CLOCK("Clock"), TEMPERATURE("Temperature") }

    /** The words to show now. [temperatureF] is the outside temperature, when known. */
    fun text(today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now(), temperatureF: Float? = null): String = when (kind) {
        Kind.MESSAGE -> message
        Kind.CLOCK -> now.format(DateTimeFormatter.ofPattern("h:mm a"))
        Kind.TEMPERATURE -> temperatureF?.let { "%.0f°F".format(it) } ?: ""
        Kind.COUNTDOWN -> {
            val day = runCatching { LocalDate.parse(countdownTo) }.getOrNull() ?: nextChristmas(today)
            when (val d = ChronoUnit.DAYS.between(today, day)) {
                0L -> "It's $countdownName!"
                1L -> "1 day until $countdownName"
                in 2..Long.MAX_VALUE -> "$d days until $countdownName"
                else -> ""
            }
        }
    }

    companion object {
        fun nextChristmas(today: LocalDate): LocalDate = LocalDate.of(today.year, 12, 25).let { if (it.isBefore(today)) it.plusYears(1) else it }
    }
}

/**
 * Puts [LiveText] on its props: each lit LED of a chosen prop takes the text colour where the
 * letters are, and dims elsewhere so the words stand out.
 */
class LiveTextOverlay(map: HouseMap, private val leds: List<PlacedLed>) {
    /** For every LED: its run's id and where it sits in that run's box (0..1, v down). */
    private val runIds = leds.map { map.runs.getOrNull(it.runIndex)?.id }
    private val u = FloatArray(leds.size); private val v = FloatArray(leds.size); private val aspectOf = FloatArray(leds.size)

    init {
        leds.indices.groupBy { leds[it].runIndex }.forEach { (_, ks) ->
            val x0 = ks.minOf { leds[it].x }; val x1 = ks.maxOf { leds[it].x }
            val y0 = ks.minOf { leds[it].y }; val y1 = ks.maxOf { leds[it].y }
            val w = (x1 - x0).coerceAtLeast(1e-4f); val h = (y1 - y0).coerceAtLeast(1e-4f)
            for (k in ks) {
                u[k] = (leds[k].x - x0) / w; v[k] = (leds[k].y - y0) / h
                aspectOf[k] = (w * map.aspect / h).coerceIn(0.2f, 20f)
            }
        }
    }

    fun apply(colors: IntArray, live: LiveText, words: String, t: Float) {
        if (words.isBlank() || live.runIds.isEmpty()) return
        val r = Rasters.text(words) ?: return
        val chosen = live.runIds.toSet()
        val tc = live.color and 0xFFFFFF
        for (k in leds.indices) {
            if (runIds[k] !in chosen) continue
            val aspect = aspectOf[k]
            // Letters three-quarters of the prop's height, scrolling right to left (or centred when still).
            val h = 0.75f
            val w = r.width.toFloat() / r.height * h / aspect
            val left = if (live.speed < 0.02f) (1f - w) / 2f else 1f - HouseRenderer.frac(t * (0.05f + live.speed * 0.3f) / (1f + w)) * (1f + w)
            val px = r.sample((u[k] - left) / w, (v[k] - (1f - h) / 2f) / h)
            val alpha = ((px ushr 24) and 0xFF) / 255f
            colors[k] = if (alpha > 0.3f) HouseRenderer.scale(tc, alpha) else HouseRenderer.scale(colors[k], 0.25f)
        }
    }
}
