package com.wledcontroller.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thin client for the WLED JSON API (https://kno.wled.ge/interfaces/json-api/). */
class WledApi(val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonType = "application/json".toMediaType()

    private val probeClient = client.newBuilder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .callTimeout(3000, TimeUnit.MILLISECONDS)
        .build()

    fun baseUrl(host: String) = "http://" + normalizeHost(host)

    suspend fun get(host: String, path: String, http: OkHttpClient = client): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl(host) + path).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $path")
            json.parseToJsonElement(resp.body?.string() ?: throw IOException("Empty body"))
        }
    }

    suspend fun postState(host: String, body: JsonObject): JsonElement = post(host, "/json/state", body)

    /** Device configuration (LED hardware, power limit …) — same shape as WLED's cfg.json. */
    suspend fun config(host: String): JsonObject = get(host, "/json/cfg") as? JsonObject ?: throw IOException("Bad config")

    suspend fun postConfig(host: String, body: JsonObject): JsonElement = post(host, "/json/cfg", body)

    /** Uploads a file to the device's filesystem (e.g. "/ledmap.json"). */
    suspend fun uploadFile(host: String, name: String, content: String): Unit = withContext(Dispatchers.IO) {
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("data", name, content.toRequestBody(jsonType))
            .build()
        val req = Request.Builder().url(baseUrl(host) + "/upload").post(body).build()
        client.newCall(req).execute().use { resp -> if (!resp.isSuccessful) throw IOException("Upload failed: HTTP ${resp.code}") }
    }

    private suspend fun post(host: String, path: String, body: JsonObject): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(baseUrl(host) + path)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
        }
    }

    /** Returns device info if [host] answers like a WLED device, else null. */
    suspend fun probe(host: String): WledInfo? = try {
        val o = get(host, "/json/info", probeClient).jsonObject
        if (o.containsKey("leds") && (o.containsKey("ver") || o.str("brand") == "WLED")) WledInfo.parse(o) else null
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

    suspend fun effectData(host: String): List<String> =
        (get(host, "/json/fxdata") as? JsonArray)?.map { it.asString() ?: "" } ?: emptyList()

    suspend fun presets(host: String): List<Preset> {
        val o = get(host, "/presets.json") as? JsonObject ?: return emptyList()
        return o.mapNotNull { (k, v) ->
            val id = k.toIntOrNull() ?: return@mapNotNull null
            val p = v as? JsonObject ?: return@mapNotNull null
            if (id == 0 || p.isEmpty()) return@mapNotNull null
            Preset(
                id = id,
                name = p.str("n")?.takeIf { it.isNotBlank() } ?: "Preset $id",
                isPlaylist = p.containsKey("playlist"),
                quickLabel = p.str("ql")?.takeIf { it.isNotBlank() },
            )
        }.sortedBy { it.id }
    }

    /** Fetches gradient previews for all palettes. Older firmware without /json/palx returns empty. */
    suspend fun palettePreviews(host: String): Map<Int, List<PaletteStop>> {
        val out = mutableMapOf<Int, List<PaletteStop>>()
        var page = 0
        var maxPage = 0
        do {
            val o = get(host, "/json/palx?page=$page") as? JsonObject ?: break
            maxPage = o.int("m")
            o.obj("p")?.forEach { (k, v) ->
                val id = k.toIntOrNull() ?: return@forEach
                val arr = v as? JsonArray ?: return@forEach
                out[id] = parseStops(arr)
            }
            page++
        } while (page <= maxPage && page < 40)
        return out
    }

    private fun parseStops(arr: JsonArray): List<PaletteStop> {
        val n = arr.size
        return arr.mapIndexedNotNull { i, el ->
            val evenPos = if (n <= 1) 0f else i / (n - 1f)
            when (el) {
                is JsonArray -> PaletteStop(
                    pos = el.getOrNull(0).asInt() / 255f,
                    color = (0xFF shl 24) or (el.getOrNull(1).asInt() shl 16) or (el.getOrNull(2).asInt() shl 8) or el.getOrNull(3).asInt(),
                )
                else -> when (val s = el.asString()) {
                    "r" -> PaletteStop(evenPos, random = true)
                    "c1", "c2", "c3" -> PaletteStop(evenPos, slot = s[1] - '1')
                    else -> null
                }
            }
        }
    }

    companion object {
        fun normalizeHost(host: String) = host.trim()
            .removePrefix("http://").removePrefix("https://")
            .trimEnd('/')
    }
}
