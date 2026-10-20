package com.wledcontroller.app.house

import org.junit.Assert.assertTrue
import org.junit.Test

class GhostsTest {
    /** Share of a grid of LEDs over the house that are in a ghost's shadow at time [t]. */
    private fun darkShare(p: HouseEffectParams, t: Float): Float {
        var dark = 0; var n = 0
        for (i in 0 until 80) for (j in 0 until 50) {
            val c = HouseRenderer.color(p, i / 79f, j / 49f, t, i * 50 + j, i)
            val lum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
            if (lum < 60) dark++
            n++
        }
        return dark / n.toFloat()
    }

    @Test
    fun theHouseIsLitExceptWhereGhostsAre() {
        val one = HouseEffectParams(effect = HouseEffect.GHOSTS, colors = listOf(0xFFFFFF, 0xFFFFFF), count = 1, size = 0.5f, brightness = 1f)
        val share = darkShare(one, 3f)
        assertTrue("a ghost casts a shadow: $share", share > 0.005f)
        assertTrue("most of the house stays lit: $share", share < 0.5f)

        // More ghosts and bigger ghosts darken more of the house (averaged over a few moments).
        fun avg(p: HouseEffectParams) = listOf(1f, 4f, 9f, 15f).map { darkShare(p, it) }.average()
        assertTrue(avg(one.copy(count = 6)) > avg(one))
        assertTrue(avg(one.copy(size = 1f)) > avg(one.copy(size = 0f)))
    }

    @Test
    fun ghostsMove() {
        val p = HouseEffectParams(effect = HouseEffect.GHOSTS, colors = listOf(0xFFFFFF), count = 2, speed = 0.8f, brightness = 1f)
        val a = (0 until 80).map { HouseRenderer.color(p, it / 79f, 0.4f, 2f, it, it) }
        val b = (0 until 80).map { HouseRenderer.color(p, it / 79f, 0.4f, 6f, it, it) }
        assertTrue(a != b)
    }
}
