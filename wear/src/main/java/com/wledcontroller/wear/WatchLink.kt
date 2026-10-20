package com.wledcontroller.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A controller, as the phone app knows it. */
@Serializable
data class WatchDevice(val id: String, val name: String, val host: String)

/** What a controller is doing, enough for the watch. */
data class LightState(val on: Boolean, val bri: Int, val fx: Int, val pal: Int, val ledCount: Int)

/** Reading WLED's answers; kept apart from the network so it can be tested. */
object WledParse {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun devices(text: String?): List<WatchDevice> =
        text?.let { runCatching { json.decodeFromString<List<WatchDevice>>(it) }.getOrNull() } ?: emptyList()

    /** From /json/si: { "state": {...}, "info": {...} }. */
    fun state(text: String): LightState {
        val o = json.parseToJsonElement(text) as JsonObject
        val st = o["state"] as? JsonObject ?: o
        val info = o["info"] as? JsonObject
        val seg = (st["seg"] as? JsonArray)?.let { segs ->
            val main = (st["mainseg"] as? JsonPrimitive)?.intOrNull ?: 0
            segs.mapNotNull { it as? JsonObject }.firstOrNull { (it["id"] as? JsonPrimitive)?.intOrNull == main } ?: segs.firstOrNull() as? JsonObject
        }
        fun JsonObject?.int(k: String) = (this?.get(k) as? JsonPrimitive)?.intOrNull ?: 0
        return LightState(
            on = (st["on"] as? JsonPrimitive)?.booleanOrNull ?: false,
            bri = st.int("bri"),
            fx = seg.int("fx"),
            pal = seg.int("pal"),
            ledCount = ((info?.get("leds") as? JsonObject)?.get("count") as? JsonPrimitive)?.intOrNull ?: 0,
        )
    }

    /** From /json/eff: the effect names, by number. */
    fun names(text: String): List<String> =
        (json.parseToJsonElement(text) as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } ?: emptyList()

    /** From /presets.json: (id, name, is a playlist), in order. */
    fun presets(text: String): List<Triple<Int, String, Boolean>> {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        return o.mapNotNull { (k, v) ->
            val id = k.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val p = v as? JsonObject ?: return@mapNotNull null
            if (p.isEmpty()) return@mapNotNull null
            val name = (p["n"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Preset $id"
            Triple(id, name, p.containsKey("playlist"))
        }.sortedBy { it.first }
    }
}

/**
 * The watch's way to the lights. The controller list (and favourite effects) come from the phone
 * app over the Wear OS data layer. Requests go through the phone when it's connected (the watch is
 * usually on Bluetooth to it, which can't reach the home network), otherwise straight to the
 * controller over the watch's own Wi-Fi.
 */
class WatchLink(private val context: Context) {
    private val prefs = context.getSharedPreferences("watch", Context.MODE_PRIVATE)
    private val _devices = MutableStateFlow(WledParse.devices(prefs.getString("devices", null)))
    val devices: StateFlow<List<WatchDevice>> = _devices.asStateFlow()
    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet()).orEmpty())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val http = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    /** Takes in the phone's controller list from a data item. */
    private fun take(map: com.google.android.gms.wearable.DataMap) {
        val list = map.getString("devices")
        val favs = map.getStringArrayList("favorites").orEmpty().toSet()
        prefs.edit().putString("devices", list).putStringSet("favorites", favs).apply()
        _devices.value = WledParse.devices(list)
        _favorites.value = favs
    }

    /** Fetches the phone's latest controller list. */
    suspend fun refreshDevices() {
        runCatching {
            val items = Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*$DEVICES_PATH")).await()
            try {
                items.forEach { take(DataMapItem.fromDataItem(it).dataMap) }
            } finally {
                items.release()
            }
        }
    }

    /** Follows changes to the controller list while the app is open. */
    val listener = DataClient.OnDataChangedListener { events ->
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == DEVICES_PATH) take(DataMapItem.fromDataItem(e.dataItem).dataMap)
        }
    }

    private suspend fun viaPhone(d: WatchDevice, path: String, body: String?): String? {
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.await() }.getOrNull().orEmpty()
        val request = buildJsonObject {
            put("id", d.id); put("host", d.host); put("path", path)
            if (body != null) put("body", body)
        }.toString().toByteArray()
        for (n in nodes) {
            val answer = runCatching { Wearable.getMessageClient(context).sendRequest(n.id, HTTP_PATH, request).await() }.getOrNull()
            if (answer != null) return String(answer)
        }
        return null
    }

    private suspend fun direct(d: WatchDevice, path: String, body: String?): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("http://" + d.host.removePrefix("http://").trimEnd('/') + path).apply {
                if (body != null) post(body.toRequestBody("application/json".toMediaType()))
            }.build()
            http.newCall(req).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
        }.getOrNull()
    }

    private suspend fun call(d: WatchDevice, path: String, body: String? = null): String =
        viaPhone(d, path, body) ?: direct(d, path, body) ?: throw IOException("Can't reach ${d.name}")

    suspend fun state(d: WatchDevice): LightState = WledParse.state(call(d, "/json/si"))
    suspend fun effects(d: WatchDevice): List<String> = WledParse.names(call(d, "/json/eff"))
    suspend fun presets(d: WatchDevice): List<Triple<Int, String, Boolean>> = WledParse.presets(call(d, "/presets.json"))
    suspend fun send(d: WatchDevice, patch: JsonObject) { call(d, "/json/state", patch.toString()) }

    companion object {
        const val DEVICES_PATH = "/wled/devices"
        const val HTTP_PATH = "/wled/http"
    }
}
