package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropModel
import com.wledcontroller.app.house.XlightsModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FacesTest {
    private val n = 2
    private val face = LightRun("f", "Face", "d", 0, PropModel.XL_FACE_PARTS.size * n, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
        model = PropModel(ModelKind.SINGING_FACE, nodes = n))
    private val map = HouseMap(aspect = 1f, runs = listOf(face))

    /** Which parts are lit (by name) at [ms]. */
    private fun lit(seq: Sequence, ms: Long, audio: AudioEnvelope? = null): Set<String> {
        val layout = SequenceRenderer.prepare(map, seq.tracks)
        val out = SequenceRenderer.render(seq, layout, ms, audio = audio)
        return PropModel.XL_FACE_PARTS.filterIndexed { p, _ -> (0 until n).all { out[p * n + it] != 0 } }.toSet()
    }

    private fun sing(lyrics: Lyrics?) = Sequence(
        "s", "S", 10_000,
        listOf(Track("t", "Face", TrackTarget(TrackTarget.Kind.PROP, "f"), listOf(Clip("c", 0, 10_000, SeqEffect.SING, colors = listOf(0xFFFFFF, 0xFF0000, 0x00FF00))))),
        lyrics = lyrics,
    )

    @Test
    fun eachSoundShowsItsOwnMouth() {
        val lyrics = Lyrics(
            listOf(TimedLabel(1000, 3000, "Oh me")), listOf(TimedLabel(1000, 3000, "Oh me")),
            listOf(TimedLabel(1000, 1500, "O"), TimedLabel(1500, 2000, "MBP"), TimedLabel(2000, 2500, "E"), TimedLabel(2500, 3000, "WQ")),
        )
        val s = sing(lyrics)
        assertEquals(setOf("Eyes-Open", "Outline", "Mouth-O"), lit(s, 1200))
        assertEquals(setOf("Eyes-Open", "Outline", "Mouth-MBP"), lit(s, 1700))
        assertEquals(setOf("Eyes-Open", "Outline", "Mouth-E"), lit(s, 2200))
        assertEquals(setOf("Eyes-Open", "Outline", "Mouth-WQ"), lit(s, 2700))
        // Between words the mouth rests.
        assertEquals(setOf("Eyes-Open", "Outline", "Mouth-rest"), lit(s, 5000))
        // A blink: eyes closed for a moment (3.7 s cycle, the last 4%).
        assertTrue("Eyes-Closed" in lit(s, 3600))
    }

    @Test
    fun withoutLyricsTheMouthFollowsTheMusic() {
        val quiet = AudioEnvelope(FloatArray(1000) { 0.02f })
        val loud = AudioEnvelope(FloatArray(1000) { 0.9f })
        assertTrue("Mouth-rest" in lit(sing(null), 1000, quiet))
        assertTrue("Mouth-AI" in lit(sing(null), 1000, loud))
    }

    @Test
    fun xlightsFaceDefinitionsImportAsParts() {
        val xml = """<custommodel name="Singer" DisplayAs="Custom" parm1="4" parm2="1" CustomModel="1,2,3,4">
            <faceInfo Name="Default" Type="NodeRange" Mouth-AI="1-2" Mouth-O="3" Eyes-Open="4" Mouth-AI-Color="#FF0000"/>
        </custommodel>"""
        val m = XlightsModels.parseXmodel(xml.byteInputStream())
        assertEquals(setOf("Mouth-AI", "Mouth-O", "Eyes-Open"), m.submodels.map { it.name }.toSet())
        assertEquals(listOf(0..1), m.submodels.first { it.name == "Mouth-AI" }.ranges)
        assertEquals(FaceRole.MOUTH + Phoneme.AI.ordinal, FaceRole.of(setOf("mouth-ai")))
        assertEquals(FaceRole.EYES_CLOSED, FaceRole.of(setOf("eyes-closed")))
    }
}

class MoreCurvesTest {
    private val map = HouseMap(aspect = 1f, runs = listOf(LightRun("r", "Roof", "d", 0, 200, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f)))))
    private fun whites(clip: Clip, ms: Long): Int {
        val tracks = listOf(Track("t", "R", TrackTarget(TrackTarget.Kind.PROP, "r"), listOf(clip)))
        return SequenceRenderer.render(Sequence("s", "S", 10_000, tracks), SequenceRenderer.prepare(map, tracks), ms).count { it == 0xFFFFFF }
    }

    @Test
    fun sparklesFollowTheirCurve() {
        val clip = Clip("c", 0, 10_000, SeqEffect.ON, colors = listOf(0xFF0000), sparkles = 0f, sparklesCurve = ValueCurve(CurveShape.RAMP, end = 1f))
        assertEquals(0, whites(clip, 0))
        assertTrue(whites(clip, 9900) > 10)
    }

    @Test
    fun blurFollowsItsCurve() {
        // A single lit LED in the middle (a short comet): blurred at the end, sharp at the start.
        val clip = Clip("c", 0, 10_000, SeqEffect.MARQUEE, colors = listOf(0xFF0000), blur = 0f, blurCurve = ValueCurve(CurveShape.RAMP, end = 1f))
        val tracks = listOf(Track("t", "R", TrackTarget(TrackTarget.Kind.PROP, "r"), listOf(clip)))
        val layout = SequenceRenderer.prepare(map, tracks)
        fun levels(ms: Long) = SequenceRenderer.render(Sequence("s", "S", 10_000, tracks), layout, ms).map { (it shr 16) and 0xFF }.toSet().size
        assertTrue(levels(9900) > levels(0))
    }
}
