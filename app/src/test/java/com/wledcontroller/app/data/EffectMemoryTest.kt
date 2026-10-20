package com.wledcontroller.app.data

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectMemoryTest {
    @Test
    fun anEffectComesBackAsItWasSet() {
        val saved = mapOf("sx" to 40, "ix" to 200, "c1" to 7, "c2" to 128, "c3" to 16, "o1" to 1, "o2" to 0, "o3" to 0)
        val seg = EffectMemory.patch(9, saved, defaultSpeed = 90)["seg"]!!.jsonObject
        assertEquals(9, seg["fx"]!!.jsonPrimitive.int)
        // Its own speed, not the light type's.
        assertEquals(40, seg["sx"]!!.jsonPrimitive.int)
        assertEquals(200, seg["ix"]!!.jsonPrimitive.int)
        assertEquals(7, seg["c1"]!!.jsonPrimitive.int)
        assertTrue(seg["o1"]!!.jsonPrimitive.boolean)
        assertFalse(seg["o2"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun aNewEffectGetsOnlyTheLightTypesSpeed() {
        val seg = EffectMemory.patch(3, null, defaultSpeed = 90)["seg"]!!.jsonObject
        assertEquals(90, seg["sx"]!!.jsonPrimitive.int)
        assertNull(seg["ix"])
        assertNull(EffectMemory.patch(3, null)["seg"]!!.jsonObject["sx"])
        assertEquals("dev#3", EffectMemory.key("dev", 3))
    }
}
