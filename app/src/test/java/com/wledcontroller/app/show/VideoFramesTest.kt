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
import java.io.File

class VideoFramesTest {
    /** A 2 × 1 video at 2 fps: red then blue frames. */
    private fun video(): File {
        val f = File.createTempFile("video", ".vid")
        f.outputStream().use { out ->
            VideoFrames.writeHeader(out, 2, 1, 2f, 2)
            out.write(byteArrayOf(-1, 0, 0, -1, 0, 0)) // red, red
            out.write(byteArrayOf(0, 0, -1, 0, 0, -1)) // blue, blue
        }
        return f
    }

    @Test
    fun framesReadBackAndLoop() {
        val v = VideoFrames.read(video())!!
        assertEquals(2, v.count)
        assertEquals(0xFFFF0000.toInt(), v.at(0.1f).argb[0])
        assertEquals(0xFF0000FF.toInt(), v.at(0.6f).argb[1])
        assertEquals(0xFFFF0000.toInt(), v.at(1.1f).argb[0]) // looped
        assertEquals(96 to 54, VideoFrames.fit(1920, 1080))
        assertEquals(40 to 30, VideoFrames.fit(40, 30))
    }

    @Test
    fun videoEffectPlaysOnAPanel() {
        val panel = LightRun("p", "Panel", "d", 0, 16, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
            prop = PropType.MATRIX, model = PropModel(ModelKind.MATRIX, strings = 4, nodes = 4))
        val map = HouseMap(aspect = 1f, runs = listOf(panel))
        val clip = Clip("v", 0, 4000, SeqEffect.VIDEO, videoFile = video().absolutePath, speed = 0.5f)
        val tracks = listOf(Track("t", "P", TrackTarget(TrackTarget.Kind.PROP, "p"), listOf(clip)))
        val layout = SequenceRenderer.prepare(map, tracks)
        val seq = Sequence("s", "S", 4000, tracks)
        assertTrue(SequenceRenderer.render(seq, layout, 100).any { it == 0xFF0000 })
        assertTrue(SequenceRenderer.render(seq, layout, 700).any { it == 0x0000FF })
    }
}
