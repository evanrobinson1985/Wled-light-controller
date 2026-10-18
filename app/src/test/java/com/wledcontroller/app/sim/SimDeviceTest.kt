package com.wledcontroller.app.sim

import com.wledcontroller.app.data.WledApi
import com.wledcontroller.app.data.WledInfo
import com.wledcontroller.app.data.WledState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimDeviceTest {

    private val hosts = ArrayList<String>()

    @After
    fun cleanUp() = hosts.forEach { SimHub.remove(it) }

    private fun device(host: String, leds: Int = 60, name: String = "Test"): SimDevice {
        hosts += host
        return SimHub.add(SimDeviceData(
            host = host, name = name, seed = 1, createdAt = 0,
            state = SimDevice.defaultState(leds).toString(),
            cfg = SimDevice.defaultCfg(name, leds).toString(),
            presets = """{"0":{}}""",
        ))
    }

    private fun get(d: SimDevice, path: String): JsonObject = Json.parseToJsonElement(d.handle("GET", path, null).body).jsonObject
    private fun post(d: SimDevice, body: String) = d.handle("POST", "/json/state", body)
    private fun state(d: SimDevice) = WledState.parse(get(d, "/json/state"))

    @Test
    fun answersLikeWled() {
        val d = device("127.77.0.201", leds = 150, name = "Roof")
        val info = WledInfo.parse(get(d, "/json/info"))
        assertEquals(150, info.ledCount)
        assertEquals("Roof", info.name)
        val all = get(d, "/json")
        assertTrue(all.containsKey("effects") && all.containsKey("palettes") && all.containsKey("state"))
        assertEquals(1, state(d).segments.size)
        assertEquals(404, d.handle("GET", "/nothing", null).code)
    }

    @Test
    fun stateChangesAndFrames() {
        val d = device("127.77.0.202")
        post(d, """{"on":true,"bri":255,"transition":0,"seg":{"fx":0,"col":[[255,0,0]]}}""")
        assertEquals(0, state(d).segments[0].fx)
        val f = d.frame(System.currentTimeMillis())
        assertEquals(60, f.size)
        assertEquals(0xFF0000, f[0])
        post(d, """{"on":"t"}""")
        assertFalse(state(d).on)
        assertEquals(0, d.frame(System.currentTimeMillis() + 50)[0])
    }

    @Test
    fun presetsSaveAndLoad() {
        val d = device("127.77.0.203")
        post(d, """{"transition":0,"seg":{"fx":9,"sx":200}}""")
        post(d, """{"psave":3,"n":"Party","ib":true,"sb":true}""")
        post(d, """{"seg":{"fx":0}}""")
        assertTrue(get(d, "/presets.json").containsKey("3"))
        post(d, """{"ps":3}""")
        val s = state(d)
        assertEquals(9, s.segments[0].fx)
        assertEquals(3, s.ps)
        post(d, """{"pdel":3}""")
        assertFalse(get(d, "/presets.json").containsKey("3"))
    }

    @Test
    fun segmentsAndConfig() {
        val d = device("127.77.0.204")
        post(d, """{"seg":[{"id":1,"start":30,"stop":60}]}""")
        assertEquals(2, state(d).segments.size)
        // Growing the strip keeps segment 1 where it is; shrinking clips it.
        d.handle("POST", "/json/cfg", """{"hw":{"led":{"ins":[{"start":0,"len":40,"type":22}]}}}""")
        assertEquals(40, WledInfo.parse(get(d, "/json/info")).ledCount)
        assertEquals(40, state(d).segments.maxOf { it.stop })
    }

    @Test
    fun powerLimiterCapsCurrent() {
        val d = device("127.77.0.205", leds = 100)
        post(d, """{"bri":255,"transition":0,"seg":{"fx":0,"col":[[255,255,255]]}}""")
        d.frame(System.currentTimeMillis())
        assertTrue(d.powerMa > 5000)
        d.handle("POST", "/json/cfg", """{"hw":{"led":{"maxpwr":1000}}}""")
        val f = d.frame(System.currentTimeMillis() + 20)
        assertTrue(d.powerMa <= 1000)
        assertTrue((f[0] and 0xFF) < 255)
    }

    @Test
    fun realtimeAndNetworkOutputs() {
        val master = device("127.77.0.206", leds = 10, name = "Master")
        val remote = device("127.77.0.207", leds = 5, name = "Remote")
        // DDP from the phone overrides effects.
        assertTrue(SimHub.deliverDdp("127.77.0.206", ByteArray(30) { if (it % 3 == 2) -1 else 0 }))
        assertEquals(0x0000FF, master.frame(System.currentTimeMillis())[3])
        assertTrue(WledInfo.parse(get(master, "/json/info")).live)

        // A network output on the master drives the remote controller.
        val other = device("127.77.0.208", leds = 10)
        other.handle("POST", "/json/cfg", """{"hw":{"led":{"ins":[
            {"start":0,"len":10,"type":22},{"start":10,"len":5,"type":80,"pin":[127,77,0,207]}]}}}""")
        post(other, """{"bri":255,"transition":0,"seg":{"fx":0,"col":[[0,255,0]]}}""")
        assertEquals(15, other.frame(System.currentTimeMillis()).size)
        val remoteFrame = remote.frame(System.currentTimeMillis() + 15)
        assertEquals(0x00FF00, remoteFrame[0])
        assertEquals(10, other.localLeds)
    }

    @Test
    fun appTalksToSimulatorThroughHttpClient() = runBlocking {
        device("127.77.0.209", leds = 33, name = "Via HTTP")
        val api = WledApi(OkHttpClient.Builder().addInterceptor(SimHub.interceptor).build())
        val info = api.probe("127.77.0.209")
        assertNotNull(info)
        assertEquals(33, info!!.ledCount)
        api.postState("127.77.0.209", Json.parseToJsonElement("""{"bri":12}""").jsonObject)
        assertEquals(12, WledState.parse(api.get("127.77.0.209", "/json/state").jsonObject).bri)
        assertTrue(api.palettePreviews("127.77.0.209").size > 50)
        assertEquals(SimCatalog.effects.size, api.effectData("127.77.0.209").size)
    }

    @Test
    fun everyEffectAndPaletteRenders() {
        for (fx in SimCatalog.effects.indices) for (len in listOf(1, 2, 7, 150)) for (pal in listOf(0, 1, 4, 5, 9, 40)) {
            val p = SegParams(len, fx, sx = (fx * 37) % 256, ix = (fx * 91) % 256, c1 = 128, c2 = 128, c3 = 16,
                colors = intArrayOf(0xFF0000, 0x00FF00, 0x0000FF), pal = pal, seed = fx)
            val out = IntArray(len)
            for (t in listOf(0L, 17L, 1234L, 98_765L, 3_600_000L)) {
                SimEffects.render(p, t, out)
                out.forEach { c -> assertTrue("fx ${SimCatalog.effects[fx]} gave $c", c in 0..0xFFFFFF) }
            }
        }
    }
}
