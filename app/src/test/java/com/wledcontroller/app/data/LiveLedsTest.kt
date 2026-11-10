package com.wledcontroller.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveLedsTest {
    @Test fun readsTheSocketStream() {
        // "L", version 1, then red, green, blue for three LEDs.
        val strip = byteArrayOf('L'.code.toByte(), 1, 0xFF.toByte(), 0, 0, 0, 0xFF.toByte(), 0, 0x12, 0x34, 0x56)
        assertArrayEquals(intArrayOf(0xFF0000, 0x00FF00, 0x123456), LiveLeds.fromBinary(strip)!!.colors)
        // A matrix: version 2 with its width and height first.
        val matrix = byteArrayOf('L'.code.toByte(), 2, 2, 1, 1, 2, 3, 4, 5, 6)
        assertArrayEquals(intArrayOf(0x010203, 0x040506), LiveLeds.fromBinary(matrix)!!.colors)
        assertNull(LiveLeds.fromBinary(byteArrayOf(1, 2, 3)))
    }

    @Test fun readsTheHttpAnswer() {
        val e = Json.parseToJsonElement("""{"leds":["FF8800","00FF00"],"n":1}""")
        assertArrayEquals(intArrayOf(0xFF8800, 0x00FF00), LiveLeds.fromJson(e)!!.colors)
    }

    @Test fun spreadsASampleOverTheWholeStrip() {
        // A long strip is sent as every other LED: each shown LED takes the nearest sample.
        val live = LiveLeds(intArrayOf(1, 2, 3))
        assertArrayEquals(intArrayOf(1, 1, 2, 2, 3, 3), live.spread(6))
        assertEquals(3, live.spread(3).size)
    }
}
