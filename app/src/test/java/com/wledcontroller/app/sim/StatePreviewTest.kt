package com.wledcontroller.app.sim

import com.wledcontroller.app.data.WledState
import com.wledcontroller.app.ui.components.stripLayout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatePreviewTest {
    private fun state(s: String) = WledState.parse(Json.parseToJsonElement(s).jsonObject)

    @Test
    fun showsTheChosenLookWithoutTheController() {
        // Two segments: red Solid on the first half, blue Solid (number 7 on this controller) on the rest.
        val st = state("""{"on":true,"bri":255,"seg":[
            {"id":0,"start":0,"stop":5,"on":true,"fx":0,"col":[[255,0,0]]},
            {"id":1,"start":5,"stop":10,"on":true,"fx":7,"col":[[0,0,255]]}]}""")
        val names = listOf("Blink", "x", "x", "x", "x", "x", "x", "Solid")
        val leds = StatePreview.render(st, names, emptyList(), 10, 0L)
        // Effect 0 here is "Blink" (matched by name, not number), effect 7 is "Solid".
        assertEquals(0x0000FF, leds[7])
        assertEquals(10, leds.size)

        val solid = StatePreview.render(state("""{"on":true,"bri":255,"seg":[{"id":0,"start":0,"stop":4,"fx":0,"col":[[0,255,0]]}]}"""), listOf("Solid"), emptyList(), 4, 0L)
        assertTrue(solid.all { it == 0x00FF00 })
    }

    @Test
    fun offAndDimmedLights() {
        val off = StatePreview.render(state("""{"on":false,"bri":255,"seg":[{"id":0,"start":0,"stop":3,"fx":0,"col":[[255,255,255]]}]}"""), listOf("Solid"), emptyList(), 3, 0L)
        assertTrue(off.all { it == 0 })
        val dim = StatePreview.render(state("""{"on":true,"bri":128,"seg":[{"id":0,"start":0,"stop":3,"fx":0,"col":[[255,255,255]]}]}"""), listOf("Solid"), emptyList(), 3, 0L)
        val r = (dim[0] shr 16) and 0xFF
        assertTrue("half brightness, got $r", r in 120..136)
    }

    @Test
    fun longStripsWrapButEveryLedStaysVisible() {
        val (rows, perRow, pitch) = stripLayout(1000, 392f)
        assertTrue(rows * perRow >= 1000)
        assertTrue(pitch >= 3.5f)
        assertEquals(1, stripLayout(30, 392f).first)
        assertEquals(12f, stripLayout(30, 392f).third, 0.01f)
    }
}
