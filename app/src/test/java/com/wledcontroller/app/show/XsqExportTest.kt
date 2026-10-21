package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PropGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XsqExportTest {
    @Test
    fun aShowGoesToXlightsAndComesBack() {
        val map = HouseMap(
            runs = listOf(LightRun("roof", "Roofline", "d", 0, 50, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f)))),
            groups = listOf(PropGroup("g", "Arches & canes", listOf("roof"))),
        )
        val bars = Clip("b", 1000, 2000, SeqEffect.BARS, listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()), fadeInMs = 500, inTransition = Transition.WIPE)
        val life = Clip("l", 4000, 1000, SeqEffect.LIFE, listOf(0xFF00FF00.toInt()))
        val words = Clip("t", 0, 3000, SeqEffect.TEXT, listOf(0xFFFFFFFF.toInt()), text = "Merry Christmas")
        val seq = Sequence(
            "s", "Carol", 12_000,
            listOf(
                Track("t1", "Roof top", TrackTarget(TrackTarget.Kind.PROP, "roof"), listOf(words)),
                Track("t2", "Roof", TrackTarget(TrackTarget.Kind.PROP, "roof"), listOf(bars, life)),
                Track("t3", "Arches", TrackTarget(TrackTarget.Kind.GROUP, "g"), listOf(Clip("o", 0, 1000))),
            ),
            marks = listOf(0, 500, 1000),
            audioTitle = "Carol of the Bells.mp3",
            lyrics = LyricSync.breakdown("Hark how the bells", 0, 4000),
        )
        val xml = XsqExport.export(seq, map)
        val back = XsqImport.parse(xml.byteInputStream())
        assertEquals(12_000L, back.durationMs)
        assertEquals("Carol of the Bells.mp3", back.mediaFile)
        assertEquals(listOf(0L, 500L, 1000L), back.marks)
        assertNotNull(back.lyrics)
        assertEquals(listOf("Hark", "how", "the", "bells"), back.lyrics!!.words.map { it.label })
        // The roof's two tracks are two layers of one model; the group is a model of its own.
        assertEquals(listOf("Roofline", "Arches & canes"), back.elements.map { it.name })
        val roof = back.elements.first()
        assertEquals(2, roof.layers.size)
        val (imported, skipped) = XsqImport.toSequence(back, "Carol", mapOf("Roofline" to TrackTarget(TrackTarget.Kind.PROP, "roof")))
        assertEquals(0, skipped)
        val clips = imported.tracks.flatMap { it.clips }
        val text = clips.first { it.effect == SeqEffect.TEXT }
        assertEquals("Merry Christmas", text.text)
        val b = clips.first { it.effect == SeqEffect.BARS }
        assertEquals(500L, b.fadeInMs)
        assertEquals(Transition.WIPE, b.inTransition)
        assertEquals(listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()), b.colors)
        assertTrue(clips.any { it.effect == SeqEffect.LIFE && it.startMs == 4000L })
    }

    @Test
    fun everyEffectHasAnXlightsName() {
        SeqEffect.entries.forEach { e ->
            val name = XsqExport.xlightsName(e)
            assertNotNull("$e comes back", XsqImport.effectFor(name))
        }
    }
}
