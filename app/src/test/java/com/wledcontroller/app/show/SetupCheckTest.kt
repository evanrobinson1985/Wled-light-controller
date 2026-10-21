package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.MapPoint
import com.wledcontroller.app.house.PropGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCheckTest {
    private val placed = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f))

    @Test
    fun aCleanSetupHasNoProblems() {
        val map = HouseMap(runs = listOf(LightRun("r", "Roof", "d1", 0, 100, points = placed)))
        val show = Sequence("s", "Carol", 10_000, listOf(Track("t", "Roof", TrackTarget(TrackTarget.Kind.PROP, "r"), listOf(Clip("c", 0, 1000)))))
        assertEquals(emptyList<SetupIssue>(), SetupCheck.run(map, mapOf("d1" to 150), listOf(show)) { true })
    }

    @Test
    fun findsTheUsualMistakes() {
        val map = HouseMap(
            runs = listOf(
                LightRun("r", "Roof", "d1", 0, 100, points = placed),
                LightRun("w", "Window", "d1", 90, 80, points = placed),   // overlaps the roof, past the end
                LightRun("b", "Bush", "gone", 0, 20),                      // missing controller, not placed
            ),
            groups = listOf(PropGroup("g", "Old", listOf("deleted"))),
        )
        val show = Sequence(
            "s", "Carol", 10_000,
            listOf(
                Track("t1", "Deleted prop", TrackTarget(TrackTarget.Kind.PROP, "nope"), listOf(Clip("c1", 0, 1000))),
                Track("t2", "Roof", TrackTarget(TrackTarget.Kind.PROP, "r"), listOf(
                    Clip("c2", 0, 2000, SeqEffect.VU_METER),
                    Clip("c3", 1000, 2000, SeqEffect.PICTURE),
                )),
            ),
        )
        val issues = SetupCheck.run(map, mapOf("d1" to 150), listOf(show)) { false }
        val text = issues.joinToString("\n") { "${it.where}: ${it.message}" }
        assertTrue(text, issues.any { it.where == "Prop \"Bush\"" && "controller isn't in the app" in it.message })
        assertTrue(text, issues.any { it.where == "Prop \"Window\"" && "only has 150" in it.message })
        assertTrue(text, issues.any { "Share LEDs 90–99" in it.message })
        assertTrue(text, issues.any { it.where == "Prop \"Bush\"" && "placed" in it.message })
        assertTrue(text, issues.any { it.where.startsWith("Group") })
        assertTrue(text, issues.any { "no longer exists" in it.message })
        assertTrue(text, issues.any { "has no song" in it.message })
        assertTrue(text, issues.any { "has no picture" in it.message })
        assertTrue(text, issues.any { "overlap" in it.message })
        // Problems come before warnings.
        assertEquals(SetupIssue.Severity.PROBLEM, issues.first().severity)
    }
}
