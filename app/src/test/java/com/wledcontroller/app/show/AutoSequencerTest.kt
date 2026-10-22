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

class AutoSequencerTest {
    private val line = listOf(MapPoint(0f, 0f), MapPoint(0.3f, 0f))
    private val box = listOf(MapPoint(0.4f, 0.2f), MapPoint(0.6f, 0.6f))
    private val map = HouseMap(
        aspect = 1.5f,
        runs = listOf(
            LightRun("roof", "Roofline", "d", 0, 100, points = line),
            LightRun("a1", "Arch 1", "d", 100, 20, points = line, prop = PropType.ARCH),
            LightRun("a2", "Arch 2", "d", 120, 20, points = line, prop = PropType.ARCH),
            LightRun("a3", "Arch 3", "d", 140, 20, points = line, prop = PropType.ARCH),
            LightRun("tree", "Mega tree", "d", 160, 64, points = box, model = PropModel(ModelKind.MEGA_TREE, strings = 8, nodes = 8)),
            LightRun("mx", "Matrix", "d", 224, 64, points = box, model = PropModel(ModelKind.MATRIX, strings = 8, nodes = 8)),
            LightRun("face", "Face", "d", 300, 26, points = box, model = PropModel(ModelKind.SINGING_FACE, nodes = 2)),
            LightRun("spot", "Spot", "fx1", 0, 1, points = listOf(MapPoint(0.9f, 0.1f))),
            LightRun("unplaced", "Bush", "d", 400, 10),
        ),
    )
    private val song = Sequence("s", "Carol", 60_000)
    // Quiet for 20 s, medium for 20 s, loud for the last 20 s.
    private val loudness: (Long) -> Float = { ms -> when { ms < 20_000 -> 0.1f; ms < 40_000 -> 0.5f; else -> 0.9f } }
    private val beats = (0 until 60_000L step 500).toList()

    private fun build(live: Float = 0.6f) = AutoSequencer.build(song, map, loudness, beats, setOf("fx1"), AutoSequencer.Options(listOf(0xFF0000, 0x00FF00, 0xFFFFFF), live, seed = 1))

    @Test
    fun findsTheQuietLivelyAndBigParts() {
        val s = AutoSequencer.sections(loudness, beats, 60_000)
        assertEquals(listOf(AutoSequencer.Energy.CALM, AutoSequencer.Energy.LIVELY, AutoSequencer.Energy.BIG), s.map { it.energy })
        assertEquals(0L, s.first().startMs)
        assertEquals(60_000L, s.last().endMs)
        assertTrue(abs(s[1].startMs - 20_000) <= 2500 && abs(s[2].startMs - 40_000) <= 2500)
    }

    private fun abs(v: Long) = if (v < 0) -v else v

    @Test
    fun everyPlacedPropGetsEffectsThatSuitIt() {
        val show = build()
        val targets = show.tracks.map { it.target.refId }.toSet()
        for (id in listOf("roof", "a1", "a2", "a3", "tree", "mx", "face", "spot")) assertTrue("$id has a track", id in targets)
        assertTrue("unplaced props are left alone", "unplaced" !in targets)
        // The face sings the whole song.
        val face = show.tracks.first { it.target.refId == "face" }.clips.single()
        assertEquals(SeqEffect.SING, face.effect); assertEquals(60_000L, face.durationMs)
        // The matrix gets the spectrum when it's big; the spotlight sweeps.
        assertTrue(show.tracks.filter { it.target.refId == "mx" }.flatMap { it.clips }.any { it.effect == SeqEffect.MUSIC && it.startMs >= 38_000 })
        assertTrue(show.tracks.first { it.target.refId == "spot" }.clips.all { it.panCurve != null })
        // Beat marks and a Sections timing track come with it.
        assertEquals(beats, show.marks)
        assertEquals(listOf("Calm", "Lively", "Big"), show.timingTracks.single { it.name == "Sections" }.marks.map { it.label })
    }

    @Test
    fun archesTakeTurnsOnTheBeat() {
        val show = build(1f)
        fun flashes(id: String) = show.tracks.filter { it.target.refId == id && it.name.endsWith("beats") }.flatMap { it.clips }.map { it.startMs }.toSet()
        val a1 = flashes("a1"); val a2 = flashes("a2"); val a3 = flashes("a3")
        assertTrue(a1.isNotEmpty() && a2.isNotEmpty() && a3.isNotEmpty())
        assertTrue("no two arches flash together", a1.intersect(a2).isEmpty() && a2.intersect(a3).isEmpty())
        // No accents in the calm opening.
        assertTrue((a1 + a2 + a3).all { it >= 15_000 })
    }

    @Test
    fun theShowPlaysAndIsTheSameEachTime() {
        val show = build()
        val layout = SequenceRenderer.prepare(map, show.tracks)
        val lit = (0 until 60).sumOf { s -> SequenceRenderer.render(show, layout, s * 1000L).count { it != 0 } }
        assertTrue(lit > 1000)
        assertEquals(show, build())
        // Gentle: no beat accents at all.
        assertTrue(build(0f).tracks.none { it.name.endsWith("beats") })
    }
}
