package com.wledcontroller.app.show

import com.wledcontroller.app.data.SavedDevice
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropGroup
import com.wledcontroller.app.house.PropModel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SharingTest {

    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        FseqReader.forgetAll()
        files.forEach { it.delete() }
    }

    private fun temp() = File.createTempFile("export", ".fseq").also { files += it }

    @Test
    fun writtenFseqReadsBack() {
        val f = temp()
        f.outputStream().use { out ->
            FseqWriter.write(out, channels = 9, frameCount = 300, stepMs = 25, mediaFile = "Song.mp3") { n, buf ->
                for (c in buf.indices) buf[c] = (n + c).toByte()
            }
        }
        val r = FseqReader.open(f.path)
        assertEquals(9, r.channelCount)
        assertEquals(300, r.frameCount)
        assertEquals(25, r.stepMs)
        assertEquals("zlib", r.compressionName)
        assertEquals("Song.mp3", r.mediaFile)
        for (n in listOf(0, 1, 9, 10, 150, 299)) {
            assertArrayEquals("frame $n", ByteArray(9) { (n + it).toByte() }, r.frame(n))
        }
    }

    @Test
    fun showExportsWithEachControllerInTurn() {
        val map = HouseMap(
            aspect = 1f,
            runs = listOf(
                LightRun("roof", "Roof", "a", 0, 4, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f))),
                LightRun("bush", "Bush", "b", 0, 2, points = listOf(MapPoint(0f, 1f), MapPoint(1f, 1f))),
            ),
        )
        val tracks = listOf(Track("t", "Bush", TrackTarget(TrackTarget.Kind.PROP, "bush"), listOf(Clip("c", 0, 1000, SeqEffect.ON, colors = listOf(0x00FF00)))))
        val seq = Sequence("s", "Show", 1000, tracks)
        val layout = SequenceRenderer.prepare(map, tracks)
        val devices = listOf(SavedDevice("a", "Roof controller", "h1"), SavedDevice("b", "Bush controller", "h2"))
        val f = temp()
        val ranges = f.outputStream().use { FseqExport.export(seq, layout, devices, null, it) }
        assertEquals(listOf(1 to 12, 13 to 18), ranges.map { it.startChannel to it.endChannel })
        val r = FseqReader.open(f.path)
        assertEquals(18, r.channelCount)
        assertEquals(40, r.frameCount)
        val frame = r.frame(10)
        assertEquals(0, r.channel(frame, 0))       // roof dark
        assertEquals(255, r.channel(frame, 13))    // bush LED 0 green (channel 14, 1-based)
        assertEquals(255, r.channel(frame, 16))    // bush LED 1 green
    }

    private val xsq = """<?xml version="1.0" encoding="UTF-8"?>
<xsequence BaseChannel="0" ChanCtrlBasic="0" ChanCtrlColor="0" FixedPointTiming="1">
  <head><version>2023.20</version><mediaFile>C:\Shows\Carol of the Bells.mp3</mediaFile><sequenceDuration>12.500</sequenceDuration></head>
  <ColorPalettes>
    <ColorPalette>C_BUTTON_Palette1=#FF0000,C_BUTTON_Palette2=#00FF00,C_BUTTON_Palette3=#0000FF,C_CHECKBOX_Palette1=1,C_CHECKBOX_Palette2=0,C_CHECKBOX_Palette3=1,C_SLIDER_Brightness=50</ColorPalette>
  </ColorPalettes>
  <EffectDB>
    <Effect>E_SLIDER_Bars_BarCount=2,T_TEXTCTRL_Fadein=0.50,T_CHOICE_In_Transition_Type=Wipe</Effect>
    <Effect>E_TEXTCTRL_Text=Merry Christmas, everyone,E_CHOICE_Text_Dir=left</Effect>
  </EffectDB>
  <DisplayElements><Element type="timing" name="Beats"/><Element type="model" name="Roofline"/></DisplayElements>
  <ElementEffects>
    <Element type="timing" name="Beats"><EffectLayer><Effect label="" startTime="0" endTime="500"/><Effect label="" startTime="500" endTime="1000"/></EffectLayer></Element>
    <Element type="model" name="Roofline">
      <EffectLayer><Effect ref="1" name="Text" startTime="2000" endTime="6000" palette="0"/></EffectLayer>
      <EffectLayer><Effect ref="0" name="Bars" startTime="0" endTime="4000" palette="0"/><Effect name="Faces" startTime="4000" endTime="5000" palette="0"/></EffectLayer>
    </Element>
    <Element type="model" name="Mega Tree"><EffectLayer/><SubModelEffectLayer name="String 1"><Effect name="Twinkle" startTime="0" endTime="3000" palette="0"/></SubModelEffectLayer></Element>
    <Element type="model" name="Arches"><EffectLayer><Effect name="On" startTime="0" endTime="1000" palette="0"/></EffectLayer></Element>
  </ElementEffects>
</xsequence>"""

    @Test
    fun xsqImportsLayersEffectsAndTiming() {
        val x = XsqImport.parse(xsq.byteInputStream())
        assertEquals(12_500L, x.durationMs)
        assertEquals("Carol of the Bells.mp3", x.mediaFile)
        assertEquals(listOf(0L, 500L), x.marks)
        assertEquals(listOf("Roofline", "Mega Tree / String 1", "Arches"), x.elements.map { it.label })

        val tree = LightRun("tree", "Mega tree", "d", 0, 32, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
            model = PropModel(ModelKind.MEGA_TREE, strings = 4, nodes = 8))
        val map = HouseMap(
            runs = listOf(LightRun("roof", "roofline", "d", 0, 10), tree),
            groups = listOf(PropGroup("g", "Arches", emptyList())),
        )
        val targets = x.elements.associate { it.label to com.wledcontroller.app.ui.screens.autoTarget(it, map) }
        assertEquals(TrackTarget(TrackTarget.Kind.PROP, "roof"), targets["Roofline"])
        assertEquals(TrackTarget.submodel("tree", "String 1"), targets["Mega Tree / String 1"])
        assertEquals(TrackTarget(TrackTarget.Kind.GROUP, "g"), targets["Arches"])

        val (seq, skipped) = XsqImport.toSequence(x, "Carol", targets)
        assertEquals(1, skipped) // Faces has no match yet
        assertEquals(listOf("Roofline · layer 1", "Roofline · layer 2", "Mega Tree / String 1", "Arches"), seq.tracks.map { it.name })
        assertEquals(BlendMode.NORMAL, seq.tracks[0].blend)
        val text = seq.tracks[0].clips.single()
        assertEquals(SeqEffect.TEXT, text.effect)
        assertEquals("Merry Christmas, everyone", text.text)
        val bars = seq.tracks[1].clips.single()
        assertEquals(SeqEffect.BARS, bars.effect)
        assertEquals(500L, bars.fadeInMs)
        assertEquals(Transition.WIPE, bars.inTransition)
        assertEquals(listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()), bars.colors)
        assertEquals(0.5f, bars.intensity, 1e-4f)
        assertEquals(listOf(0L, 500L), seq.marks)

        // Skipping a model leaves it out.
        assertNull(XsqImport.toSequence(x, "C", targets - "Arches").first.tracks.firstOrNull { it.name == "Arches" })
    }
}
