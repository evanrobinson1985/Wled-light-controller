package com.wledcontroller.app.show

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Where the show is, for sunset times. */
@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

/** Sunrise, sunset and dusk times (the Almanac for Computers algorithm; within a minute or two). */
object Solar {
    private const val OFFICIAL = 90.833 // Sun's upper edge on the horizon, with refraction.
    private const val CIVIL = 96.0 // Civil twilight: the sky is dark enough for lights to pop.

    fun sunset(date: LocalDate, at: GeoPoint, zone: ZoneId): Int? = event(date, at, zone, rising = false, zenith = OFFICIAL)
    fun dusk(date: LocalDate, at: GeoPoint, zone: ZoneId): Int? = event(date, at, zone, rising = false, zenith = CIVIL)
    fun sunrise(date: LocalDate, at: GeoPoint, zone: ZoneId): Int? = event(date, at, zone, rising = true, zenith = OFFICIAL)

    /** Minutes after local midnight on [date], or null when the sun doesn't rise or set that day. */
    private fun event(date: LocalDate, at: GeoPoint, zone: ZoneId, rising: Boolean, zenith: Double): Int? {
        fun rad(d: Double) = Math.toRadians(d)
        fun deg(r: Double) = Math.toDegrees(r)
        fun norm(v: Double, m: Double) = ((v % m) + m) % m
        val n = date.dayOfYear
        val lngHour = at.lon / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634, 360.0)
        var ra = norm(deg(atan(0.91764 * tan(rad(l)))), 360.0)
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(zenith)) - sinDec * sin(rad(at.lat))) / (cosDec * cos(rad(at.lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360.0 - deg(acos(cosH)) else deg(acos(cosH))) / 15.0
        val localMean = h + ra - 0.06571 * t - 6.622
        val ut = norm(localMean - lngHour, 24.0)
        // UT on that date, shown in the local zone (an evening event can fall on the next UT day).
        val utc = date.atStartOfDay().plusSeconds((ut * 3600).toLong())
        val local = utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
        var min = local.hour * 60 + local.minute
        if (local.toLocalDate().isBefore(date)) min -= 1440 else if (local.toLocalDate().isAfter(date)) min += 1440
        // Western zones: an evening event's UT date is the next day; bring it back to [date].
        if (!rising && min < 6 * 60) min += 1440
        if (!rising && min >= 1440 + 6 * 60) min -= 1440
        return min.coerceIn(0, 2 * 1440)
    }
}
