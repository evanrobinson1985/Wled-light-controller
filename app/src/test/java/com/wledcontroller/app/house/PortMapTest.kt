package com.wledcontroller.app.house

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortMapTest {
    private fun run(name: String, start: Int, count: Int) = LightRun(name, name, "d", start, count)

    @Test
    fun spansShowPropsGapsOverlapsAndOverruns() {
        val spans = PortMap.spans(listOf(run("Roof", 0, 100), run("Window", 90, 30), run("Bush", 150, 80)), 200)
        assertEquals(
            listOf(
                PortSpan(0, 90, PortSpan.Kind.PROP, listOf("Roof")),
                PortSpan(90, 100, PortSpan.Kind.OVERLAP, listOf("Roof", "Window")),
                PortSpan(100, 120, PortSpan.Kind.PROP, listOf("Window")),
                PortSpan(120, 150, PortSpan.Kind.UNUSED, emptyList()),
                PortSpan(150, 200, PortSpan.Kind.PROP, listOf("Bush")),
                PortSpan(200, 230, PortSpan.Kind.PAST_END, listOf("Bush")),
            ),
            spans,
        )
    }

    @Test
    fun portsComeFromWledConfig() {
        val cfg = Json.parseToJsonElement("""{"hw":{"led":{"ins":[{"start":0,"len":150,"pin":[2]},{"start":150,"len":100,"pin":[13]}]}}}""") as JsonObject
        val ports = PortMap.ports(cfg, 250)
        assertEquals(listOf(Port(0, 150, "Output 1 (GPIO 2)"), Port(150, 100, "Output 2 (GPIO 13)")), ports)
        val roof = run("Roof", 100, 100)
        assertEquals(ports[0], PortMap.portOf(roof, ports))
        assertTrue(PortMap.crossesPorts(roof, ports))
        assertEquals(listOf(Port(0, 60, "All LEDs")), PortMap.ports(null, 60))
    }
}
