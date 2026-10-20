package com.wledcontroller.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WledParseTest {
    @Test
    fun readsWhatThePhoneAndControllersSend() {
        val devices = WledParse.devices("""[{"id":"a","name":"Porch","host":"192.168.1.20"},{"id":"b","name":"Tree","host":"192.168.1.21","extra":1}]""")
        assertEquals(listOf("Porch", "Tree"), devices.map { it.name })
        assertTrue(WledParse.devices("not json").isEmpty())

        val st = WledParse.state("""{"state":{"on":true,"bri":128,"mainseg":1,"seg":[{"id":0,"fx":3,"pal":1},{"id":1,"fx":9,"pal":6}]},"info":{"leds":{"count":1000}}}""")
        assertEquals(LightState(on = true, bri = 128, fx = 9, pal = 6, ledCount = 1000), st)

        assertEquals(listOf("Solid", "Blink"), WledParse.names("""["Solid","Blink"]"""))

        val presets = WledParse.presets("""{"0":{},"3":{"n":"Christmas"},"1":{"n":"","playlist":{"ps":[3]}},"x":{}}""")
        assertEquals(listOf(Triple(1, "Preset 1", true), Triple(3, "Christmas", false)), presets)
    }
}
