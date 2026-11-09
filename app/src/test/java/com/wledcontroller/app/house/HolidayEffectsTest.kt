package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayEffectsTest {
    private val holidayEffects = HouseEffect.entries.filter { it.isHolidayEffect }

    /** An outline of a house: 400 lights along the roof, down the sides and across the bottom. */
    private val leds = (0 until 400).map { i ->
        val f = i / 400f
        when {
            f < 0.4f -> (f / 0.4f) to 0.2f
            f < 0.6f -> 1f to (0.2f + (f - 0.4f) / 0.2f * 0.7f)
            else -> (1f - (f - 0.6f) / 0.4f) to 0.9f
        }
    }

    private fun frame(fx: HouseEffect, t: Float) = HouseEffectParams().pick(fx).let { p ->
        IntArray(leds.size) { i -> HouseRenderer.color(p, leds[i].first, leds[i].second, t, i, i) }
    }

    private fun brightness(rgb: Int) = ((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)

    @Test fun thereAreHolidayEffectsForTheHolidays() {
        val holidays = holidayEffects.map { it.holiday }.toSet()
        listOf("Christmas", "Halloween", "Independence Day", "Valentine's Day", "Hanukkah", "Thanksgiving", "Easter").forEach {
            assertTrue("$it missing", it in holidays)
        }
        // Each is a lighting effect, not a shadow, and comes in its holiday's colours.
        holidayEffects.forEach { assertTrue(!it.isShadow); assertTrue(it.palette!!.size >= 3) }
    }

    @Test fun pickingOneBringsItsColours() {
        val p = HouseEffectParams().pick(HouseEffect.CANDY_CANE)
        assertEquals(HouseEffect.CANDY_CANE, p.effect)
        assertEquals(HouseEffect.CANDY_CANE.palette, p.colors)
        // A shadow still goes on over it, keeping the effect.
        val withShadow = p.pick(HouseEffect.SLEIGH)
        assertEquals(HouseEffect.CANDY_CANE, withShadow.effect)
        assertEquals(HouseEffect.SLEIGH, withShadow.shadow)
    }

    @Test fun everyHolidayEffectLightsTheHouseAndMoves() {
        holidayEffects.forEach { fx ->
            // Over a few seconds (the thunderstorm and haunted ones are mostly quiet, then sudden).
            val frames = (0 until 40).map { frame(fx, it * 0.25f) }
            val lit = frames.sumOf { f -> f.count { brightness(it) > 30 } }
            assertTrue("${fx.label} is dark", lit > leds.size * 2)
            val changes = frames.zipWithNext().count { (a, b) -> !a.contentEquals(b) }
            assertTrue("${fx.label} doesn't move", changes > 5)
            // Not the same colour everywhere at once.
            assertTrue("${fx.label} is flat", frames.any { f -> f.toSet().size > 3 })
        }
    }

    @Test fun theMenorahLightsOneMoreCandleEachNight() {
        fun litCandles(t: Float): Int {
            val f = frame(HouseEffect.MENORAH, t)
            // The roof row runs left to right: count candles with a warm bright light.
            return (0 until 9).count { slot ->
                val i = ((slot + 0.5f) / 9f * 0.4f * 400).toInt()
                brightness(f[i]) > 300
            }
        }
        val p = HouseEffectParams()
        val night = 1f / ((0.1f + p.speed * 1.9f) * 0.5f)
        assertEquals(2, litCandles(night * 1.5f))
        assertEquals(5, litCandles(night * 4.5f))
    }
}
