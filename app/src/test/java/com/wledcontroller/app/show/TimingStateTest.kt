package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropModel
import com.wledcontroller.app.house.PropType
import com.wledcontroller.app.house.Submodel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingStateTest {
    @Test
    fun marksRunUntilTheNextOne() {
        var tt = TimingTrack("t", "Sections")
        tt = tt.withMark(10_000, "Chorus", 60_000).withMark(0, "Verse", 60_000).withMark(30_000, "Bridge", 60_000)
        assertEquals(listOf("Verse", "Chorus", "Bridge"), tt.marks.map { it.label })
        assertEquals(listOf(10_000L, 30_000L, 60_000L), tt.marks.map { it.endMs })
        assertEquals("Chorus", tt.labelAt(15_000))
        tt = tt.without(10_000, 60_000)
        assertEquals("Verse", tt.labelAt(15_000))
    }

    private val sign = LightRun(
        "s", "Sign", "d", 0, 10, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f)),
        submodels = listOf(Submodel("Joy", listOf(0..4)), Submodel("Peace", listOf(5..9))),
    )
    private val map = HouseMap(runs = listOf(sign))

    @Test
    fun stateLightsThePartTheLabelNames() {
        val tt = TimingTrack("tt", "Words").withMark(0, "Joy", 4000).withMark(2000, "Peace, joy", 4000)
        val clip = Clip("c", 0, 4000, SeqEffect.STATE, colors = listOf(0xFF0000, 0x00FF00), timingTrackId = "tt")
        val tracks = listOf(Track("t", "Sign", TrackTarget(TrackTarget.Kind.PROP, "s"), listOf(clip)))
        val seq = Sequence("q", "Q", 4000, tracks, timingTracks = listOf(tt))
        val layout = SequenceRenderer.prepare(map, tracks)
        assertEquals(List(5) { 0xFF0000 } + List(5) { 0 }, SequenceRenderer.render(seq, layout, 1000).toList())
        // "Peace, joy": both parts, each in its own colour (first label, first colour).
        assertEquals(List(5) { 0x00FF00 } + List(5) { 0xFF0000 }, SequenceRenderer.render(seq, layout, 3000).toList())
    }

    @Test
    fun sketchDrawsItselfOverTheEffect() {
        val panel = LightRun("p", "Panel", "d", 0, 20 * 20, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
            prop = PropType.MATRIX, model = PropModel(ModelKind.MATRIX, strings = 20, nodes = 20))
        val m = HouseMap(aspect = 1f, runs = listOf(panel))
        // A line across the middle, left to right.
        val clip = Clip("k", 0, 4000, SeqEffect.SKETCH, colors = listOf(0xFFFFFF), size = 0.3f, sketch = listOf(MapPoint(0f, 0.5f), MapPoint(1f, 0.5f)))
        val tracks = listOf(Track("t", "P", TrackTarget(TrackTarget.Kind.PROP, "p"), listOf(clip)))
        val layout = SequenceRenderer.prepare(m, tracks)
        val seq = Sequence("q", "Q", 4000, tracks)
        val early = SequenceRenderer.render(seq, layout, 800).count { it != 0 }
        val late = SequenceRenderer.render(seq, layout, 3800).count { it != 0 }
        assertTrue("early $early late $late", early in 1 until late)
    }

    @Test
    fun hueFollowsItsCurve() {
        val clip = Clip("h", 0, 1000, SeqEffect.ON, colors = listOf(0xFF0000), hueShift = 0f, hueCurve = ValueCurve(CurveShape.RAMP, end = 1f / 3f))
        val tracks = listOf(Track("t", "Sign", TrackTarget(TrackTarget.Kind.PROP, "s"), listOf(clip)))
        val seq = Sequence("q", "Q", 1000, tracks)
        val layout = SequenceRenderer.prepare(map, tracks)
        assertEquals(0xFF0000, SequenceRenderer.render(seq, layout, 0)[0])
        val end = SequenceRenderer.render(seq, layout, 999)[0]
        assertTrue((end shr 8 and 0xFF) > 200 && (end shr 16 and 0xFF) < 30)
    }

    @Test
    fun timingTracksAndStatesRoundTripThroughXlights() {
        val tt = TimingTrack("tt", "Sections").withMark(0, "Verse", 10_000).withMark(5000, "Chorus", 10_000)
        val clip = Clip("c", 0, 4000, SeqEffect.STATE, timingTrackId = "tt")
        val seq = Sequence("q", "Q", 10_000, listOf(Track("t", "Sign", TrackTarget(TrackTarget.Kind.PROP, "s"), listOf(clip))), timingTracks = listOf(tt))
        val back = XsqImport.parse(XsqExport.export(seq, map).byteInputStream())
        assertEquals(listOf("Sections"), back.timingTracks.map { it.first })
        val imported = XsqImport.toSequence(back, "Q", mapOf("Sign" to TrackTarget(TrackTarget.Kind.PROP, "s"))).first
        assertEquals(listOf("Verse", "Chorus"), imported.timingTracks.single().marks.map { it.label })
        assertEquals(imported.timingTracks.single().id, imported.tracks.single().clips.single().timingTrackId)
    }
}
