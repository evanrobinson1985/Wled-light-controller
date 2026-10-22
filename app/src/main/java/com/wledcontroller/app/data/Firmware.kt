package com.wledcontroller.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * WLED firmware updates: the newest official release from GitHub, and which of its files fits
 * each controller (by the "release" name the controller reports, e.g. ESP32 or ESP8266).
 */
object Firmware {
    const val LATEST_URL = "https://api.github.com/repos/wled/WLED/releases/latest"

    data class Asset(val name: String, val url: String)
    data class Release(val version: String, val assets: List<Asset>)

    fun parseRelease(text: String): Release? = runCatching {
        val o = Json.parseToJsonElement(text) as JsonObject
        val tag = o["tag_name"]!!.jsonPrimitive.content.removePrefix("v")
        val assets = (o["assets"] as? JsonArray).orEmpty().mapNotNull { a ->
            val m = a as? JsonObject ?: return@mapNotNull null
            Asset(m["name"]?.jsonPrimitive?.content ?: return@mapNotNull null, m["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null)
        }
        Release(tag, assets)
    }.getOrNull()

    /** The firmware file for a controller whose build is [releaseName] (WLED's info "release"). */
    fun assetFor(release: Release, releaseName: String?): Asset? {
        val r = releaseName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return release.assets.firstOrNull { it.name.endsWith(".bin") && it.name.substringBeforeLast(".bin").endsWith("_$r", ignoreCase = true) }
    }

    /** Whether [latest] is newer than [current] ("0.15.0" > "0.14.4"; "-b1" betas sort before their release). */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String): List<Int> = v.removePrefix("v").substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        // Same numbers: a full release beats a beta of it.
        return current.contains('-') && !latest.contains('-')
    }
}
