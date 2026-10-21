package com.wledcontroller.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionTest {
    @Test
    fun straightOnTheHouseIsFlatAndCloserIsBigger() {
        val p = Projection(yaw = 0f, pitch = 0f, zoom = 1f, w = 1600f, h = 1000f)
        val (cx, cy, _) = p.project(0f, 0f, 0f)
        assertEquals(800f, cx, 0.01f)
        // Left of centre on screen is left in the house; up is up.
        assertTrue(p.project(-0.5f, 0f, 0f).first < cx)
        assertTrue(p.project(0f, 0.5f, 0f).second < cy)
        // A yard prop in front looks further from the middle than the same spot on the wall.
        val wall = p.project(0.4f, 0f, 0f).first - cx
        val yard = p.project(0.4f, 0f, 1f).first - cx
        assertTrue(yard > wall)
    }

    @Test
    fun turningMovesThingsInFrontSideways() {
        val straight = Projection(0f, 0f, 1f, 1600f, 1000f).project(0f, 0f, 1f).first
        val turned = Projection(0.6f, 0f, 1f, 1600f, 1000f).project(0f, 0f, 1f).first
        assertTrue(turned != straight)
    }
}
