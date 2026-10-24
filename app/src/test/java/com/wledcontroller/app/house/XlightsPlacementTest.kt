package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XlightsPlacementTest {
    private val layout = """
        <xrgb>
          <settings>
            <previewWidth value="2200"/><previewHeight value="1400"/>
            <backgroundImage value="D:\xLights\Show\House.jpg"/>
          </settings>
          <models>
            <model DisplayAs="Single Line" name="Gutter" parm1="1" parm2="50" parm3="1" WorldPosX="100" WorldPosY="1000" X2="500" Y2="0" StartChannel="1"/>
            <model DisplayAs="Poly Line" name="Door" parm1="1" parm2="100" parm3="1" WorldPosX="1000" WorldPosY="400" ScaleX="100" ScaleY="100" NumPoints="3" PointData="-1,0,0,-1,2,0,1,2,0" StartChannel="151"/>
            <model DisplayAs="Vert Matrix" name="Pole" parm1="1" parm2="350" parm3="5" WorldPosX="2100" WorldPosY="600" ScaleX="4" ScaleY="3" StartChannel="451"/>
            <model DisplayAs="Window Frame" name="Window" parm1="12" parm2="20" parm3="12" WorldPosX="1400" WorldPosY="1000" ScaleX="3.5" ScaleY="4" StartChannel="1501"/>
            <model DisplayAs="Single Line" name="Flood" parm1="1" parm2="1" parm3="1" WorldPosX="300" WorldPosY="50" X2="3" Y2="-8" StartChannel="1700"/>
          </models>
          <modelGroups><modelGroup name="Outline" models="Gutter,Door"/></modelGroups>
        </xrgb>
    """.trimIndent()

    @Test
    fun placesPropsOnTheLayoutsPhoto() {
        val l = XlightsModels.parseLayout(layout.byteInputStream())
        val pv = l.preview!!
        assertEquals("House.jpg", pv.image)
        // A 3072 × 1728 photo fits the 2200-wide view: 2200 × 1237.5 from the bottom-left.
        val area = XlightsPlacement.photoArea(pv, 3072, 1728)
        assertEquals(2200f, area.first, 0.01f); assertEquals(1237.5f, area.second, 0.01f)
        fun pts(name: String) = XlightsPlacement.points(l.models.first { it.name == name }, area)!!
        // A line from (100, 1000) to (600, 1000): near the top of the photo.
        val gutter = pts("Gutter")
        assertEquals(100f / 2200, gutter[0].x, 1e-4f); assertEquals(1f - 1000f / 1237.5f, gutter[0].y, 1e-4f)
        assertEquals(600f / 2200, gutter[1].x, 1e-4f)
        // A poly line's points, scaled from its position: the door's outline.
        val door = pts("Door")
        assertEquals(3, door.size)
        assertEquals(900f / 2200, door[0].x, 1e-4f); assertEquals(1f - 400f / 1237.5f, door[0].y, 1e-4f)
        assertEquals(1f - 600f / 1237.5f, door[1].y, 1e-4f)
        // A vertical matrix: 5 columns of 70 lights, scaled 4 × 3, as a box round its position.
        val pole = l.models.first { it.name == "Pole" }
        assertNotNull(pole.model)
        val box = pts("Pole")
        assertEquals((2100f - 10f) / 2200, box[0].x, 1e-4f); assertEquals(1f - (600f + 105f) / 1237.5f, box[0].y, 1e-4f)
        assertEquals((2100f + 10f) / 2200, box[1].x, 1e-4f)
        // A window frame: its outline, round and back to where it started.
        val window = pts("Window")
        assertEquals(5, window.size); assertEquals(window.first(), window.last())
        // A single light: one spot.
        assertEquals(1, pts("Flood").size)
        // Everything on the photo.
        l.models.flatMap { XlightsPlacement.points(it, area)!! }.forEach { assertTrue(it.x in 0f..1f && it.y in 0f..1f) }
        // When the photo fills the view instead.
        val filled = XlightsPlacement.photoArea(pv.copy(scaleImage = true), 3072, 1728)
        assertEquals(1400f, filled.second, 0.01f)
    }
}
