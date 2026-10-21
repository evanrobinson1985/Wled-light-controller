package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestPatternTest {
    @Test
    fun chaseLightsOneInTenAndMoves() {
        val a = (0 until 30).map { TestPattern.CHASE.color(it, 0) }
        assertEquals(3, a.count { it != 0 })
        val b = (0 until 30).map { TestPattern.CHASE.color(it, 3) }
        assertTrue(a != b)
    }

    @Test
    fun countMarksEveryTenthFiftiethAndHundredth() {
        assertEquals(0xFF0000, TestPattern.COUNT.color(9, 0))
        assertEquals(0x00FF00, TestPattern.COUNT.color(49, 0))
        assertEquals(0x0000FF, TestPattern.COUNT.color(99, 0))
    }

    @Test
    fun framesHonourBrightness() {
        val f = TestPattern.WHITE.frame(4, 0, 0.5f)
        assertEquals(12, f.size)
        assertEquals(127, f[0].toInt() and 0xFF)
        val colours = (0 until 160 step 40).map { TestPattern.COLORS.color(0, it) }
        assertEquals(listOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF), colours)
    }
}
