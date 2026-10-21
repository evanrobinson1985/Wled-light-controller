package com.wledcontroller.app.sim

import com.wledcontroller.app.data.DmxFixture
import com.wledcontroller.app.data.DmxRegistry
import com.wledcontroller.app.data.OutputProtocol
import com.wledcontroller.app.data.PixelOutput
import com.wledcontroller.app.house.DdpSender
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PropGroup
import com.wledcontroller.app.show.Clip
import com.wledcontroller.app.show.CurveShape
import com.wledcontroller.app.show.SeqEffect
import com.wledcontroller.app.show.Sequence
import com.wledcontroller.app.show.SequenceRenderer
import com.wledcontroller.app.show.Track
import com.wledcontroller.app.show.TrackTarget
import com.wledcontroller.app.show.ValueCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole way from a show to a spotlight's beam in the simulator: the renderer works out the
 * spotlight's colour and aim, the player's frame goes out through the DDP sender as DMX, the
 * simulated DMX node takes it, and reading it back (as the simulator draws) gives the same look.
 */
class SpotlightPathTest {
    @Test
    fun aShowSweepsASimulatedSpotlight() {
        val node = PixelOutput(id = "node", name = "Node", host = "127.77.1.9", protocol = OutputProtocol.E131, ledCount = 0)
        val head = DmxFixture(
            id = "spot", name = "Spot", outputId = node.id, universe = 1, address = 12,
            channels = DmxFixture.PRESETS.first { it.first.startsWith("Moving head + shutter") }.second,
        )
        DmxRegistry.update(listOf(head), listOf(node))
        val map = HouseMap(
            runs = listOf(
                LightRun("run-spot", "Spot", head.id, 0, 1, ledPoints = listOf(MapPoint(0.3f, 0.3f))),
                LightRun("run-roof", "Roof", "roof", 0, 10, points = listOf(MapPoint(0f, 0.5f), MapPoint(1f, 0.5f))),
            ),
            groups = listOf(PropGroup("spots", "Spots", listOf("run-spot"))),
        )
        val show = Sequence(
            id = "s", name = "Show", durationMs = 10_000,
            tracks = listOf(
                Track("t1", "Spots", TrackTarget(TrackTarget.Kind.GROUP, "spots"), listOf(
                    Clip("c1", 0, 10_000, SeqEffect.ON, listOf(0xFFFF0000.toInt()), pan = 0.2f, panCurve = ValueCurve(CurveShape.RAMP, end = 0.8f), tilt = 0.7f),
                )),
                // A whole-house effect underneath, as in the demo.
                Track("t2", "House", TrackTarget(TrackTarget.Kind.HOUSE), listOf(
                    Clip("c2", 0, 10_000, SeqEffect.FIREWORKS, listOf(0xFFFFFFFF.toInt())),
                )),
            ),
        )
        val layout = SequenceRenderer.prepare(map, show.tracks)
        val spotIndex = layout.leds.indexOfFirst { it.deviceId == head.id }
        assertTrue("the spotlight is in the show's layout", spotIndex >= 0)

        for ((time, expectedPan) in listOf(1_000L to 0.26f, 5_000L to 0.5f, 9_000L to 0.74f)) {
            val colors = IntArray(layout.leds.size)
            val motion = FloatArray(layout.leds.size * 2)
            SequenceRenderer.render(show, layout, time, colors, null, motion)
            assertEquals(expectedPan, motion[spotIndex * 2], 0.01f)
            assertEquals(0.7f, motion[spotIndex * 2 + 1], 0.01f)

            // As the player sends it.
            DdpSender().use { it.send(DmxRegistry.address(head.id), DmxRegistry.frame(colors[spotIndex], motion[spotIndex * 2], motion[spotIndex * 2 + 1])) }
            val universe = SimHub.dmxUniverse(node.host, 1)
            assertNotNull("the simulated DMX node received it", universe)
            val look = head.read(universe!!)
            assertEquals(expectedPan, look.pan, 0.01f)
            assertEquals(0.7f, look.tilt, 0.01f)
            assertTrue("the beam is lit red: ${Integer.toHexString(look.rgb)}", ((look.rgb shr 16) and 0xFF) > 200)
        }
    }
}
