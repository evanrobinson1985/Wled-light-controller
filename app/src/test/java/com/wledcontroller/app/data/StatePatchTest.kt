package com.wledcontroller.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatePatchTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    private val state = obj(
        """{"on":true,"bri":128,"mainseg":0,"seg":[
            {"id":0,"start":0,"stop":50,"sel":true,"fx":0,"col":[[255,0,0],[0,0,255],[0,0,0]]},
            {"id":1,"start":50,"stop":100,"sel":false,"fx":0,"col":[[0,255,0],[0,0,0],[0,0,0]]}]}"""
    )

    private fun seg(s: JsonObject, i: Int) = s["seg"]!!.jsonArray[i].jsonObject

    @Test
    fun segObjectAppliesToSelectedSegmentsOnly() {
        val out = StatePatch.apply(state, obj("""{"seg":{"fx":9}}"""))
        assertEquals(9, seg(out, 0).int("fx"))
        assertEquals(0, seg(out, 1).int("fx"))
    }

    @Test
    fun emptyColourSlotKeepsExistingColour() {
        val out = StatePatch.apply(state, obj("""{"seg":{"col":[[],[255,255,0]]}}"""))
        val col = seg(out, 0)["col"]!!.jsonArray
        assertEquals(Json.parseToJsonElement("[255,0,0]"), col[0])
        assertEquals(Json.parseToJsonElement("[255,255,0]"), col[1])
    }

    @Test
    fun segArrayWithStopZeroDeletesSegment() {
        val out = StatePatch.apply(state, obj("""{"seg":[{"id":1,"stop":0}]}"""))
        val segs = out["seg"] as JsonArray
        assertEquals(1, segs.size)
        assertEquals(0, segs[0].jsonObject.int("id"))
    }

    @Test
    fun commandsAreNotAppliedLocally() {
        val out = StatePatch.apply(state, obj("""{"ps":3,"bri":200}"""))
        assertFalse(out.containsKey("ps"))
        assertEquals(200, out.int("bri"))
    }

    @Test
    fun mergePatchesMergesColourSlots() {
        val merged = StatePatch.mergePatches(obj("""{"col":[[1,2,3]]}"""), obj("""{"col":[[],[4,5,6]]}"""))
        assertEquals(Json.parseToJsonElement("[[1,2,3],[4,5,6]]"), merged["col"])
    }

    @Test
    fun canMergeRules() {
        assertTrue(StatePatch.canMerge(obj("""{"bri":1}"""), obj("""{"bri":2}""")))
        assertFalse(StatePatch.canMerge(obj("""{"bri":1}"""), obj("""{"ps":2}""")))
        assertFalse(StatePatch.canMerge(obj("""{"seg":{"id":0}}"""), obj("""{"seg":{"id":1}}""")))
        assertFalse(StatePatch.canMerge(obj("""{"seg":{"id":0}}"""), obj("""{"seg":[{"id":0}]}""")))
    }
}
