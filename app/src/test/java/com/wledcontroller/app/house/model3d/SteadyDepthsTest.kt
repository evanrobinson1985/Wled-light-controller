package com.wledcontroller.app.house.model3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SteadyDepthsTest {
    @Test fun strayHitsGoBackOnTheString() {
        // A string 5 m away; some rays slip through to the wall 2 m behind, one catches a bit in front.
        val raw = List<Float?>(40) { 5f }.toMutableList()
        listOf(6, 7, 15, 22, 23, 24, 31).forEach { raw[it] = 7f }
        raw[12] = 3.5f
        raw[18] = null
        val out = Led3D.steadyDepths(raw)
        out.forEach { assertNotNull(it); assertEquals(5f, it!!, 0.01f) }
    }

    @Test fun aStringThatRecedesKeepsItsSlope() {
        val raw = List<Float?>(30) { 4f + it * 0.1f }
        val out = Led3D.steadyDepths(raw)
        raw.indices.forEach { assertEquals(raw[it]!!, out[it]!!, 0.001f) }
    }

    @Test fun nothingTracedYetHasNoLights() {
        val run = com.wledcontroller.app.house.LightRun("r", "Roofline", "d", 0, 50)
        assertEquals(0, Led3D.along(emptyList(), run).size)
        assertEquals(50, Led3D.along(listOf(P3(0f, 3f, 0f)), run).size)
    }

    @Test fun allMissedStaysMissed() {
        assertEquals(listOf<Float?>(null, null), Led3D.steadyDepths(listOf(null, null)))
    }
}
