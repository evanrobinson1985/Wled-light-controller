package com.wledcontroller.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelPacketsTest {

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    @Test
    fun e131PacketLayout() {
        val data = ByteArray(6) { (it + 1).toByte() }
        val p = PixelPackets.e131(universe = 7, sequence = 3, data = data)
        assertEquals(126 + 6, p.size)
        assertEquals(0x0010, u16(p, 0))
        assertEquals("ASC-E1.17", String(p, 4, 9))
        assertEquals(0x7000 or (p.size - 16), u16(p, 16))   // root length counts from byte 16
        assertEquals(0x7000 or (p.size - 38), u16(p, 38))   // framing layer
        assertEquals(3, p[111].toInt())                       // sequence
        assertEquals(7, u16(p, 113))                          // universe
        assertEquals(0x7000 or (p.size - 115), u16(p, 115))  // DMP layer
        assertEquals(7, u16(p, 123))                          // property count = 1 + channels
        assertEquals(0, p[125].toInt())                       // start code
        assertArrayEquals(data, p.copyOfRange(126, p.size))
    }

    @Test
    fun artnetPacketLayout() {
        val p = PixelPackets.artnet(universe = 0x0102, sequence = 9, data = ByteArray(5) { 1 })
        assertEquals("Art-Net", String(p, 0, 7))
        assertEquals(0, p[7].toInt())
        assertEquals(0x00, p[8].toInt()); assertEquals(0x50, p[9].toInt())
        assertEquals(14, p[11].toInt())
        assertEquals(9, p[12].toInt())
        assertEquals(0x02, p[14].toInt()); assertEquals(0x01, p[15].toInt())
        assertEquals(6, u16(p, 16))                          // padded to an even length
        assertEquals(18 + 6, p.size)
        assertEquals(0, p[23].toInt())
    }

    @Test
    fun pixelsSplitAcrossUniverses() {
        val parts = PixelPackets.split(ByteArray(1200), startUniverse = 5, perUniverse = 510)
        assertEquals(listOf(5 to 510, 6 to 510, 7 to 180), parts.map { it.first to it.second.size })
        val o = PixelOutput(name = "Mega tree", host = "10.0.0.20", ledCount = 400, startUniverse = 5)
        assertEquals(3, o.universes)
        assertEquals("e131://10.0.0.20?u=5&c=510", o.address)
    }
}
