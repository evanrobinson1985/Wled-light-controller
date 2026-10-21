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
import java.io.ByteArrayOutputStream
import java.io.File

class NicheEffectsTest {
    private val panel = LightRun("p", "Panel", "d", 0, 12 * 12, points = listOf(MapPoint(0f, 0f), MapPoint(0.5f, 1f)),
        prop = PropType.MATRIX, model = PropModel(ModelKind.MATRIX, strings = 12, nodes = 12))
    private val roof = LightRun("r", "Roof", "d", 200, 30, points = listOf(MapPoint(0.6f, 0f), MapPoint(1f, 0f)))
    private val map = HouseMap(aspect = 1f, runs = listOf(panel, roof))

    private fun render(tracks: List<Track>, ms: Long, audio: AudioEnvelope? = null, motion: FloatArray? = null): IntArray {
        val layout = SequenceRenderer.prepare(map, tracks)
        return SequenceRenderer.render(Sequence("s", "S", 10_000, tracks), layout, ms, audio = audio, motion = motion)
    }

    @Test
    fun duplicatePlaysTheOtherTracksEffect() {
        val red = Track("a", "Roof", TrackTarget(TrackTarget.Kind.PROP, "r"), listOf(Clip("c1", 0, 5000, SeqEffect.ON, colors = listOf(0xFF0000))))
        val copy = Track("b", "Panel", TrackTarget(TrackTarget.Kind.PROP, "p"), listOf(Clip("c2", 0, 5000, SeqEffect.DUPLICATE, duplicateTrackId = "a")))
        val out = render(listOf(red, copy), 1000)
        assertTrue((0 until 144).all { out[it] == 0xFF0000 })
        // Nothing to copy after the source's effect ends.
        val late = render(listOf(red.copy(clips = listOf(Clip("c1", 0, 500, SeqEffect.ON, colors = listOf(0xFF0000)))), copy), 1000)
        assertTrue((0 until 144).all { late[it] == 0 })
    }

    @Test
    fun guitarStringsShakeWithTheMusic() {
        val t = Track("g", "Panel", TrackTarget(TrackTarget.Kind.PROP, "p"), listOf(Clip("c", 0, 5000, SeqEffect.GUITAR, colors = listOf(0xFF0000, 0x00FF00))))
        val loud = AudioEnvelope(FloatArray(600) { 0.9f }, Array(BandAnalyzer.BANDS) { FloatArray(600) { 0.9f } })
        val quiet = AudioEnvelope(FloatArray(600) { 0f }, Array(BandAnalyzer.BANDS) { FloatArray(600) { 0f } })
        val a = render(listOf(t), 1000, loud).toList(); val b = render(listOf(t), 1100, loud).toList()
        assertTrue(a.any { it != 0 } && a != b)
        assertTrue(render(listOf(t), 1000, quiet).sum() < render(listOf(t), 1000, loud).sum())
    }

    @Test
    fun servoSetsOneLevelAndTurnsHeads() {
        val t = Track("s", "Roof", TrackTarget(TrackTarget.Kind.PROP, "r"), listOf(Clip("c", 0, 1000, SeqEffect.SERVO, colors = listOf(0xFFFFFF), size = 0f, sizeCurve = ValueCurve(CurveShape.RAMP, end = 1f))))
        val motion = FloatArray((144 + 30) * 2)
        val start = render(listOf(t), 0, motion = motion)
        val layout = SequenceRenderer.prepare(map, listOf(t))
        val roofIdx = layout.tracks[0].ledIndex
        assertTrue(roofIdx.all { start[it] == 0 })
        val end = render(listOf(t), 990, motion = motion)
        assertTrue(roofIdx.all { (end[it] and 0xFF) > 240 })
        assertEquals(0.99f, motion[roofIdx[0] * 2], 0.02f)
    }

    @Test
    fun glediatorRecordingsPlay() {
        // A 2 × 1 recording: two frames, red then blue.
        val raw = byteArrayOf(-1, 0, 0, -1, 0, 0, 0, 0, -1, 0, 0, -1)
        val f = File.createTempFile("gled", ".vid")
        f.outputStream().use { assertEquals(2, VideoFrames.fromGlediator(raw, 2, 1, 2f, it)) }
        val t = Track("g", "Panel", TrackTarget(TrackTarget.Kind.PROP, "p"), listOf(Clip("c", 0, 5000, SeqEffect.GLEDIATOR, videoFile = f.absolutePath, speed = 0.5f)))
        assertTrue(render(listOf(t), 100).take(144).all { it == 0xFF0000 })
        assertTrue(render(listOf(t), 700).take(144).all { it == 0x0000FF })
        val bad = runCatching { VideoFrames.fromGlediator(ByteArray(5), 2, 1, 2f, ByteArrayOutputStream()) }
        assertTrue(bad.isFailure)
    }
}
