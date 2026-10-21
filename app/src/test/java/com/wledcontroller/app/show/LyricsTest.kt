package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropGroup
import com.wledcontroller.app.house.PropModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {

    @Test
    fun syllablesAndMouthShapes() {
        assertEquals(1, LyricSync.syllables("night"))
        assertEquals(2, LyricSync.syllables("silent"))
        assertEquals(2, LyricSync.syllables("holy"))
    }

    @Test
    fun wordsBecomeMouthShapes() {
        assertEquals(listOf(Phoneme.MBP, Phoneme.O), LyricSync.phonemes("mo").map { it.first })
        // "moon": closed, then the "oo" round mouth, then a consonant.
        assertEquals(listOf(Phoneme.MBP, Phoneme.U, Phoneme.ETC), LyricSync.phonemes("moon").map { it.first })
        assertEquals(Phoneme.FV, LyricSync.phonemes("phone").first().first)
        assertEquals(Phoneme.WQ, LyricSync.phonemes("what").first().first)
    }

    @Test
    fun linesSpreadOverTheSongInOrder() {
        val l = LyricSync.breakdown("Silent night\n\nHoly night\nAll is calm", 0, 12_000)
        assertEquals(listOf("Silent night", "Holy night", "All is calm"), l.phrases.map { it.label })
        assertEquals(listOf("Silent", "night", "Holy", "night", "All", "is", "calm"), l.words.map { it.label })
        // Everything in time order, inside the song, words inside their lines.
        assertTrue(l.words.zipWithNext().all { (a, b) -> a.endMs <= b.startMs })
        assertTrue(l.phonemes.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
        assertTrue(l.phrases.last().endMs <= 12_000)
        // A mouth shape during the first word, nothing before the song's first line.
        val first = l.words.first()
        assertNotNull(l.phonemeAt((first.startMs + first.endMs) / 2))
        assertEquals(Phoneme.ETC, Phoneme.parse(l.phonemes.first().label)) // "s"
    }

    @Test
    fun tappedLinesStartWhereTapped() {
        val l = LyricSync.breakdown("Jingle bells\nJingle all the way", 0, 60_000, lineStarts = listOf(5_000, 20_000))
        assertEquals(5_000, l.phrases[0].startMs)
        assertEquals(20_000, l.phrases[1].startMs)
        // Not stretched over the long gap between the taps.
        assertTrue(l.phrases[0].endMs < 9_000)
        assertNull(l.phonemeAt(15_000))
    }

    @Test
    fun facesSingTheLyrics() {
        val face = LightRun("f", "Face", "d", 0, 8, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
            model = PropModel(ModelKind.FACE, nodes = 2))
        val map = HouseMap(aspect = 1f, runs = listOf(face))
        val clip = Clip("s", 0, 10_000, SeqEffect.SING, colors = listOf(0x00FF00, 0xFF0000))
        val tracks = listOf(Track("t", "Face", TrackTarget(TrackTarget.Kind.PROP, "f"), listOf(clip)))
        // "Ah" sung from 1 s to 2 s: a wide mouth; before it, closed.
        val lyrics = Lyrics(listOf(TimedLabel(1000, 2000, "Ah")), listOf(TimedLabel(1000, 2000, "Ah")), listOf(TimedLabel(1000, 2000, "AI")))
        val seq = Sequence("q", "Q", 10_000, tracks, lyrics = lyrics)
        val layout = SequenceRenderer.prepare(map, tracks)
        assertEquals(listOf(0x00FF00, 0x00FF00, 0, 0, 0, 0, 0xFF0000, 0xFF0000), SequenceRenderer.render(seq, layout, 1500).toList())
        assertEquals(listOf(0x00FF00, 0x00FF00, 0xFF0000, 0xFF0000, 0, 0, 0, 0), SequenceRenderer.render(seq, layout, 500).toList())
    }
}

class RenderStyleTest {
    // Two short props side by side in a group.
    private val a = LightRun("a", "Arch 1", "d", 0, 10, points = listOf(MapPoint(0.1f, 0.5f), MapPoint(0.3f, 0.5f)))
    private val b = LightRun("b", "Arch 2", "d", 10, 10, points = listOf(MapPoint(0.6f, 0.5f), MapPoint(0.8f, 0.5f)))
    private val map = HouseMap(aspect = 1f, runs = listOf(a, b), groups = listOf(PropGroup("g", "Arches", listOf("a", "b"))))

    private fun frame(style: RenderStyle, clip: Clip, ms: Long): List<Int> {
        val tracks = listOf(Track("t", "Arches", TrackTarget(TrackTarget.Kind.GROUP, "g"), listOf(clip), style = style))
        return SequenceRenderer.render(Sequence("s", "S", 10_000, tracks), SequenceRenderer.prepare(map, tracks), ms).toList()
    }

    @Test
    fun eachPropGetsTheWholeEffect() {
        // A left-to-right wipe half way: as one, only the left arch is lit; per prop, half of each.
        val wipe = Clip("w", 0, 2000, SeqEffect.WIPE, colors = listOf(0xFF0000), direction = com.wledcontroller.app.house.Direction.LEFT_RIGHT)
        val asOne = frame(RenderStyle.AS_ONE, wipe, 1000)
        assertTrue(asOne.take(10).count { it != 0 } >= 9 && asOne.drop(10).count { it != 0 } <= 1)
        val perProp = frame(RenderStyle.PER_PROP, wipe, 1000)
        assertTrue(perProp.take(10).count { it != 0 } in 4..6)
        assertEquals(perProp.take(10), perProp.drop(10))
        // One line: along all 20 in order, so half way lights the first prop.
        val line = frame(RenderStyle.SINGLE_LINE, wipe.copy(direction = com.wledcontroller.app.house.Direction.ALONG), 1000)
        assertTrue(line.take(10).all { it != 0 } && line.drop(11).all { it == 0 })
    }

    @Test
    fun coloursChangeOverTheEffectAndBlurSoftens() {
        val on = Clip("c", 0, 1000, SeqEffect.ON, colors = listOf(0xFF0000), colorsEnd = listOf(0x0000FF))
        assertEquals(0xFF0000, frame(RenderStyle.AS_ONE, on, 0)[0])
        val mid = frame(RenderStyle.AS_ONE, on, 500)[0]
        assertTrue((mid shr 16 and 0xFF) in 100..160 && (mid and 0xFF) in 100..160)
        // A single lit LED in a blurred chase spreads to its neighbours.
        assertEquals(listOf(0, 0x101010, 0x101010, 0x101010, 0), SequenceRenderer.blur(intArrayOf(0, 0, 0x303030, 0, 0), 1).toList())
    }
}

class XsqLyricsTest {
    @Test
    fun xlightsLyricTracksComeIn() {
        val xsq = """<?xml version="1.0" encoding="UTF-8"?>
<xsequence><head><sequenceDuration>10.0</sequenceDuration></head><ElementEffects>
  <Element type="timing" name="Lyrics">
    <EffectLayer><Effect label="Silent night" startTime="1000" endTime="3000"/></EffectLayer>
    <EffectLayer><Effect label="Silent" startTime="1000" endTime="2000"/><Effect label="night" startTime="2000" endTime="3000"/></EffectLayer>
    <EffectLayer><Effect label="etc" startTime="1000" endTime="1200"/><Effect label="AI" startTime="1200" endTime="1600"/></EffectLayer>
  </Element>
</ElementEffects></xsequence>"""
        val x = XsqImport.parse(xsq.byteInputStream())
        val l = x.lyrics!!
        assertEquals(listOf("Silent night"), l.phrases.map { it.label })
        assertEquals(listOf("Silent", "night"), l.words.map { it.label })
        assertEquals(Phoneme.AI, l.phonemeAt(1300))
        assertTrue(x.marks.isEmpty())
        assertEquals(l, XsqImport.toSequence(x, "Song", emptyMap()).first.lyrics)
    }
}
