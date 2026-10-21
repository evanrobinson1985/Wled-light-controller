package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
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

    @Test
    fun everyHolidayFigureCastsAMovingShadow() {
        HouseEffect.entries.filter { it.figures.isNotEmpty() }.forEach { fx ->
            val p = HouseEffectParams(effect = fx, colors = listOf(0xFFFFFF, 0xFFFFFF), count = 3, size = 0.6f, speed = 0.6f, brightness = 1f)
            val shares = listOf(2f, 5f, 11f, 17f, 23f).map { darkShare(p, it) }
            assertTrue("${fx.label} casts a shadow: $shares", shares.average() > 0.002)
            assertTrue("${fx.label} leaves most of the house lit: $shares", shares.all { it < 0.6f })
            val a = (0 until 80).map { i -> (0 until 50).map { j -> HouseRenderer.color(p, i / 79f, j / 49f, 3f, i, i) } }
            val b = (0 until 80).map { i -> (0 until 50).map { j -> HouseRenderer.color(p, i / 79f, j / 49f, 8f, i, i) } }
            assertTrue("${fx.label} moves", a != b)
        }
    }

    @Test
    fun jackOLanternFacesGlowAndSkullsHaveHoles() {
        // On a plain white house a pumpkin's carved face shows red (the second colour, dimmer than the lit house) inside
        // its shadow, and a skull's eye sockets let the lit house show through its shadow.
        fun frame(fx: HouseEffect, t: Float) = HouseEffectParams(effect = fx, colors = listOf(0xFFFFFF, 0xFF0000), count = 1, size = 1f, brightness = 1f).let { p ->
            (0 until 100).map { i -> (0 until 40).map { j -> HouseRenderer.color(p, i / 99f, j / 39f, t, 0, i) } }
        }
        fun red(c: Int) = ((c shr 16) and 0xFF) in 50..200 && ((c shr 8) and 0xFF) < 30
        fun dark(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF) < 60
        fun lit(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF) > 200

        val pumpkin = frame(HouseEffect.PUMPKINS, 9f)
        val glow = pumpkin.sumOf { col -> col.count(::red) }
        val shadow = pumpkin.sumOf { col -> col.count(::dark) }
        assertTrue("the pumpkin casts a shadow: $shadow", shadow > 50)
        assertTrue("its face glows: $glow", glow > 20)

        // Somewhere a lit pixel has the skull's shadow both above and below it, and both sides.
        val skull = frame(HouseEffect.SKULLS, 3f)
        var holes = 0
        for (i in 6 until 94) for (j in 6 until 34) {
            if (!lit(skull[i][j])) continue
            val enclosed = (1..6).any { dark(skull[i - it][j]) } && (1..6).any { dark(skull[i + it][j]) } &&
                (1..6).any { dark(skull[i][j - it]) } && (1..6).any { dark(skull[i][j + it]) }
            if (enclosed) holes++
        }
        assertTrue("the skull has eye sockets: $holes", holes > 4)
    }

    @Test
    fun walkersAndHoppersCoverTheWholeHeight() {
        // Pumpkins, bunnies, clowns and turkeys go along lines from the bottom of the house up, not just the top half.
        for (fx in listOf(HouseEffect.PUMPKINS, HouseEffect.BUNNIES, HouseEffect.CLOWNS, HouseEffect.TURKEYS)) {
            val p = HouseEffectParams(effect = fx, colors = listOf(0xFFFFFF), count = 3, size = 0.5f, speed = 0.5f, brightness = 1f)
            var low = 0; var high = 0
            for (t in listOf(2f, 5f, 9f, 14f, 20f, 27f)) for (i in 0 until 80) for (j in 0 until 50) {
                val c = HouseRenderer.color(p, i / 79f, j / 49f, t, i * 50 + j, i)
                val lum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
                if (lum < 60) { if (j >= 25) low++ else high++ }
            }
            assertTrue("${fx.label} reach the bottom half: low $low, high $high", low > high / 2)
            assertTrue("${fx.label} still reach the top half: low $low, high $high", high > 0)
        }
    }

    @Test
    fun holidayShadowsPlayOverAnyEffect() {
        val base = HouseEffectParams(effect = HouseEffect.FIREWORKS, colors = listOf(0xFFFFFF, 0xFF0000), brightness = 1f)
        // Picking a shadow keeps the effect and puts the shadow on top; picking it again takes it off.
        val withGhosts = base.pick(HouseEffect.GHOSTS)
        assertEquals(HouseEffect.FIREWORKS, withGhosts.effect)
        assertEquals(HouseEffect.GHOSTS, withGhosts.shadow)
        assertEquals(HouseEffect.SKULLS, withGhosts.pick(HouseEffect.SKULLS).shadow)
        assertEquals(null, withGhosts.pick(HouseEffect.GHOSTS).shadow)
        // Picking another effect keeps the shadow.
        val twinkle = withGhosts.pick(HouseEffect.TWINKLE)
        assertEquals(HouseEffect.TWINKLE, twinkle.effect)
        assertEquals(HouseEffect.GHOSTS, twinkle.shadow)

        // Rendered: wherever there's no figure, it's exactly the effect underneath; the figures darken some of it.
        val plain = HouseEffectParams(effect = HouseEffect.SOLID, colors = listOf(0xFFFFFF), brightness = 1f)
        val shaded = plain.copy(shadow = HouseEffect.PUMPKINS, count = 3, shadowSize = 0.6f)
        var same = 0; var darker = 0
        for (t in listOf(3f, 9f, 15f)) for (i in 0 until 80) for (j in 0 until 50) {
            val a = HouseRenderer.color(plain, i / 79f, j / 49f, t, i, i)
            val b = HouseRenderer.color(shaded, i / 79f, j / 49f, t, i, i)
            if (a == b) same++ else if ((b and 0xFF) < (a and 0xFF)) darker++
        }
        assertTrue("most of the house shows the effect: $same", same > darker)
        assertTrue("the pumpkins shade some of it: $darker", darker > 100)
    }

    @Test
    fun olderSettingsWithTheShadowAsTheEffectStillWork() {
        val old = HouseEffectParams(effect = HouseEffect.WITCHES, size = 0.7f, speed = 0.3f)
        val n = old.normalized()
        assertEquals(HouseEffect.GLOW, n.effect)
        assertEquals(HouseEffect.WITCHES, n.shadow)
        assertEquals(0.7f, n.shadowSize)
        assertEquals(0.3f, n.shadowSpeed)
        // Picking an everyday effect then keeps the witches over it.
        assertEquals(HouseEffect.WITCHES, old.pick(HouseEffect.RAINBOW).shadow)
    }
}
