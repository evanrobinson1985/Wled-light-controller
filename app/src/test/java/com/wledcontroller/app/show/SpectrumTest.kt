package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropModel
import com.wledcontroller.app.house.PropType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumTest {
    private fun loudestBand(freq: Float): Int {
        val a = BandAnalyzer(44_100)
        for (i in 0 until 2048) a.push(sin(2 * PI * freq * i / 44_100).toFloat())
        val b = a.bands()
        return b.indices.maxByOrNull { b[it] }!!
    }

    @Test
    fun bassAndTrebleLandInTheirBands() {
        assertEquals(0, loudestBand(60f))
        assertEquals(BandAnalyzer.BANDS - 1, loudestBand(8000f))
        val mid = loudestBand(BandAnalyzer.frequency(6))
        assertTrue(mid in 5..7)
    }

    @Test
    fun musicEffectDrawsTheSpectrum() {
        val panel = LightRun("p", "Panel", "d", 0, 12 * 10, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
            prop = PropType.MATRIX, model = PropModel(ModelKind.MATRIX, strings = 12, nodes = 10, vertical = true, zigzag = false))
        val map = HouseMap(aspect = 1.2f, runs = listOf(panel))
        // Loud bass, silent treble.
        val bands = Array(BandAnalyzer.BANDS) { b -> FloatArray(100) { if (b < 3) 0.9f else 0f } }
        val env = AudioEnvelope(FloatArray(100) { 0.5f }, bands)
        val clip = Clip("m", 0, 1000, SeqEffect.MUSIC, colors = listOf(0xFF0000, 0x0000FF))
        val tracks = listOf(Track("t", "P", TrackTarget(TrackTarget.Kind.PROP, "p"), listOf(clip)))
        val out = SequenceRenderer.render(Sequence("s", "S", 1000, tracks), SequenceRenderer.prepare(map, tracks), 500, audio = env)
        // The left strings (bass) are lit most of the way up; the right ones are dark.
        val left = (0 until 10).count { out[it] != 0 }
        val right = (110 until 120).count { out[it] != 0 }
        assertTrue("left $left right $right", left >= 8 && right == 0)
    }
}
