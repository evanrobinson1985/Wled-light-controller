package com.wledcontroller.app.data

import com.wledcontroller.app.ui.components.stripLayout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveViewTest {
    @Test
    fun readsWledsLiveFrames() {
        val oneD = byteArrayOf('L'.code.toByte(), 1, 255.toByte(), 0, 0, 0, 128.toByte(), 255.toByte())
        assertArrayEquals(intArrayOf(0xFF0000, 0x0080FF), LiveView.parseBinary(oneD))
        // 2D frames carry width and height before the colours.
        val twoD = byteArrayOf('L'.code.toByte(), 2, 2, 1, 1, 2, 3, 4, 5, 6)
        assertArrayEquals(intArrayOf(0x010203, 0x040506), LiveView.parseBinary(twoD))
        assertNull(LiveView.parseBinary("{".toByteArray()))

        val json = Json.parseToJsonElement("""{"leds":["FF0000","00FF00","10000000"],"n":4}""").jsonObject
        assertArrayEquals(intArrayOf(0xFF0000, 0x00FF00, 0x101010), LiveView.parseJson(json))
    }

    @Test
    fun samplesSpreadOverEveryLed() {
        // 250 samples of a 1000-LED strip: each sample covers four LEDs.
        val samples = IntArray(250) { it }
        val leds = LiveView.expand(samples, 1000)
        assertEquals(1000, leds.size)
        assertEquals(0, leds[3]); assertEquals(1, leds[4]); assertEquals(249, leds[999])
    }

    @Test
    fun longStripsWrapButEveryLedStaysVisible() {
        val (rows, perRow, pitch) = stripLayout(1000, 392f)
        assertTrue(rows * perRow >= 1000)
        assertTrue(pitch >= 3.5f)
        assertEquals(1, stripLayout(30, 392f).first)
        assertEquals(12f, stripLayout(30, 392f).third, 0.01f)  // short strips: comfortable dots in one row
    }
}
