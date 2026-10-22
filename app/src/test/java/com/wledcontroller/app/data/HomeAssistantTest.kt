package com.wledcontroller.app.data

import com.wledcontroller.app.show.RunnerState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantTest {
    private val p = "wled-abc"

    @Test
    fun discoveryOffersTheShowEntities() {
        val msgs = HomeAssistant.discovery(p, listOf("Christmas", "Halloween")).toMap()
        assertEquals(5, msgs.size)
        val select = Json.parseToJsonElement(msgs.getValue("homeassistant/select/wled-abc_app/mode/config")) as JsonObject
        assertEquals(listOf("Off", "Schedule", "Jukebox", "Play: Christmas", "Play: Halloween"), select["options"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("wled-abc/app/set/mode", select["command_topic"]!!.jsonPrimitive.content)
        val switch = Json.parseToJsonElement(msgs.getValue("homeassistant/switch/wled-abc_app/power/config")) as JsonObject
        assertEquals("wled-abc/app/status", switch["availability_topic"]!!.jsonPrimitive.content)
        assertTrue(msgs.keys.any { it.startsWith("homeassistant/button/") && it.contains("/skip/") })
    }

    @Test
    fun commandsBecomeShowActions() {
        assertEquals("play" to null, HomeAssistant.command(p, "wled-abc/app/set/power", "ON"))
        assertEquals("stop" to null, HomeAssistant.command(p, "wled-abc/app/set/power", "OFF"))
        assertEquals("skip" to null, HomeAssistant.command(p, "wled-abc/app/set/skip", "PRESS"))
        assertEquals("play" to "Halloween", HomeAssistant.command(p, "wled-abc/app/set/mode", "Play: Halloween"))
        assertEquals("jukebox" to null, HomeAssistant.command(p, "wled-abc/app/set/mode", "Jukebox"))
        assertNull(HomeAssistant.command(p, "someone-else/app/set/power", "ON"))
        assertNull(HomeAssistant.command(p, "wled-abc/app/set/mode", "Nonsense"))
    }

    @Test
    fun stateMatchesWhatsPlaying() {
        val playing = RunnerState(running = true, mode = "playlist", nowPlaying = "Carol of the Bells", playlistName = "Christmas")
        val s = Json.parseToJsonElement(HomeAssistant.stateJson(playing)) as JsonObject
        assertEquals("ON", s["power"]!!.jsonPrimitive.content)
        assertEquals("Play: Christmas", s["mode"]!!.jsonPrimitive.content)
        assertEquals("Carol of the Bells", s["now"]!!.jsonPrimitive.content)
        assertEquals("Off", HomeAssistant.modeOf(RunnerState()))
        assertEquals("Schedule", HomeAssistant.modeOf(RunnerState(running = true, mode = "schedule")))
    }
}
