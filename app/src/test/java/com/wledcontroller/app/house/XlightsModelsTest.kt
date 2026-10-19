package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XlightsModelsTest {

    @Test
    fun vendorCustomModelKeepsItsLayoutAndParts() {
        // A 4 × 3 snowflake-ish grid, nodes 1..6, with two submodels.
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<custommodel name="Mini Snowflake" parm1="4" parm2="3" StringType="RGB Nodes" CustomModel=",1,2,;3,,,4;,5,6," SourceVersion="2023.10">
  <subModel name="Center" layout="horizontal" type="ranges" line0="1-2" line1="5"/>
  <subModel name="Arms" layout="horizontal" type="ranges" line0="3,4,6"/>
</custommodel>"""
        val m = XlightsModels.parseXmodel(xml.byteInputStream())
        assertEquals("Mini Snowflake", m.name)
        assertEquals(6, m.ledCount)
        val model = m.model!!
        assertEquals(ModelKind.CUSTOM, model.kind)
        assertEquals(listOf(1, 2, 4, 7, 9, 10), model.customCells)
        val p = model.layout(MapPoint(0f, 0f), MapPoint(1f, 1f), 1f)
        assertEquals(0.375f, p[0].x, 1e-4f); assertEquals(1f / 6, p[0].y, 1e-4f)   // node 1: row 0, column 1
        assertEquals(0.875f, p[3].x, 1e-4f); assertEquals(0.5f, p[3].y, 1e-4f)    // node 4: row 1, column 3
        assertEquals(listOf("Center", "Arms"), m.submodels.map { it.name })
        assertEquals(listOf(0..1, 4..4), m.submodels[0].ranges)
        assertEquals(listOf(2..2, 3..3, 5..5), m.submodels[1].ranges)
    }

    @Test
    fun compressedCustomModel() {
        val xml = """<custommodel name="C" parm1="3" parm2="2" CustomModelCompressed="1,0,0;2,0,2;3,1,1"/>"""
        assertEquals(listOf(0, 2, 4), XlightsModels.parseXmodel(xml.byteInputStream()).model!!.customCells)
    }

    @Test
    fun wrappedExportWithCustomSize() {
        // As newer xLights exports (and some vendors) write it: wrapped, sized by CustomWidth/Height.
        val xml = """<?xml version="1.0"?>
<models type="exported">
  <model name="GE Starburst" DisplayAs="Custom" CustomWidth="3" CustomHeight="2" CustomModelCompressed="1,0,0;2,0,2;3,1,1" StartChannel="2402" widthmm="4605" heightmm="8794"/>
</models>"""
        val m = XlightsModels.parseXmodel(xml.byteInputStream())
        assertEquals("GE Starburst", m.name)
        assertEquals(3, m.ledCount)
        assertEquals(3, m.model!!.customW)
        assertEquals(listOf(0, 2, 4), m.model!!.customCells)
        assertEquals(460.5f, m.widthCm!!, 1e-3f)
        assertEquals(879.4f, m.heightCm!!, 1e-3f)
    }

    private val layoutXml = """<?xml version="1.0" encoding="UTF-8"?>
<xrgb>
  <models>
    <model name="Roofline" DisplayAs="Single Line" parm1="1" parm2="150" StartChannel="!Garage F16:1"/>
    <model name="Arches" DisplayAs="Arches" parm1="3" parm2="25" StartChannel="&gt;Roofline:1"/>
    <model name="Mega Tree" DisplayAs="Tree 360" parm1="16" parm2="50" StartChannel="!Tree Controller:1" StartSide="B"/>
    <model name="Star" DisplayAs="Star" parm1="1" parm2="100" parm3="5" StartChannel="!Tree Controller:2401">
      <subModel name="Top point" type="ranges" line0="1-20"/>
    </model>
    <model name="Panel" DisplayAs="Horiz Matrix" parm1="20" parm2="50" StartChannel="4501" Dir="R" StartSide="T"/>
    <model name="Moving head" DisplayAs="DmxMovingHead" parm1="1" parm2="1" StartChannel="1"/>
  </models>
  <modelGroups>
    <modelGroup name="Front yard" models="Arches,Star"/>
  </modelGroups>
</xrgb>"""

    @Test
    fun layoutBringsModelsGroupsAndStarts() {
        val l = XlightsModels.parseLayout(layoutXml.byteInputStream())
        assertEquals(listOf("Roofline", "Arches", "Mega Tree", "Star", "Panel"), l.models.map { it.name })
        assertEquals(listOf("Garage F16", "Tree Controller"), l.controllers)
        assertEquals(listOf("Front yard" to listOf("Arches", "Star")), l.groups)

        val arches = l.models[1]
        assertEquals(75, arches.ledCount)
        assertEquals(PropType.ARCH, arches.prop)
        assertNull(arches.model)
        assertEquals(listOf("Arch 1", "Arch 2", "Arch 3"), arches.submodels.map { it.name })

        val tree = l.models[2].model!!
        assertEquals(ModelKind.MEGA_TREE, tree.kind)
        assertEquals(800, tree.ledCount)
        assertEquals(360, tree.degrees)

        val star = l.models[3]
        assertEquals(5, star.model!!.points)
        assertEquals(100, star.ledCount)
        assertEquals("Top point", star.submodels.single().name)

        val panel = l.models[4].model!!
        assertEquals(ModelKind.MATRIX, panel.kind)
        assertEquals(StartCorner.TOP_RIGHT, panel.start)

        val starts = XlightsModels.resolveStarts(l.models, "Default")
        assertEquals("Garage F16" to 0, starts["Roofline"])
        assertEquals("Garage F16" to 150, starts["Arches"])   // chained after the roofline
        assertEquals("Tree Controller" to 800, starts["Star"])  // channel 2401 = LED 800
        assertEquals("Default" to 1500, starts["Panel"])        // absolute channel 4501
    }
}
