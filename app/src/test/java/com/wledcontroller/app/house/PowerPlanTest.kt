package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerPlanTest {
    @Test
    fun ampsSuppliesAndInjections() {
        // 300 pixels at 12 V: 12 A at full white, about 4.2 A in a show.
        val p = PowerPlan.plan(listOf(LightRun("r", "Roof", "d", 0, 300), LightRun("w", "Window", "d", 300, 100)), 0, PowerPlan.Volts.V12)
        assertEquals(400, p.leds)
        assertEquals(16f, p.fullAmps, 0.01f)
        assertEquals(5.6f, p.showAmps, 0.01f)
        assertEquals(231, p.wattsNeeded) // 16 A × 12 V × 1.2
        assertEquals(listOf(350), p.supplies)
        assertEquals(1, p.runs[0].injections) // 300 px at 12 V: inject once
        assertEquals(0, p.runs[1].injections)
        assertEquals(listOf(600, 600), PowerPlan.supplies(1000))
        assertEquals(2, PowerPlan.injections(250, PowerPlan.Volts.V5))
    }
}
