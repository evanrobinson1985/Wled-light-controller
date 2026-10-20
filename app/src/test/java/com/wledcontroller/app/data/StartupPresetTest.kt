package com.wledcontroller.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupPresetTest {
    private fun patch(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun whatEachCommandMeansAtStartUp() {
        assertEquals(StartupPreset.UsePreset(3), StartupPreset.actionFor(patch("""{"ps":3}""")))
        assertEquals(StartupPreset.SaveCurrent, StartupPreset.actionFor(patch("""{"on":true,"seg":{"fx":9}}""")))
        assertEquals(StartupPreset.SaveCurrent, StartupPreset.actionFor(patch("""{"seg":[{"id":1,"col":[[255,0,0]]}]}""")))
        // Power, brightness, picking a segment and saving presets don't change what it starts with.
        assertNull(StartupPreset.actionFor(patch("""{"on":false}""")))
        assertNull(StartupPreset.actionFor(patch("""{"bri":40,"on":true}""")))
        assertNull(StartupPreset.actionFor(patch("""{"seg":[{"id":0,"sel":true},{"id":1,"sel":false}]}""")))
        assertNull(StartupPreset.actionFor(patch("""{"psave":250,"n":"x","ib":true}""")))
        assertNull(StartupPreset.actionFor(JsonObject(emptyMap())))
    }

    @Test
    fun startupSlotLeavesTheUsersPresetsAlone() {
        assertEquals(250, StartupPreset.slot(null))
        assertEquals(250, StartupPreset.slot(listOf(Preset(250, StartupPreset.NAME, false, null))))
        assertEquals(249, StartupPreset.slot(listOf(Preset(250, "My favourite", false, null))))
    }
}
