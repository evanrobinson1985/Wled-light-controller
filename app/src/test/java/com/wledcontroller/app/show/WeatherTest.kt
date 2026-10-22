package com.wledcontroller.app.show

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherTest {
    @Test
    fun readsOpenMeteo() {
        val w = WeatherService.parse("""{"current":{"time":"2026-12-24T18:00","rain":1.2,"showers":0.8,"snowfall":0.0,"wind_speed_10m":12.5,"wind_gusts_10m":31.0,"weather_code":63}}""")!!
        assertEquals(2.0f, w.rainMmPerHour, 0.01f)
        assertEquals(31f, w.gustMph, 0.01f)
        assertEquals(false, w.thunder)
        assertNull(WeatherService.parse("not json"))
    }

    @Test
    fun holdsOffOnlyWhenItShould() {
        val rules = WeatherRules(enabled = true, rainMm = 2.5f, gustMph = 40f)
        assertNull(rules.holdOff(WeatherNow(1f, 0f, 20f, 10f, false)))
        assertEquals("rain (3.0 mm an hour)", rules.holdOff(WeatherNow(3f, 0f, 20f, 10f, false)))
        assertEquals("wind gusts of 45 mph", rules.holdOff(WeatherNow(0f, 0f, 45f, 20f, false)))
        assertEquals("thunderstorm", rules.holdOff(WeatherNow(0f, 0f, 0f, 0f, true)))
        assertNull(rules.copy(enabled = false).holdOff(WeatherNow(9f, 0f, 60f, 30f, true)))
    }
}
