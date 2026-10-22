package com.wledcontroller.app.show

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The weather now, enough to decide whether to run a show. */
data class WeatherNow(val rainMmPerHour: Float, val snowCmPerHour: Float, val gustMph: Float, val windMph: Float, val thunder: Boolean)

/** When to hold off scheduled shows because of the weather. */
@Serializable
data class WeatherRules(
    val enabled: Boolean = false,
    /** Hold off at or above this much rain, mm an hour (0.5 = steady rain, 4 = heavy). */
    val rainMm: Float = 2.5f,
    /** Hold off when gusts reach this, mph. */
    val gustMph: Float = 40f,
    val snow: Boolean = false,
    val thunder: Boolean = true,
) {
    /** Why to hold off, or null to go ahead. */
    fun holdOff(w: WeatherNow): String? = when {
        !enabled -> null
        thunder && w.thunder -> "thunderstorm"
        w.rainMmPerHour >= rainMm -> "rain (%.1f mm an hour)".format(w.rainMmPerHour)
        w.gustMph >= gustMph -> "wind gusts of %.0f mph".format(w.gustMph)
        snow && w.snowCmPerHour > 0.1f -> "snow"
        else -> null
    }
}

/** Reads current conditions from Open-Meteo (free, no account). */
object WeatherService {
    fun url(at: GeoPoint) = "https://api.open-meteo.com/v1/forecast?latitude=${at.lat}&longitude=${at.lon}" +
        "&current=rain,showers,snowfall,wind_speed_10m,wind_gusts_10m,weather_code&wind_speed_unit=mph"

    /** Parses Open-Meteo's answer. Thunderstorms are weather codes 95–99. */
    fun parse(text: String): WeatherNow? = runCatching {
        val cur = (Json.parseToJsonElement(text) as JsonObject)["current"] as JsonObject
        fun num(k: String) = cur[k]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f
        val code = num("weather_code").toInt()
        WeatherNow(num("rain") + num("showers"), num("snowfall"), num("wind_gusts_10m"), num("wind_speed_10m"), code in 95..99)
    }.getOrNull()
}
