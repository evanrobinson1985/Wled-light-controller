package com.wledcontroller.app.house

import android.content.Context
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.data.WledApi
import com.wledcontroller.app.data.arr
import com.wledcontroller.app.data.int
import com.wledcontroller.app.data.obj
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Another controller driven by the master over the network (a WLED "virtual" DDP output). */
data class RemoteBus(val device: SavedDevice, val ip: List<Int>, val start: Int, val len: Int)

data class StandalonePlan(
    val master: SavedDevice,
    val masterLeds: Int,
    val remotes: List<RemoteBus>,
    val width: Int,
    val height: Int,
    val map: IntArray,
    val mappedLeds: Int,
    val droppedLeds: Int,
    val warnings: List<String>,
) {
    val totalLeds get() = masterLeds + remotes.sumOf { it.len }
}

/**
 * Makes one ESP32 controller run whole-house effects on its own: the other controllers become
 * network outputs of it, and a 2D ledmap.json built from the photo map places every LED.
 * The master's previous LED config (and ledmap) is backed up in the app for [undo].
 */
class StandaloneSetup(private val context: Context, private val api: WledApi, private val store: AppStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private fun backupFile(deviceId: String) = File(context.filesDir, "standalone_backup_${deviceId.filter { it.isLetterOrDigit() }}.json")

    fun hasBackup(deviceId: String) = backupFile(deviceId).exists()

    private fun isNetworkBus(o: JsonObject) = o.int("type") in 80..95

    suspend fun plan(masterId: String): StandalonePlan {
        val devices = store.devices.first()
        val master = devices.first { it.id == masterId }
        val map = store.houseMap.first()
        val leds = map.placedLeds().filter { l -> devices.any { it.id == l.deviceId } }
        require(leds.isNotEmpty()) { "Trace some lights on the house map first." }

        val warnings = mutableListOf<String>()
        val info = api.probe(master.host) ?: throw IllegalStateException("${master.name} isn't answering.")
        if (!info.arch.contains("esp32", ignoreCase = true)) {
            warnings += "${master.name} is an ${info.arch.uppercase()}. Network outputs need an ESP32 — pick an ESP32 controller as the master."
        }
        val ver = Regex("""(\d+)\.(\d+)""").find(info.version)?.destructured?.let { (a, b) -> a.toInt() * 100 + b.toInt() } ?: 0
        if (ver in 1 until 15) warnings += "${master.name} runs WLED ${info.version}. 2D maps across controllers need WLED 0.15 or newer."

        val cfg = api.config(master.host)
        val masterLeds = maxOf(
            cfg.obj("hw")?.obj("led")?.arr("ins")?.mapNotNull { it as? JsonObject }?.filterNot(::isNetworkBus)
                ?.maxOfOrNull { it.int("start") + it.int("len") } ?: 0,
            leds.filter { it.deviceId == masterId }.maxOfOrNull { it.index + 1 } ?: 0,
        )

        var next = masterLeds
        val remotes = leds.filter { it.deviceId != masterId }.groupBy { it.deviceId }.map { (id, l) ->
            val dev = devices.first { it.id == id }
            val probed = api.probe(dev.host)?.ledCount ?: 0
            val len = maxOf(probed, l.maxOf { it.index } + 1)
            RemoteBus(dev, ipOctets(dev.host), next, len).also { next += len }
        }

        fun masterIndex(l: PlacedLed) = if (l.deviceId == masterId) l.index else remotes.first { it.device.id == l.deviceId }.start + l.index

        // Fit a grid over the area the lights cover, fine enough that few LEDs share a cell.
        val xs = leds.map { it.x * map.aspect }; val ys = leds.map { it.y }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).coerceAtLeast(0.01f); val spanY = (maxY - minY).coerceAtLeast(0.01f)
        var w = 16; var h = 1; var cells = IntArray(0); var dropped = 0
        while (true) {
            h = ceil(w * spanY / spanX).toInt().coerceIn(1, 128)
            cells = IntArray(w * h) { -1 }
            dropped = 0
            var clashes = 0
            for (l in leds) {
                val cx = (((l.x * map.aspect) - minX) / spanX * (w - 1)).roundToInt()
                val cy = ((l.y - minY) / spanY * (h - 1)).roundToInt()
                val cell = nearestFree(cells, w, h, cx, cy)
                if (cell < 0) dropped++ else {
                    if (cell != cy * w + cx) clashes++
                    cells[cell] = masterIndex(l)
                }
            }
            if (clashes <= leds.size / 50 || w >= 128 || (w * 2) * h > 8192) break
            w = (w * 1.5f).toInt().coerceAtMost(128)
        }
        if (dropped > 0) warnings += "$dropped LEDs are packed too tightly to place and will stay dark. Spread the traced lines out a little."
        val unmapped = remotes.sumOf { it.len } + masterLeds - (leds.size - dropped)
        if (unmapped > 0) warnings += "$unmapped LEDs aren't on the map and will stay dark in whole-house mode."
        return StandalonePlan(master, masterLeds, remotes, w, h, cells, leds.size - dropped, dropped, warnings)
    }

    private fun nearestFree(cells: IntArray, w: Int, h: Int, cx: Int, cy: Int): Int {
        for (r in 0..2) for (dy in -r..r) for (dx in -r..r) {
            if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != r) continue
            val x = cx + dx; val y = cy + dy
            if (x in 0 until w && y in 0 until h && cells[y * w + x] < 0) return y * w + x
        }
        return -1
    }

    private suspend fun ipOctets(host: String): List<Int> = withContext(Dispatchers.IO) {
        val addr = InetAddress.getByName(WledApi.normalizeHost(host).substringBefore(':'))
        require(addr is Inet4Address) { "$host doesn't have an IPv4 address." }
        addr.address.map { it.toInt() and 0xFF }
    }

    suspend fun apply(plan: StandalonePlan) {
        val host = plan.master.host
        val led = api.config(host).obj("hw")?.obj("led") ?: throw IllegalStateException("Can't read LED config.")
        val backup = backupFile(plan.master.id)
        if (!backup.exists()) {
            val oldMap = runCatching { api.get(host, "/ledmap.json").toString() }.getOrNull()
            backup.writeText(buildJsonObject {
                put("led", led)
                if (oldMap != null) put("ledmap", oldMap)
            }.toString())
        }
        val physical = led.arr("ins")?.mapNotNull { it as? JsonObject }?.filterNot(::isNetworkBus) ?: emptyList()
        val virtual = plan.remotes.map { r ->
            buildJsonObject {
                put("start", r.start); put("len", r.len); put("type", 80)
                put("pin", JsonArray(r.ip.map { JsonPrimitive(it) }))
                put("order", 1); put("rev", false); put("skip", 0); put("ref", false); put("rgbwm", 0); put("freq", 0)
            }
        }
        api.postConfig(host, buildJsonObject {
            putJsonObject("hw") { putJsonObject("led") { put("ins", JsonArray(physical + virtual)) } }
        })
        val ledmap = buildJsonObject {
            put("n", "Whole house")
            put("width", plan.width)
            put("height", plan.height)
            put("map", JsonArray(plan.map.map { JsonPrimitive(it) }))
        }
        api.uploadFile(host, "/ledmap.json", ledmap.toString())
        reboot(host)
    }

    suspend fun undo(master: SavedDevice) {
        val backup = backupFile(master.id)
        val saved = json.parseToJsonElement(backup.readText()) as JsonObject
        val led = saved.obj("led") ?: throw IllegalStateException("Backup is damaged.")
        api.postConfig(master.host, buildJsonObject {
            putJsonObject("hw") { putJsonObject("led") { put("ins", led.arr("ins") ?: JsonArray(emptyList())) } }
        })
        val oldMap = (saved["ledmap"] as? JsonPrimitive)?.content
        val restored = oldMap ?: run {
            // No map before: an identity map behaves exactly like having none.
            val total = led.arr("ins")?.mapNotNull { it as? JsonObject }?.maxOfOrNull { it.int("start") + it.int("len") } ?: 0
            buildJsonObject { put("map", JsonArray(List(total) { JsonPrimitive(it) })) }.toString()
        }
        api.uploadFile(master.host, "/ledmap.json", restored)
        backup.delete()
        reboot(master.host)
    }

    private suspend fun reboot(host: String) {
        kotlinx.coroutines.delay(500)
        runCatching { api.postState(host, buildJsonObject { put("rb", true) }) }
    }
}
