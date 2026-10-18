package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SequenceRendererTest {

    private val map = HouseMap(
        aspect = 1f,
        runs = listOf(
            LightRun("roof", "Roof", "d", 0, 4, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f))),
            LightRun("bush", "Bush", "d", 4, 2, points = listOf(MapPoint(0f, 1f), MapPoint(1f, 1f))),
        ),
    )

    private fun seq(vararg tracks: Track) = Sequence("s", "Test", 10_000, tracks.toList())

    private fun red(start: Long, dur: Long, fadeIn: Long = 0, intensityEnd: Float? = null) =
        Clip("c$start", start, dur, SeqEffect.ON, colors = listOf(0xFF0000), fadeInMs = fadeIn, intensityEnd = intensityEnd)

    private fun render(s: Sequence, t: Long) = SequenceRenderer.render(s, SequenceRenderer.prepare(map, s.tracks), t)

    @Test
    fun clipLightsOnlyWhileItRuns() {
        val s = seq(Track("t", "House", TrackTarget(TrackTarget.Kind.HOUSE), listOf(red(1000, 2000))))
        assertEquals(6, render(s, 1500).size)
        render(s, 1500).forEach { assertEquals(0xFF0000, it) }
        render(s, 999).forEach { assertEquals(0, it) }
        render(s, 3000).forEach { assertEquals(0, it) }
    }

    @Test
    fun propTrackOnlyLightsItsProp() {
        val s = seq(Track("t", "Bush", TrackTarget(TrackTarget.Kind.PROP, "bush"), listOf(red(0, 1000))))
        val out = render(s, 500)
        assertEquals(listOf(0, 0, 0, 0, 0xFF0000, 0xFF0000), out.toList())
    }

    @Test
    fun fadeInAndBrightnessRamp() {
        val fade = seq(Track("t", "House", TrackTarget(TrackTarget.Kind.HOUSE), listOf(red(0, 4000, fadeIn = 1000))))
        assertEquals(0x7F0000, render(fade, 500)[0])
        val ramp = seq(Track("t", "House", TrackTarget(TrackTarget.Kind.HOUSE), listOf(red(0, 1000, intensityEnd = 0f))))
        assertEquals(0x7F0000, render(ramp, 500)[0])
    }

    @Test
    fun overlappingTracksKeepTheBrightest() {
        val blue = Clip("b", 0, 1000, SeqEffect.ON, colors = listOf(0x0000FF), intensity = 0.5f)
        val s = seq(
            Track("a", "House", TrackTarget(TrackTarget.Kind.HOUSE), listOf(red(0, 1000))),
            Track("b", "House", TrackTarget(TrackTarget.Kind.HOUSE), listOf(blue)),
        )
        assertEquals(0xFF007F, render(s, 500)[0])
    }

    @Test
    fun mutedTrackIsDark() {
        val s = seq(Track("t", "House", TrackTarget(TrackTarget.Kind.HOUSE), listOf(red(0, 1000)), muted = true))
        render(s, 500).forEach { assertEquals(0, it) }
    }
}
