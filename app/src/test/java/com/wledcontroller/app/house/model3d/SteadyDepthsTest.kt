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

class BendTest {
    private val a = P3(0f, 3f, 0f)
    private val b = P3(4f, 3f, 0f)

    @Test fun aBentSpanSagsMostInTheMiddle() {
        val run = com.wledcontroller.app.house.LightRun("r", "Bullets", "d", 0, 9, bends3d = listOf(0.3f))
        val lights = Led3D.along(listOf(a, b), run)
        assertEquals(9, lights.size)
        assertEquals(3f, lights.first().y, 0.01f); assertEquals(3f, lights.last().y, 0.01f)
        val lowest = lights.minByOrNull { it.y }!!
        assertEquals(2.7f, lowest.y, 0.02f)
        assertEquals(2f, lowest.x, 0.3f)
    }

    @Test fun aNegativeBendArchesUp() {
        val pts = Led3D.bent(listOf(a, b), listOf(-0.5f))
        assertEquals(3.5f, pts.maxOf { it.y }, 0.01f)
    }

    @Test fun eachSpanBendsOnItsOwn() {
        val c = P3(8f, 3f, 0f)
        val pts = Led3D.bent(listOf(a, b, c), listOf(0.4f))
        assertEquals(2.6f, pts.filter { it.x <= 4f }.minOf { it.y }, 0.01f)
        pts.filter { it.x > 4f }.forEach { assertEquals(3f, it.y, 0.001f) }
    }

    @Test fun draggingTheHandleFollowsTheFinger() {
        // Looking at the span from the front, the finger aimed 0.5 m below its middle.
        val eye = P3(2f, 2f, 10f)
        val target = P3(2f, 2.5f, 0f)
        val bend = Led3D.bendToward(a, b, eye, target - eye)!!
        assertEquals(0.5f, bend, 0.01f)
        val h = Led3D.bendHandle(a, b, bend)
        assertEquals(2.5f, h.y, 0.01f)
        // Straight down onto it there's no telling how far: no answer.
        assertEquals(null, Led3D.bendToward(a, b, P3(2f, 10f, 0f), P3(0f, -1f, 0f)))
    }
}
