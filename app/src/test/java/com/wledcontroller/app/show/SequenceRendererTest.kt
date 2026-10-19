package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun house(vararg clips: Clip, blend: BlendMode = BlendMode.MAX, id: String = "t") =
        Track(id, "House", TrackTarget(TrackTarget.Kind.HOUSE), clips.toList(), blend = blend)

    private fun solid(rgb: Int, intensity: Float = 1f) = Clip("c$rgb", 0, 1000, SeqEffect.ON, colors = listOf(rgb), intensity = intensity)

    @Test
    fun blendModesMixWithTheTracksBelow() {
        // The first track in the list is on top of the second.
        fun top(mode: BlendMode, top: Int, below: Int) = render(seq(house(solid(top), blend = mode, id = "a"), house(solid(below), id = "b")), 500)[0]
        assertEquals(0xFF0000, top(BlendMode.NORMAL, 0xFF0000, 0x0000FF))
        assertEquals(0x4000FF, top(BlendMode.ADD, 0x400000, 0x0000FF))
        assertEquals(0x0000BF, top(BlendMode.SUBTRACT, 0x000040, 0x0000FF))
        assertEquals(0x7F007F, top(BlendMode.AVERAGE, 0xFF0000, 0x0000FF))
        assertEquals(0x000000, top(BlendMode.MASK, 0xFFFFFF, 0x0000FF))
        assertEquals(0x0000FF, top(BlendMode.REVEAL, 0xFFFFFF, 0x0000FF))
        assertEquals(0x00007F, top(BlendMode.REVEAL, 0x7F7F7F, 0x0000FF))
        // A blend track with nothing playing leaves what's below alone.
        assertEquals(0x0000FF, render(seq(house(blend = BlendMode.MASK, id = "a"), house(solid(0x0000FF), id = "b")), 500)[0])
    }

    @Test
    fun valueCurvesShapeSettingsOverTheEffect() {
        val up = ValueCurve(CurveShape.UP_DOWN, end = 1f)
        assertEquals(0f, up.at(0f, 0f), 1e-4f)
        assertEquals(1f, up.at(0f, 0.5f), 1e-4f)
        assertEquals(0f, up.at(0f, 1f), 1e-4f)
        val wave = ValueCurve(CurveShape.SINE, end = 1f, cycles = 2f)
        assertEquals(1f, wave.at(0f, 0.25f), 1e-3f)
        assertEquals(0f, wave.at(0f, 0.5f), 1e-3f)
        // Brightness following a curve: half way up a ramp from 0 is half bright.
        val clip = Clip("c", 0, 1000, SeqEffect.ON, colors = listOf(0xFF0000), intensity = 0f, intensityCurve = ValueCurve(CurveShape.RAMP, end = 1f))
        assertEquals(0x7F0000, render(seq(house(clip)), 500)[0])
    }

    @Test
    fun wipeTransitionRevealsAlongTheProp() {
        val clip = Clip("c", 0, 4000, SeqEffect.ON, colors = listOf(0xFF0000), fadeInMs = 1000, inTransition = Transition.WIPE)
        val s = seq(Track("t", "Roof", TrackTarget(TrackTarget.Kind.PROP, "roof"), listOf(clip)))
        val half = render(s, 500)
        assertEquals(0xFF0000, half[0])   // The start of the roofline is in…
        assertEquals(0, half[3])          // …the far end isn't yet.
        render(s, 1500).take(4).forEach { assertEquals(0xFF0000, it) }
    }

    @Test
    fun musicEffectsFollowTheSong() {
        // A song at 60 % loudness throughout.
        val env = AudioEnvelope(FloatArray(500) { 0.6f })
        val vu = Clip("v", 0, 2000, SeqEffect.VU_METER, colors = listOf(0x00FF00), direction = com.wledcontroller.app.house.Direction.ALONG)
        val out = SequenceRenderer.render(seq(Track("t", "Roof", TrackTarget(TrackTarget.Kind.PROP, "roof"), listOf(vu))), SequenceRenderer.prepare(map, listOf(Track("t", "Roof", TrackTarget(TrackTarget.Kind.PROP, "roof"), listOf(vu)))), 1000, audio = env)
        // The roofline's first LEDs light, the last doesn't (60 % ≈ 66 % of 4 LEDs spread 0..1).
        assertTrue(out[0] != 0 && out[1] != 0)
        assertEquals(0, out[3])
        // Without a song the meter stays dark.
        assertEquals(0, render(seq(Track("t", "Roof", TrackTarget(TrackTarget.Kind.PROP, "roof"), listOf(vu))), 1000)[0])
    }

    @Test
    fun textUsesTheRasterItIsGiven() {
        // A one-pixel "font": every pixel opaque.
        Rasters.textMaker = { Raster(4, 1, IntArray(4) { 0xFFFFFFFF.toInt() }) }
        try {
            val clip = Clip("x", 0, 2000, SeqEffect.TEXT, colors = listOf(0xFF0000), speed = 0f, size = 1f, text = "HI")
            val out = render(seq(house(clip)), 500)
            assertTrue(out.any { it == 0xFF0000 })
        } finally {
            Rasters.textMaker = null
        }
    }

    @Test
    fun newEffectsDrawSomething() {
        for (fx in listOf(
            SeqEffect.SPIRALS, SeqEffect.BUTTERFLY, SeqEffect.METEORS, SeqEffect.FIRE, SeqEffect.RIPPLE, SeqEffect.SHOCKWAVE,
            SeqEffect.CURTAIN, SeqEffect.PINWHEEL, SeqEffect.GALAXY, SeqEffect.PLASMA, SeqEffect.MARQUEE, SeqEffect.GARLANDS,
        )) {
            val clip = Clip("e", 0, 10_000, fx, colors = listOf(0xFF0000, 0x00FF00, 0x0000FF))
            val lit = (0 until 40).sumOf { k -> render(seq(house(clip)), k * 250L).count { it != 0 } }
            assertTrue("$fx stayed dark", lit > 0)
        }
    }

    @Test
    fun faceSingsWithTheSong() {
        val face = LightRun("f", "Face", "d", 0, 8, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
            model = com.wledcontroller.app.house.PropModel(com.wledcontroller.app.house.ModelKind.FACE, nodes = 2))
        val faceMap = HouseMap(aspect = 1f, runs = listOf(face))
        val clip = Clip("s", 0, 5000, SeqEffect.SING, colors = listOf(0x00FF00, 0xFF0000))
        val tracks = listOf(Track("t", "Face", TrackTarget(TrackTarget.Kind.PROP, "f"), listOf(clip)))
        val layout = SequenceRenderer.prepare(faceMap, tracks)
        // Quiet: eyes and the closed mouth. Loud: eyes and the wide mouth.
        val quiet = SequenceRenderer.render(Sequence("q", "Q", 5000, tracks), layout, 1000, audio = AudioEnvelope(FloatArray(500) { 0.05f }))
        assertEquals(listOf(0x00FF00, 0x00FF00, 0xFF0000, 0xFF0000, 0, 0, 0, 0), quiet.toList())
        val loud = SequenceRenderer.render(Sequence("q", "Q", 5000, tracks), layout, 1000, audio = AudioEnvelope(FloatArray(500) { 0.8f }))
        assertEquals(listOf(0x00FF00, 0x00FF00, 0, 0, 0, 0, 0xFF0000, 0xFF0000), loud.toList())
    }

    @Test
    fun movingHeadsFollowPanAndTilt() {
        val clip = Clip("m", 0, 1000, SeqEffect.ON, colors = listOf(0xFFFFFF), pan = 0f, panCurve = ValueCurve(CurveShape.RAMP, end = 1f), tilt = 0.8f)
        val s = seq(house(clip))
        val motion = FloatArray(12)
        SequenceRenderer.render(s, SequenceRenderer.prepare(map, s.tracks), 500, motion = motion)
        assertEquals(0.5f, motion[0], 1e-4f)   // half way along the pan ramp
        assertEquals(0.8f, motion[1], 1e-4f)
        // No aim set: straight ahead.
        SequenceRenderer.render(seq(house(solid(0xFF0000))), SequenceRenderer.prepare(map, s.tracks), 500, motion = motion)
        assertEquals(0.5f, motion[1], 1e-4f)
    }
}
