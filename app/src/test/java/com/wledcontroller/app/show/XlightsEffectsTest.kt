package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropModel
import com.wledcontroller.app.house.PropType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlightsEffectsTest {
    // A 24 × 16 matrix filling the picture, and a 50-LED roofline.
    private val panel = LightRun(
        "p", "Panel", "d", 0, 24 * 16, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
        prop = PropType.MATRIX, model = PropModel(ModelKind.MATRIX, strings = 24, nodes = 16, vertical = true, zigzag = false),
    )
    private val roof = LightRun("r", "Roof", "d", 400, 50, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f)))
    private val map = HouseMap(aspect = 1.5f, runs = listOf(panel, roof))

    private fun frame(clip: Clip, target: String, ms: Long, audio: AudioEnvelope? = null): IntArray {
        val tracks = listOf(Track("t", "T", TrackTarget(TrackTarget.Kind.PROP, target), listOf(clip)))
        val layout = SequenceRenderer.prepare(map, tracks)
        val all = SequenceRenderer.render(Sequence("s", "S", 20_000, tracks), layout, ms, audio = audio)
        return layout.tracks[0].ledIndex.map { all[it] }.toIntArray()
    }

    private val newEffects = listOf(
        SeqEffect.COLOR_WASH, SeqEffect.FILL, SeqEffect.CANDLE, SeqEffect.CIRCLES, SeqEffect.FAN, SeqEffect.KALEIDOSCOPE,
        SeqEffect.LIFE, SeqEffect.LIGHTNING, SeqEffect.LINES, SeqEffect.LIQUID, SeqEffect.MORPH, SeqEffect.SHAPE,
        SeqEffect.SINGLE_STRAND, SeqEffect.SNOWFLAKES, SeqEffect.SPIROGRAPH, SeqEffect.TENDRIL, SeqEffect.TREE,
        SeqEffect.WARP, SeqEffect.WAVE, SeqEffect.PIANO,
    )

    @Test
    fun everyNewEffectDrawsAndMoves() {
        val loud = AudioEnvelope(FloatArray(2000) { 0.8f })
        for (fx in newEffects) {
            val clip = Clip("e", 0, 10_000, fx, colors = listOf(0xFF0000, 0x00FF00, 0x0000FF))
            val frames = (0 until 20).map { k -> frame(clip, "p", 300L + k * 450L, loud).toList() }
            assertTrue("$fx stayed dark", frames.any { f -> f.any { it != 0 } })
            assertTrue("$fx never changes", frames.toSet().size > 1)
        }
        // Off is off.
        assertTrue(frame(Clip("o", 0, 1000, SeqEffect.OFF, colors = listOf(0xFFFFFF)), "p", 500).all { it == 0 })
    }

    @Test
    fun singleStrandAndMorphWorkOnALine() {
        for (fx in listOf(SeqEffect.SINGLE_STRAND, SeqEffect.MORPH, SeqEffect.FILL, SeqEffect.COLOR_WASH, SeqEffect.CANDLE)) {
            val clip = Clip("e", 0, 4000, fx, colors = listOf(0xFF0000, 0x0000FF))
            assertTrue("$fx on a roofline", (0 until 8).any { k -> frame(clip, "r", 200L + k * 450L).any { it != 0 } })
        }
    }

    @Test
    fun lifeFollowsTheRules() {
        // The board moves on but some cells live from one generation to the next.
        val clip = Clip("life", 0, 20_000, SeqEffect.LIFE, colors = listOf(0xFFFFFF), speed = 0.2f)
        val a = frame(clip, "p", 1000); val b = frame(clip, "p", 3000)
        assertNotEquals(a.toList(), b.toList())
        assertTrue(a.any { it != 0 } && b.any { it != 0 })
        // Rendering the same moment twice (even after others) gives the same board.
        frame(clip, "p", 9000)
        assertEquals(a.toList(), frame(clip, "p", 1000).toList())
    }

    @Test
    fun layerSettingsTurnZoomRecolourAndSparkle() {
        val wipe = Clip("w", 0, 2000, SeqEffect.WIPE, colors = listOf(0xFF0000), direction = com.wledcontroller.app.house.Direction.LEFT_RIGHT)
        // Half way through a left-to-right wipe the left half is lit; mirrored, the right half.
        val plain = frame(wipe, "p", 1000)
        val mirrored = frame(wipe.copy(mirrorX = true), "p", 1000)
        val half = plain.count { it != 0 }
        assertTrue(half in 120..260)
        assertNotEquals(plain.toList(), mirrored.toList())
        assertEquals(half.toFloat(), mirrored.count { it != 0 }.toFloat(), 30f)
        // Turned half way round, a left-to-right wipe fills from the right instead.
        val turned = frame(wipe.copy(rotation = 0.5f), "p", 1000)
        assertTrue((turned.zip(mirrored.toList()).count { (a, b) -> (a != 0) == (b != 0) }) > 300)

        // A third of the way round the colour wheel: red becomes green.
        val on = Clip("h", 0, 1000, SeqEffect.ON, colors = listOf(0xFF0000))
        assertTrue(frame(on.copy(hueShift = 1f / 3f), "p", 500).all { (it shr 8 and 0xFF) > 200 && (it shr 16 and 0xFF) < 30 })

        // Sparkles put white on a red panel.
        assertTrue(frame(on.copy(sparkles = 1f), "p", 500).any { it == 0xFFFFFF })
        assertTrue(frame(on, "p", 500).none { it == 0xFFFFFF })

        // Zoomed in, a few broad bars fill the panel with fewer colour changes.
        val bars = Clip("b", 0, 1000, SeqEffect.BARS, colors = listOf(0xFF0000, 0x0000FF), speed = 0f, size = 0f, direction = com.wledcontroller.app.house.Direction.LEFT_RIGHT)
        fun changes(f: IntArray) = (1 until 24).count { x -> f[x * 16] != f[(x - 1) * 16] }
        assertTrue(changes(frame(bars.copy(zoom = 3f), "p", 500)) < changes(frame(bars, "p", 500)))
    }

    @Test
    fun hueShiftKeepsGreysAndTurnsColours() {
        assertEquals(0x808080, SequenceRenderer.hueShift(0x808080, 0.3f))
        assertEquals(0x0000FF, SequenceRenderer.hueShift(0x00FF00, 1f / 3f))
        assertEquals(0xFF0000, SequenceRenderer.hueShift(0xFF0000, 1f))
    }

    @Test
    fun xlightsEffectsImportAsTheirOwn() {
        assertEquals(SeqEffect.COLOR_WASH, XsqImport.effectFor("Color Wash"))
        assertEquals(SeqEffect.LIFE, XsqImport.effectFor("Life"))
        assertEquals(SeqEffect.SINGLE_STRAND, XsqImport.effectFor("Single Strand"))
        assertEquals(SeqEffect.KALEIDOSCOPE, XsqImport.effectFor("Kaleidoscope"))
    }
}
