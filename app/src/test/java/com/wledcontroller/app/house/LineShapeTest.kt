package com.wledcontroller.app.house

import com.wledcontroller.app.house.model3d.Led3D
import com.wledcontroller.app.house.model3d.P3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class LineShapeTest {
    private val aspect = 2f

    @Test
    fun straightensACrookedlyMarkedStrip() {
        val crooked = listOf(MapPoint(0.1f, 0.50f), MapPoint(0.3f, 0.52f), MapPoint(0.5f, 0.49f), MapPoint(0.7f, 0.51f), MapPoint(0.9f, 0.50f))
        val s = LineTools.straighten(crooked, aspect)
        // On one line (equal slope everywhere), in the same order, still spanning the same length.
        val slope = (s.last().y - s.first().y) / (s.last().x - s.first().x)
        s.zipWithNext { a, b -> assertEquals(slope, (b.y - a.y) / (b.x - a.x), 1e-3f); assertTrue(b.x > a.x) }
        assertEquals(0.1f, s.first().x, 0.01f); assertEquals(0.9f, s.last().x, 0.01f)
        assertEquals(0.504f, s.map { it.y }.average().toFloat(), 0.002f)
        // A vertical one stays vertical.
        val up = LineTools.straighten(listOf(MapPoint(0.5f, 0.1f), MapPoint(0.51f, 0.5f), MapPoint(0.5f, 0.9f)), aspect)
        assertTrue(up.all { abs(it.x - up[0].x) < 1e-3f })
    }

    @Test
    fun levelsOnePieceAndKeepsHiddenStretchesInStep() {
        val pts = listOf(MapPoint(0.1f, 0.5f), MapPoint(0.5f, 0.53f), MapPoint(0.52f, 0.9f))
        val lv = LineTools.level(pts, 0, aspect)
        assertEquals(lv[0].y, lv[1].y, 1e-6f); assertEquals(0.515f, lv[0].y, 1e-6f)
        val plumb = LineTools.level(pts, 1, aspect)
        assertEquals(plumb[1].x, plumb[2].x, 1e-6f)
        val hidden = listOf(HiddenStretch(0), HiddenStretch(2), HiddenStretch(3))
        assertEquals(listOf(0, 3, 4), LineTools.hiddenAfterInserting(hidden, 1).map { it.after })
        assertEquals(listOf(0, 2), LineTools.hiddenAfterRemoving(hidden, 2).map { it.after })
    }

    @Test
    fun aHangingStrandDroopsBetweenItsHooks() {
        // Two hooks 1 picture-height apart, 9 bulbs, sagging 10% of the span.
        val run = LightRun("r", "Bulbs", "d", 0, 9, points = listOf(MapPoint(0.25f, 0.3f), MapPoint(0.75f, 0.3f)), sag = 0.1f)
        val pos = run.ledPositions(aspect)
        assertEquals(9, pos.size)
        // Ends at the hooks, lowest in the middle by 0.1 of the span.
        assertEquals(0.3f, pos.first()!!.y, 1e-4f); assertEquals(0.3f, pos.last()!!.y, 1e-4f)
        assertEquals(0.4f, pos[4]!!.y, 0.005f)
        assertTrue(pos.zipWithNext().take(4).all { (a, b) -> b!!.y > a!!.y })
        // Evenly spaced along the curve, not straight across.
        val gaps = pos.zipWithNext { a, b -> hypot((b!!.x - a!!.x) * aspect, b.y - a.y) }
        assertTrue(gaps.max() - gaps.min() < gaps.average() * 0.08)
        // Extra hooks: three spans, each with its own dip, back up at the hooks.
        val hooked = run.copy(count = 13, hooksBetween = 2)
        assertEquals(4, hooked.hooks().size)
        val hp = hooked.ledPositions(aspect)
        assertEquals(0.3f, hp[4]!!.y, 0.01f); assertEquals(0.3f, hp[8]!!.y, 0.01f)
        assertTrue(hp[2]!!.y > 0.3f + 0.025f)
        // With a real spacing, the strand's extra length uses up more bulbs.
        val spaced = run.copy(count = 40, spacingCm = 30f, sag = 0.2f)
        val straight = spaced.copy(sag = 0f)
        val cmPerHeight = 600f
        val endSag = spaced.ledPositions(aspect, cmPerHeight).indexOfLast { it!!.x <= 0.75f + 1e-4f }
        val endStraight = straight.ledPositions(aspect, cmPerHeight).indexOfLast { it!!.x <= 0.75f + 1e-4f }
        assertTrue("$endSag vs $endStraight", endSag > endStraight)
    }

    @Test
    fun hiddenStretchesStayStraightAndTheRestSags() {
        val run = LightRun(
            "r", "Bulbs", "d", 0, 20,
            points = listOf(MapPoint(0.1f, 0.3f), MapPoint(0.4f, 0.3f), MapPoint(0.6f, 0.3f), MapPoint(0.9f, 0.3f)),
            hidden = listOf(HiddenStretch(1, leds = 4)), sag = 0.1f,
        )
        val flags = run.hiddenFlags(aspect)
        assertEquals(4, flags.count { it })
        val pos = run.ledPositions(aspect)
        pos.withIndex().filter { flags[it.index] }.forEach { assertEquals(0.3f, it.value!!.y, 1e-4f) }
        assertTrue(pos.withIndex().filter { !flags[it.index] }.any { it.value!!.y > 0.33f })
    }

    @Test
    fun hangsOnTheModelToo() {
        val run = LightRun("r", "Bulbs", "d", 0, 11, sag = 0.1f)
        val p = Led3D.along(listOf(P3(0f, 3f, 0f), P3(4f, 3f, 0f)), run)
        assertEquals(3f, p.first().y, 1e-4f); assertEquals(3f, p.last().y, 1e-4f)
        assertEquals(2.6f, p[5].y, 0.02f)
    }
}
