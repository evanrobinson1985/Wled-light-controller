package com.wledcontroller.app.data

import com.wledcontroller.app.house.XlightsModels
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmxTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun colourBecomesChannelValues() {
        val dimRgb = DmxFixture(name = "Par", outputId = "o", channels = DmxFixture.PRESETS[2].second) // dimmer, R, G, B
        // Half-bright orange: the dimmer carries the brightness, the colour goes out full.
        assertArrayEquals(bytes(128, 255, 127, 63), dimRgb.values(0x804020))
        val rgbw = DmxFixture(name = "Wash", outputId = "o", channels = DmxFixture.PRESETS[1].second)
        assertArrayEquals(bytes(255, 200, 100, 100), rgbw.values(0xFFC864))
        val shutter = DmxFixture(name = "S", outputId = "o", channels = DmxFixture.PRESETS[3].second) // R, G, B, dimmer, shutter
        assertEquals(255, shutter.values(0).last().toInt() and 0xFF)
    }

    @Test
    fun movingHeadsAimWithSixteenBitPanAndTilt() {
        val head = DmxFixture(name = "Head", outputId = "o", channels = DmxFixture.PRESETS[6].second)
        assertTrue(head.movingHead)
        val v = head.values(0xFFFFFF, pan = 0.5f, tilt = 1f)
        assertEquals(0x7F, v[0].toInt() and 0xFF); assertEquals(0xFF, v[1].toInt() and 0xFF)  // pan 32767
        assertEquals(0xFF, v[2].toInt() and 0xFF); assertEquals(0xFF, v[3].toInt() and 0xFF)  // tilt 65535
        assertEquals(255, v[5].toInt() and 0xFF)                                                // dimmer
    }

    @Test
    fun fixturesSharingAUniverseKeepEachOthersValues() {
        val out = PixelOutput(id = "o1", name = "Node", host = "10.0.0.9", protocol = OutputProtocol.ARTNET)
        val a = DmxFixture(id = "a", name = "A", outputId = "o1", universe = 2, address = 1, channels = DmxFixture.PRESETS[0].second)
        val b = DmxFixture(id = "b", name = "B", outputId = "o1", universe = 2, address = 10, channels = DmxFixture.PRESETS[0].second)
        DmxRegistry.update(listOf(a, b), listOf(out))
        DmxRegistry.stage("a", DmxRegistry.frame(0xFF0000, 0.5f, 0.5f))
        val u = DmxRegistry.stage("b", DmxRegistry.frame(0x0000FF, 0.5f, 0.5f))!!
        assertEquals("10.0.0.9", u.host)
        assertEquals(OutputProtocol.ARTNET, u.protocol)
        assertEquals(2, u.number)
        assertEquals(255, u.data[0].toInt() and 0xFF)    // A red still set
        assertEquals(255, u.data[11].toInt() and 0xFF)   // B blue at address 10 + 2
    }

    @Test
    fun xlightsFixtureModelsImport() {
        // Straight from the xLights DMX fixture library.
        val flood = """<?xml version="1.0" encoding="UTF-8"?>
<dmxmodel name="CL-48 LED-Cluster" parm1="6" parm2="1" parm3="1" DisplayAs="DMX" StringType="Single Color White" NodeNames="Red,Green,Blue,Auto Program,Strobe,Dimmer" DmxPanChannel="0" DmxTiltChannel="0" DmxShutterChannel="0" DmxRedChannel="1" DmxGreenChannel="2" DmxBlueChannel="3" DmxWhiteChannel="0" DmxStyle="Flood Light" DmxShutterOpen="1" />"""
        assertTrue(XlightsModels.isDmx(flood))
        val f = XlightsModels.parseDmxFixture(flood.byteInputStream())
        assertEquals(listOf(DmxRole.RED, DmxRole.GREEN, DmxRole.BLUE, DmxRole.NONE, DmxRole.NONE, DmxRole.DIMMER), f.channels.map { it.role })
        assertEquals("Flood Light", f.type)

        val wash = """<dmxmodel name="A. Leda Wash K5" parm1="19" DisplayAs="DMX" NodeNames="Red,Red Fine,Green,Green Fine,Blue,Blue Fine,White,White Fine,CTO,Macro,Strobe,Dimmer,Dimmer Fine,Pan,Pan Fine,Tilt,Tilt Fine,Function,Reset" DmxPanChannel="14" DmxTiltChannel="16" DmxShutterChannel="0" DmxRedChannel="1" DmxGreenChannel="3" DmxBlueChannel="5" DmxWhiteChannel="7" DmxStyle="Moving Head 3D" DmxShutterOpen="1" />"""
        val w = XlightsModels.parseDmxFixture(wash.byteInputStream())
        assertEquals(19, w.channels.size)
        assertTrue(w.movingHead)
        assertEquals(DmxRole.PAN, w.channels[13].role)
        assertEquals(DmxRole.PAN_FINE, w.channels[14].role)
        assertEquals(DmxRole.TILT, w.channels[15].role)
        assertEquals(DmxRole.TILT_FINE, w.channels[16].role)
        assertEquals(DmxRole.DIMMER, w.channels[11].role)
        assertEquals(DmxRole.NONE, w.channels[12].role)   // dimmer fine stays 0
        assertNotNull(w.channels.firstOrNull { it.role == DmxRole.WHITE })
    }
}
