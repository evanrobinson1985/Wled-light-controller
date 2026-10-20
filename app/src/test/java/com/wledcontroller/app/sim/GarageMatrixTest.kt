package com.wledcontroller.app.sim

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class GarageMatrixTest {
    @Test
    fun theDoorMatrixSnakesInRowsFromTheTopLeft() {
        val map = DemoSetup.matrixLedmap()
        assertEquals(96, map["width"]!!.jsonPrimitive.int)
        assertEquals(42, map["height"]!!.jsonPrimitive.int)
        val cells = map["map"]!!.jsonArray.map { it.jsonPrimitive.int }
        assertEquals(96 * 42, cells.size)
        // Every LED on the wiring is used exactly once.
        assertEquals((0 until 96 * 42).toList(), cells.sorted())
        // Row 1 runs left to right, row 2 comes back right to left.
        assertEquals(0, cells[0]); assertEquals(95, cells[95])
        assertEquals(191, cells[96]); assertEquals(96, cells[191])
    }
}
