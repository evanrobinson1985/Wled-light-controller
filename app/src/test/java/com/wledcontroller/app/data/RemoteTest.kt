package com.wledcontroller.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTest {

    private val xml = """<?xml version="1.0" ?><vs><ac>128</ac><cl>255</cl><cl>160</cl><cl>0</cl><cs>0</cs><cs>0</cs><cs>255</cs><ns>0</ns><nr>1</nr><nl>0</nl><nf>1</nf><nd>60</nd><nt>0</nt><fx>9</fx><sx>200</sx><ix>90</ix><fp>6</fp><wv>-1</wv><ws>0</ws><ps>3</ps><cy>0</cy><ds>Roofline</ds><ss>0</ss></vs>"""

    @Test
    fun mqttStateBecomesAPatch() {
        val p = WledXml.statePatch(xml)!!
        assertTrue(p["on"]!!.jsonPrimitive.boolean)
        assertEquals(128, p["bri"]!!.jsonPrimitive.int)
        val seg = p["seg"]!!.jsonObject
        assertEquals(listOf(255, 160, 0), seg["col"]!!.jsonArray[0].jsonArray.map { it.jsonPrimitive.int })
        assertEquals(listOf(0, 0, 255), seg["col"]!!.jsonArray[1].jsonArray.map { it.jsonPrimitive.int })
        assertEquals(9, seg["fx"]!!.jsonPrimitive.int)
        assertEquals(200, seg["sx"]!!.jsonPrimitive.int)
        assertEquals(90, seg["ix"]!!.jsonPrimitive.int)
        assertEquals(6, seg["pal"]!!.jsonPrimitive.int)
        assertEquals(3, WledXml.preset(xml))
        assertNull(WledXml.statePatch("<html>nope</html>"))
    }

    @Test
    fun offAndWhiteChannel() {
        val p = WledXml.statePatch(xml.replace("<ac>128</ac>", "<ac>0</ac>").replace("<wv>-1</wv>", "<wv>40</wv>"))!!
        assertFalse(p["on"]!!.jsonPrimitive.boolean)
        assertNull(p["bri"])
        assertEquals(4, (p["seg"]!!.jsonObject["col"] as JsonArray)[0].jsonArray.size)
        // Applied over a known state, the patch lands on the selected segment.
        val state = JsonObject(mapOf("mainseg" to kotlinx.serialization.json.JsonPrimitive(0), "seg" to kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.buildJsonObject { put("id", kotlinx.serialization.json.JsonPrimitive(0)); put("sel", kotlinx.serialization.json.JsonPrimitive(true)); put("fx", kotlinx.serialization.json.JsonPrimitive(0)) })
        }))
        val merged = StatePatch.apply(state, p)
        assertEquals(9, merged["seg"]!!.jsonArray[0].jsonObject["fx"]!!.jsonPrimitive.int)
    }

    @Test
    fun controllersGetTheirOwnTopicAndThePlainPort() {
        val s = MqttSettings(enabled = true, host = "broker.example.com", port = 8883, tls = true, wledPort = 1883, user = "me", password = "pw", prefix = "wled-abc")
        val topic = RemoteSetup.topicFor(s, SavedDevice("1234abcd", "Front Roof!", "192.168.1.20"))
        assertEquals("wled-abc/front-roof-1234", topic)
        val mqtt = RemoteSetup.wledConfig(s, topic)["if"]!!.jsonObject["mqtt"]!!.jsonObject
        assertTrue(mqtt["en"]!!.jsonPrimitive.boolean)
        assertEquals("broker.example.com", mqtt["broker"]!!.jsonPrimitive.content)
        assertEquals(1883, mqtt["port"]!!.jsonPrimitive.int)
        assertEquals("pw", mqtt["psk"]!!.jsonPrimitive.content)
        assertEquals(topic, mqtt["topics"]!!.jsonObject["device"]!!.jsonPrimitive.content)
        assertEquals("wled-abc/all", mqtt["topics"]!!.jsonObject["group"]!!.jsonPrimitive.content)
    }
}
