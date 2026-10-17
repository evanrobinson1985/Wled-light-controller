package com.wledcontroller.app.data

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * A holiday colour scheme. WLED segments hold three colours, which the "* Color Gradient"
 * palette blends into a smooth ramp (or "* Colors Only" repeats as distinct bands).
 */
data class HolidayTheme(
    val id: String,
    val name: String,
    val colors: List<Int>,
    /** Effects that suit the theme, best first; matched by name against the device's list. */
    val effects: List<String>,
    /** Distinct bands of colour (e.g. candy cane) rather than a blended ramp. */
    val bands: Boolean,
    /** Window when the theme is suggested; may wrap around the new year. */
    val seasonStart: MonthDay,
    val seasonEnd: MonthDay,
    val speed: Int = 128,
    val intensity: Int = 128,
)

object HolidayThemes {

    private fun md(month: Int, day: Int) = MonthDay.of(month, day)
    private fun c(hex: Long) = hex.toInt() or (0xFF shl 24)

    val all: List<HolidayTheme> = listOf(
        HolidayTheme(
            "new_year", "New Year's Eve", listOf(c(0xFFD27A), c(0xFFFFFF), c(0xC0C8FF)),
            listOf("Fireworks", "Starburst", "Fireworks Starburst", "Glitter", "Twinkle"), bands = false,
            seasonStart = md(12, 27), seasonEnd = md(1, 2), speed = 160, intensity = 180,
        ),
        HolidayTheme(
            "lunar_new_year", "Lunar New Year", listOf(c(0xFF0000), c(0xFFB000), c(0xFF3000)),
            listOf("Fire Flicker", "Candle Multi", "Fireworks", "Running"), bands = false,
            seasonStart = md(1, 20), seasonEnd = md(2, 20),
        ),
        HolidayTheme(
            "valentines", "Valentine's Day", listOf(c(0xFF0030), c(0xFF5AA0), c(0xFFD0E0)),
            listOf("Breathe", "Heartbeat", "Fade", "Twinkle"), bands = false,
            seasonStart = md(2, 7), seasonEnd = md(2, 15), speed = 90,
        ),
        HolidayTheme(
            "mardi_gras", "Mardi Gras", listOf(c(0x7A00FF), c(0x00C83C), c(0xFFC000)),
            listOf("Running", "Theater", "Chase 3", "Colorful"), bands = true,
            seasonStart = md(2, 10), seasonEnd = md(3, 5), speed = 150,
        ),
        HolidayTheme(
            "st_patricks", "St. Patrick's Day", listOf(c(0x00FF20), c(0x00A030), c(0xFFD040)),
            listOf("Twinkle", "Sparkle", "Colorwaves", "Running"), bands = false,
            seasonStart = md(3, 10), seasonEnd = md(3, 18),
        ),
        HolidayTheme(
            "easter", "Easter", listOf(c(0xFF9AD0), c(0xFFF080), c(0xA0B0FF)),
            listOf("Colorwaves", "Pacifica", "Breathe", "Palette"), bands = false,
            seasonStart = md(3, 20), seasonEnd = md(4, 25), speed = 70,
        ),
        HolidayTheme(
            "independence", "Independence Day", listOf(c(0xFF0000), c(0xFFFFFF), c(0x0030FF)),
            listOf("Fireworks", "Running", "Theater", "Chase 3"), bands = true,
            seasonStart = md(6, 28), seasonEnd = md(7, 5), speed = 150,
        ),
        HolidayTheme(
            "halloween", "Halloween", listOf(c(0xFF5A00), c(0x8000FF), c(0x40FF00)),
            listOf("Halloween Eyes", "Halloween", "Fire Flicker", "Candle Multi", "Twinkle"), bands = false,
            seasonStart = md(10, 1), seasonEnd = md(11, 1), speed = 110, intensity = 160,
        ),
        HolidayTheme(
            "diwali", "Diwali", listOf(c(0xFFA000), c(0xFF0080), c(0xFFE060)),
            listOf("Candle Multi", "Fire Flicker", "Twinkle", "Glitter"), bands = false,
            seasonStart = md(10, 15), seasonEnd = md(11, 15),
        ),
        HolidayTheme(
            "thanksgiving", "Thanksgiving", listOf(c(0xFF8C00), c(0xC03000), c(0xFFC060)),
            listOf("Candle Multi", "Fire Flicker", "Breathe", "Colorwaves"), bands = false,
            seasonStart = md(11, 15), seasonEnd = md(11, 30), speed = 80,
        ),
        HolidayTheme(
            "hanukkah", "Hanukkah", listOf(c(0x0040FF), c(0xFFFFFF), c(0x80C0FF)),
            listOf("Candle Multi", "Twinkle", "Running", "Breathe"), bands = false,
            seasonStart = md(12, 1), seasonEnd = md(12, 31), speed = 90,
        ),
        HolidayTheme(
            "christmas", "Christmas", listOf(c(0xFF0000), c(0x00C000), c(0xFFD8A0)),
            listOf("Merry Christmas", "Twinkle", "Twinklefox", "Running", "Theater"), bands = true,
            seasonStart = md(11, 25), seasonEnd = md(12, 26), speed = 120,
        ),
        HolidayTheme(
            "winter", "Winter Wonderland", listOf(c(0xFFFFFF), c(0x60C8FF), c(0x2040FF)),
            listOf("Twinklefox", "Twinkle", "Sparkle", "Pacifica", "Colorwaves"), bands = false,
            seasonStart = md(12, 1), seasonEnd = md(2, 28), speed = 90,
        ),
        HolidayTheme(
            "kwanzaa", "Kwanzaa", listOf(c(0xFF0000), c(0x00B020), c(0xFFB000)),
            listOf("Candle Multi", "Running", "Twinkle"), bands = true,
            seasonStart = md(12, 24), seasonEnd = md(1, 1),
        ),
    )

    fun inSeason(theme: HolidayTheme, today: LocalDate = LocalDate.now()): Boolean {
        val d = MonthDay.from(today)
        val s = theme.seasonStart
        val e = theme.seasonEnd
        return if (!s.isAfter(e)) !d.isBefore(s) && !d.isAfter(e) else !d.isBefore(s) || !d.isAfter(e)
    }

    private fun daysUntilStart(theme: HolidayTheme, today: LocalDate): Long {
        var next = theme.seasonStart.atYear(today.year)
        if (next.isBefore(today)) next = theme.seasonStart.atYear(today.year + 1)
        return ChronoUnit.DAYS.between(today, next)
    }

    /** Themes in season first, then the rest in the order they come up. */
    fun forToday(today: LocalDate = LocalDate.now()): List<HolidayTheme> =
        all.sortedWith(compareBy<HolidayTheme>({ !inSeason(it, today) }, { daysUntilStart(it, today) }))

    /**
     * Generates a fresh variation: nudges hue, saturation and order while keeping the colours
     * bright (dim colours look muddy on LEDs) and whites white.
     */
    fun remix(colors: List<Int>, random: Random = Random.Default): List<Int> {
        val out = colors.map { rgb ->
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(rgb, it) }
            if (hsv[1] > 0.15f) {
                hsv[0] = (hsv[0] + random.nextInt(-16, 17) + 360f) % 360f
                hsv[1] = (hsv[1] * (0.8f + random.nextFloat() * 0.3f)).coerceIn(0.35f, 1f)
            }
            hsv[2] = (0.8f + random.nextFloat() * 0.2f).coerceAtMost(1f)
            android.graphics.Color.HSVToColor(hsv)
        }
        return if (random.nextFloat() < 0.35f) out.shuffled(random) else out
    }
}
