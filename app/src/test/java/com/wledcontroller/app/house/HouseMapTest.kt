package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HouseMapTest {

    private fun close(expected: MapPoint, actual: MapPoint?) {
        assertEquals(expected.x, actual!!.x, 1e-4f)
        assertEquals(expected.y, actual.y, 1e-4f)
    }

    @Test
    fun tracedLedsSpreadEvenlyByDistance() {
        // An L: 0.5 across (×2 aspect = 1.0 long), then 1.0 down. 5 LEDs → every 0.5.
        val run = LightRun("r", "Roof", "d", 0, 5, points = listOf(MapPoint(0f, 0f), MapPoint(0.5f, 0f), MapPoint(0.5f, 1f)))
        val p = run.tracedPositions(aspect = 2f)
        assertEquals(5, p.size)
        close(MapPoint(0f, 0f), p[0])
        close(MapPoint(0.25f, 0f), p[1])
        close(MapPoint(0.5f, 0f), p[2])
        close(MapPoint(0.5f, 0.5f), p[3])
        close(MapPoint(0.5f, 1f), p[4])
    }

    @Test
    fun perLedPointsOverrideTraceAndMissingStayDark() {
        val run = LightRun("r", "Bush", "d", 10, 3, points = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f)), ledPoints = listOf(MapPoint(0.2f, 0.2f), null))
        val p = run.ledPositions(1f)
        assertEquals(3, p.size)
        close(MapPoint(0.2f, 0.2f), p[0])
        assertNull(p[1]); assertNull(p[2])
        val placed = HouseMap(runs = listOf(run)).placedLeds()
        assertEquals(1, placed.size)
        assertEquals(10, placed[0].index)
    }

    @Test
    fun fillGapsInterpolatesInsideTheSpanOnly() {
        val filled = listOf(null, MapPoint(0f, 0f), null, null, MapPoint(0.3f, 0.6f), null).fillGaps()
        assertNull(filled[0])
        close(MapPoint(0.1f, 0.2f), filled[2])
        close(MapPoint(0.2f, 0.4f), filled[3])
        assertNull(filled[5])
    }

    @Test
    fun fixedSpacingPlacesLedsFromTheStart() {
        // Scale: the full picture width (aspect 2 → 2 picture heights) is 200 cm, so 100 cm per height.
        val map = HouseMap(aspect = 2f, scaleA = MapPoint(0f, 0.5f), scaleB = MapPoint(1f, 0.5f), scaleCm = 200f)
        assertEquals(100f, map.cmPerHeight!!, 1e-3f)
        // A 100 cm line (x 0 → 0.5); LEDs every 30 cm, 5 of them: the last runs on past the end.
        val run = LightRun("r", "Roof", "d", 0, 5, points = listOf(MapPoint(0f, 0f), MapPoint(0.5f, 0f)), spacingCm = 30f)
        val p = map.positions(run)
        close(MapPoint(0f, 0f), p[0])
        close(MapPoint(0.15f, 0f), p[1])
        close(MapPoint(0.45f, 0f), p[3])
        close(MapPoint(0.6f, 0f), p[4])
        // Without a scale the run spreads evenly as before.
        close(MapPoint(0.5f, 0f), run.ledPositions(2f)[4])
    }

    @Test
    fun lengthsReadNaturally() {
        assertEquals("4 in", formatLength(10.16f, inches = true))
        assertEquals("40 ft", formatLength(40 * 30.48f, inches = true))
        assertEquals("3 ft 6 in", formatLength(42 * 2.54f, inches = true))
        assertEquals("1 ft 6 in", formatLength(18 * 2.54f, inches = true))
        assertEquals("1.67 m", formatLength(166.7f, inches = false))
    }

    @Test
    fun hiddenStretchTakesItsLedsWhenSpreadEvenly() {
        // 6 LEDs; the middle stretch (0.4 → 0.6) is round a corner and holds 2 of them.
        val run = LightRun(
            "r", "Porch", "d", 0, 6,
            points = listOf(MapPoint(0f, 0f), MapPoint(0.4f, 0f), MapPoint(0.6f, 0f), MapPoint(1f, 0f)),
            hidden = listOf(HiddenStretch(after = 1, leds = 2)),
        )
        val p = run.ledPositions(1f)
        val xs = listOf(0f, 0.8f / 3, 0.4f + 0.2f / 3, 0.4f + 0.4f / 3, 0.6f + (1.6f / 3 - 0.4f), 1f)
        xs.forEachIndexed { i, x -> close(MapPoint(x, 0f), p[i]) }
        assertEquals(listOf(false, false, true, true, false, false), run.hiddenFlags(1f).toList())
        val placed = HouseMap(aspect = 1f, runs = listOf(run)).placedLeds()
        assertEquals(6, placed.size)
        assertEquals(2, placed.count { it.hidden })
    }

    @Test
    fun hiddenStretchUsesItsRealLengthWithSpacing() {
        // 100 cm per picture height; three 50 cm pieces, the middle one really 150 cm round the porch.
        val map = HouseMap(aspect = 2f, scaleA = MapPoint(0f, 0.5f), scaleB = MapPoint(1f, 0.5f), scaleCm = 200f)
        val run = LightRun(
            "r", "Porch", "d", 0, 10,
            points = listOf(MapPoint(0f, 0f), MapPoint(0.25f, 0f), MapPoint(0.5f, 0f), MapPoint(0.75f, 0f)),
            spacingCm = 25f,
            hidden = listOf(HiddenStretch(after = 1, lengthCm = 150f)),
        )
        val flags = run.hiddenFlags(2f, map.cmPerHeight)
        assertEquals(listOf(false, false, false, true, true, true, true, true, true, false), flags.toList())
        close(MapPoint(0.625f, 0f), map.positions(run)[9])
    }

    @Test
    fun hiddenLedsPlacedOneByOneGetEstimatedSpots() {
        val run = LightRun(
            "r", "Bush", "d", 0, 4, ledPoints = listOf(MapPoint(0f, 0f), null, null, MapPoint(0.3f, 0f)), hiddenLeds = setOf(1, 2),
        )
        val placed = HouseMap(aspect = 1f, runs = listOf(run)).placedLeds()
        assertEquals(4, placed.size)
        assertEquals(listOf(false, true, true, false), placed.map { it.hidden })
        assertEquals(0.1f, placed[1].x, 1e-4f)
    }

    @Test
    fun newPropsGoOnAtRealSizeAndStayOnThePicture() {
        // 1200 cm across 0.6 of a 1.5-wide photo: 1200 / 0.9 cm per picture height.
        val map = HouseMap(aspect = 1.5f, scaleA = MapPoint(0.2f, 0.8f), scaleB = MapPoint(0.8f, 0.8f), scaleCm = 1200f)
        val box = map.propBox(MapPoint(0.5f, 0.5f), 120f, 90f, 1f)
        assertEquals(0.06f, box[1].x - box[0].x, 1e-4f)   // 120 cm = 0.09 heights = 0.06 widths
        assertEquals(0.0675f, box[1].y - box[0].y, 1e-4f)
        // Too near the edge: pulled in so it all shows.
        val edge = map.propBox(MapPoint(0.99f, 0.99f), 120f, 90f, 1f)
        assertEquals(1f, edge[1].x, 1e-4f); assertEquals(1f, edge[1].y, 1e-4f)
        // No real size: a fifth of the picture high, shaped like its grid.
        val plain = HouseMap(aspect = 1.5f).propBox(MapPoint(0.5f, 0.5f), null, null, 1.5f)
        assertEquals(0.2f, plain[1].y - plain[0].y, 1e-4f)
        assertEquals(0.2f, plain[1].x - plain[0].x, 1e-4f)
    }

    @Test
    fun propsMoveAndResizeAboutTheirMiddle() {
        val run = LightRun("p", "Star", "d", 0, 10, points = listOf(MapPoint(0.4f, 0.4f), MapPoint(0.6f, 0.5f)),
            model = PropModel(ModelKind.STAR), realWidthCm = 100f, realHeightCm = 50f)
        assertEquals(0.5f, run.pivot()!!.x, 1e-5f); assertEquals(0.45f, run.pivot()!!.y, 1e-5f)
        val moved = run.transformed(0.1f, -0.05f)
        assertEquals(0.5f, moved.points[0].x, 1e-5f); assertEquals(0.35f, moved.points[0].y, 1e-5f)
        assertEquals(100f, moved.realWidthCm!!, 1e-4f)
        val bigger = run.transformed(0f, 0f, zoom = 2f)
        assertEquals(0.3f, bigger.points[0].x, 1e-5f); assertEquals(0.7f, bigger.points[1].x, 1e-5f)
        assertEquals(0.5f, bigger.pivot()!!.x, 1e-5f)
        assertEquals(200f, bigger.realWidthCm!!, 1e-4f); assertEquals(100f, bigger.realHeightCm!!, 1e-4f)
    }

    @Test
    fun mirroredPropsFaceTheOtherWay() {
        // A 2 × 1 matrix: LED 0 on the left, LED 1 on the right.
        val run = LightRun("w", "Left corner web", "d", 0, 2, points = listOf(MapPoint(0.2f, 0.2f), MapPoint(0.4f, 0.3f)),
            model = PropModel(ModelKind.MATRIX, strings = 1, nodes = 2, start = StartCorner.BOTTOM_LEFT))
        val normal = run.ledPositions(1f)
        val flipped = run.copy(mirrored = true).ledPositions(1f)
        assertEquals(0.6f - normal[0]!!.x, flipped[0]!!.x, 1e-5f)
        assertEquals(normal[0]!!.y, flipped[0]!!.y, 1e-5f)
        val upside = run.copy(upsideDown = true).ledPositions(1f)
        assertEquals(0.5f - normal[0]!!.y, upside[0]!!.y, 1e-5f)

        val other = run.mirroredCopy("w2")
        assertEquals("Right corner web", other.name)
        assertEquals(true, other.mirrored)
        assertEquals("w2", other.id)
        assertEquals(0.43f, other.points[0].x, 1e-5f)  // set beside it, to the right
        assertEquals("Arch", mirroredName("Arch (mirrored)"))
        assertEquals("Arch (mirrored)", mirroredName("Arch"))
        assertEquals("RIGHT web", mirroredName("LEFT web"))
    }
}
