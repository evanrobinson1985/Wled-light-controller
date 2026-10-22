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

    @Test
    fun readsScenesGroupsAndWholeHouseEffects() {
        assertEquals(listOf(WatchScene("s1", "Movie night")), WledParse.list<WatchScene>("""[{"id":"s1","name":"Movie night"}]"""))
        assertEquals(listOf("a", "b"), WledParse.list<WatchGroup>("""[{"id":"g","name":"Yard","devices":["a","b"]}]""").single().devices)
        val fx = WledParse.list<HouseFx>("""[{"name":"WAVE","label":"Colour wave"},{"name":"WITCHES","label":"Flying witches","holiday":"Halloween","noun":"witch","plural":"witches"}]""")
        assertEquals(null, fx[0].holiday)
        assertEquals("witches", fx[1].plural)
        val st = WledParse.houseStatus("""{"running":true,"effect":"GHOSTS","speed":0.5,"size":0.3,"count":4}""")
        assertEquals(HouseStatus(true, "GHOSTS", 0.5f, 0.3f, 4), st)
        val p = WledParse.effectPatch(9, mapOf("sx" to 40, "o1" to 1))
        assertEquals("""{"on":true,"seg":{"fx":9,"sx":40,"o1":true}}""", p.toString())
    }

    @Test
    fun readsTheShowStatus() {
        val s = WledParse.showStatus("""{"running":true,"mode":"playlist","now":"Carol","playlist":"Christmas","upNext":["Jingle","Silent"],"playlists":[{"id":"p1","name":"Christmas"}],"extra":1}""")
        assertEquals("Carol", s.now)
        assertEquals(listOf("Jingle", "Silent"), s.upNext)
        assertEquals(ShowPlaylist("p1", "Christmas"), s.playlists.single())
    }
}
