package com.wledcontroller.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * What each controller last told the app at home (its info, effect and palette names, effect
 * details and presets), so the controls still make sense when it's only reachable over MQTT,
 * which carries just the basic state.
 */
class DeviceCache(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun file(id: String, part: String) = File(dir, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.$part.json")

    fun save(id: String, part: String, value: JsonElement) {
        runCatching {
            dir.mkdirs()
            val f = file(id, part)
            val tmp = File(f.path + ".tmp")
            tmp.writeText(value.toString())
            tmp.renameTo(f)
        }
    }

    fun load(id: String, part: String): JsonElement? =
        runCatching { file(id, part).takeIf { it.exists() }?.readText()?.let(json::parseToJsonElement) }.getOrNull()

    companion object {
        const val FULL = "json"
        const val FX_DATA = "fxdata"
        const val PRESETS = "presets"
    }
}
