package com.wledcontroller.app.house

import com.wledcontroller.app.show.Clip
import com.wledcontroller.app.show.SeqEffect
import com.wledcontroller.app.show.Sequence
import com.wledcontroller.app.show.SequenceRenderer
import com.wledcontroller.app.show.Track
import com.wledcontroller.app.show.TrackTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropModelTest {

    private val a = MapPoint(0.2f, 0.2f)
    private val b = MapPoint(0.6f, 0.8f)

    private fun close(x: Float, y: Float, p: MapPoint?) {
        assertEquals(x, p!!.x, 1e-4f); assertEquals(y, p.y, 1e-4f)
    }

    @Test
    fun matrixFollowsItsWiring() {
        // 3 strings of 4, up and down, starting bottom left, zig-zag.
        val m = PropModel(ModelKind.MATRIX, strings = 3, nodes = 4, start = StartCorner.BOTTOM_LEFT, vertical = true, zigzag = true)
        val p = m.layout(a, b, aspect = 1f)
        assertEquals(12, p.size)
        close(0.2f, 0.8f, p[0])  // bottom left
        close(0.2f, 0.2f, p[3])  // up the first string
        close(0.4f, 0.2f, p[4])  // second string starts at the top (zig-zag)
        close(0.4f, 0.8f, p[7])
        close(0.6f, 0.8f, p[8])  // third string back at the bottom
        assertEquals(3, m.submodels().size)
        assertEquals(4..7, m.submodels()[1].ranges.single())
    }

    @Test
    fun treeStringsFanFromTheTop() {
        val m = PropModel(ModelKind.MEGA_TREE, strings = 3, nodes = 5, degrees = 180, zigzag = false)
        val p = m.layout(MapPoint(0f, 0f), MapPoint(1f, 1f), 1f)
        close(0.5f, 0f, p[0])     // every string starts at the top centre
        val side = 1f - PropModel.TREE_TILT / 2f  // the base ring is seen a little from above
        close(0f, side, p[4])     // first string ends bottom left
        close(0.5f, 1f, p[9])     // middle string straight down, at the front
        close(1f, side, p[14])    // last string bottom right
    }

    @Test
    fun fullTreeSpacesItsStringsAllTheWayRound() {
        val m = PropModel(ModelKind.MEGA_TREE, strings = 4, nodes = 2, degrees = 360, fromBottom = true, zigzag = false)
        val p = m.layout(MapPoint(0f, 0f), MapPoint(1f, 1f), 1f)
        close(0f, 1f - PropModel.TREE_TILT / 2f, p[0])  // string 1 starts at the base, left edge
        close(0.5f, 0f, p[1])                            // and runs up to the top
        close(0.5f, 1f, p[2])                            // string 2 at the front
        close(1f, 1f - PropModel.TREE_TILT / 2f, p[4])  // string 3 on the right
        close(0.5f, 1f - PropModel.TREE_TILT, p[6])     // string 4 at the back, not on top of string 1
    }

    @Test
    fun iciclesHangFromTheTracedLine() {
        // 10 LEDs in drops of 2 and 3: 2, 3, 2, 3, every other drop wired back up.
        val m = PropModel(ModelKind.ICICLES, strings = 1, nodes = 10, dropPattern = listOf(2, 3), zigzag = true)
        assertEquals(listOf(2, 3, 2, 3), m.drops())
        assertEquals(listOf("Drop 1", "Drop 2", "Drop 3", "Drop 4"), m.submodels().map { it.name })
        assertEquals(2..4, m.submodels()[1].ranges.single())
        // A line 0.8 long: drops every 0.2, LEDs 0.1 apart hanging below it.
        val p = m.hang(listOf(MapPoint(0.1f, 0.5f), MapPoint(0.9f, 0.5f)), aspect = 1f, ledGap = 0.1f)
        assertEquals(10, p.size)
        close(0.2f, 0.55f, p[0])  // first drop, top LED just under the line
        close(0.2f, 0.65f, p[1])
        close(0.4f, 0.75f, p[2])  // second drop starts at its bottom (zig-zag)
        close(0.4f, 0.55f, p[4])
        close(0.6f, 0.55f, p[5])  // third drop from the top again
        close(0.8f, 0.55f, p[9])  // the last, wired back up, ends at the top
    }

    @Test
    fun iciclesAreTracedNotBoxed() {
        val m = PropModel(ModelKind.ICICLES, strings = 1, nodes = 4, dropPattern = listOf(2))
        val run = LightRun("i", "Icicles", "d", 0, 4, points = listOf(MapPoint(0f, 0.2f), MapPoint(0.5f, 0.2f), MapPoint(1f, 0.4f)), model = m)
        assertTrue(!run.isBoxModel)
        // Without a map scale, LEDs down a drop are as far apart as the drops.
        val pos = run.ledPositions(aspect = 1f)
        assertEquals(4, pos.count { it != null })
        assertTrue(pos[0]!!.y > 0.2f)
    }

    @Test
    fun starPointsHoldTheirOwnLeds() {
        val m = PropModel(ModelKind.STAR, points = 5, nodes = 10)
        val p = m.layout(MapPoint(0f, 0f), MapPoint(1f, 1f), 1f)
        assertEquals(50, p.size)
        // The middle of point 1 is its tip, at the top.
        val tip = p[4]!!.let { a -> p[5]!!.let { b -> MapPoint((a.x + b.x) / 2, (a.y + b.y) / 2) } }
        assertEquals(0.5f, tip.x, 0.03f)
        assertTrue(tip.y < 0.08f)
        assertEquals(listOf("Point 1", "Point 2", "Point 3", "Point 4", "Point 5"), m.submodels().map { it.name })
    }

    @Test
    fun runsUseTheirModelOnceItIsPlaced() {
        val model = PropModel(ModelKind.MATRIX, strings = 2, nodes = 2)
        val unplaced = LightRun("m", "Matrix", "d", 0, 4, model = model)
        unplaced.ledPositions(1f).forEach { assertNull(it) }
        val placed = unplaced.copy(points = listOf(a, b))
        assertEquals(4, HouseMap(aspect = 1f, runs = listOf(placed)).placedLeds().size)
    }

    @Test
    fun ledRangesParse() {
        assertEquals(listOf(0..9, 19..29), parseLedRanges("1-10, 20-30", 50))
        assertEquals(listOf(4..4), parseLedRanges("5", 50))
        assertNull(parseLedRanges("40-60", 50))
        assertNull(parseLedRanges("x", 50))
        assertEquals("1-10, 20-30", formatLedRanges(listOf(0..9, 19..29)))
    }

    @Test
    fun tracksCanTargetASubmodel() {
        val star = LightRun("s", "Star", "d", 0, 15, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 1f)),
            model = PropModel(ModelKind.STAR, points = 3, nodes = 5))
        val roof = LightRun("r", "Roof", "d", 15, 10, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f)),
            submodels = listOf(Submodel("Left half", listOf(0..4))))
        val map = HouseMap(aspect = 1f, runs = listOf(star, roof))
        val on = Clip("c", 0, 1000, SeqEffect.ON, colors = listOf(0xFF0000))
        val tracks = listOf(
            Track("a", "Point 2", TrackTarget.submodel("s", "Point 2"), listOf(on)),
            Track("b", "Left", TrackTarget.submodel("r", "Left half"), listOf(on)),
        )
        val out = SequenceRenderer.render(Sequence("q", "Q", 1000, tracks), SequenceRenderer.prepare(map, tracks), 500)
        val lit = out.withIndex().filter { it.value != 0 }.map { it.index }
        assertEquals((5..9).toList() + (15..19).toList(), lit)
    }
}
