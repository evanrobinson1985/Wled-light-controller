package com.wledcontroller.app.data

import com.wledcontroller.app.house.HouseEffect
import com.wledcontroller.app.sim.SimCatalog
import com.wledcontroller.app.show.SeqEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectFitTest {

    @Test
    fun everyWledEffectSuitsSomeKindOfLight() {
        val missing = SimCatalog.effects.filter { it != "RSVD" && EffectFit.wled(it).isEmpty() }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun everyKindOfLightHasPlentyOfEffects() {
        val names = SimCatalog.effects.filter { it != "RSVD" }
        LightType.entries.filter { it != LightType.MATRIX }.forEach { type ->
            val n = names.count { type in EffectFit.wled(it) }
            assertTrue("${type.label}: $n WLED effects", n >= 15)
        }
        // Matrices get the 2D effects.
        assertTrue(LightType.MATRIX in EffectFit.wled("2D Matrix"))
        // Every kind of light has show effects and whole-house effects.
        LightType.entries.forEach { type ->
            assertTrue("${type.label} show effects", SeqEffect.entries.count { type in EffectFit.show(it) } >= 3)
        }
        LightType.entries.filter { it != LightType.ARCH_CANE }.forEach { type ->
            assertTrue("${type.label} house effects", HouseEffect.entries.any { type in EffectFit.house(it) })
        }
    }

    @Test
    fun singleStripsGetMovementAndPixelStringsGetTwinkles() {
        assertTrue(LightType.SINGLE_STRIP in EffectFit.wled("Meteor"))
        assertTrue(LightType.SINGLE_STRIP !in EffectFit.wled("Twinklefox"))
        assertTrue(LightType.PIXEL_STRING in EffectFit.wled("twinklefox"))
        assertTrue(LightType.SPOT_FLOOD in EffectFit.wled("Breathe"))
        assertTrue(LightType.SPOT_FLOOD !in EffectFit.wled("Meteor"))
        assertEquals(setOf(LightType.MATRIX), EffectFit.show(SeqEffect.TEXT))
        // A controller's light profile maps to its kind.
        assertEquals(LightType.SINGLE_STRIP, EffectFit.of(LightProfile.STRIP))
        assertEquals(LightType.PIXEL_STRING, EffectFit.of(LightProfile.BULLETS))
    }
}
