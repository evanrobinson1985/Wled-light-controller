package com.wledcontroller.app.house

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VendorCatalogTest {

    @Test
    fun vendorListSkipsMusic() {
        val xml = """<vendors>
    <!--  model vendors just use the model tag  -->
    <vendor><name>Boscoyo Studio, LLC</name><link>http://hohenseefamily.com/xlights/boscoyo.xml</link></vendor>
    <vendor><name>Mattos Designs</name><link>https://models.mattosdesigns.com/xlights/mattos.xml</link><maxmodels>50</maxmodels></vendor>
    <vendor><name>DMX Fixture Library</name><link>https://raw.githubusercontent.com/xLightsSequencer/xLights/master/download/dmx_fixtures.xml</link></vendor>
    <musicvendor><name>Free</name><link>https://example.com/music.xml</link></musicvendor>
</vendors>"""
        assertEquals(listOf("Boscoyo Studio, LLC", "Mattos Designs", "DMX Fixture Library"), VendorCatalog.parseVendors(xml).map { it.name })
    }

    @Test
    fun inventoryReadsNestedCategoriesAndModels() {
        // Trimmed from Gilbert Engineering USA's catalogue.
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<modelinventory>
    <vendor><name>Gilbert Engineering USA</name></vendor>
    <categories>
        <category><id>1</id><name>Christmas</name>
            <categories>
                <category><id>6</id><name>Arches</name>
                    <categories><category><id>8</id><name>6 Ft</name></category></categories>
                </category>
            </categories>
        </category>
    </categories>
    <models>
        <model>
            <name>Eye of the Widow</name><id>1</id><type>Coro Prop</type>
            <width>28&quot;</width><height>44&quot;</height><pixelCount>325</pixelCount><pixelspacing>4&quot;</pixelspacing>
            <wiring><name></name><xmodellink>https://raw.githubusercontent.com/GEUSA/xmodels/refs/heads/master/xmodels/GE Eye of the Widow.xmodel</xmodellink></wiring>
            <categoryid>8</categoryid>
        </model>
        <model><id>2</id><name>No download</name><categoryid>8</categoryid></model>
    </models>
</modelinventory>"""
        val inv = VendorCatalog.parseInventory(xml)
        assertEquals("Gilbert Engineering USA", inv.vendor)
        assertEquals(listOf("Christmas", "Christmas › Arches", "Christmas › Arches › 6 Ft"), inv.categories.map { it.path })
        val m = inv.models.single()
        assertEquals("Eye of the Widow", m.name)
        assertEquals("325", m.pixelCount)
        assertEquals(listOf("8"), m.categoryIds)
        assertEquals("Standard wiring", m.wirings.single().name)
        assertEquals(71.12f, VendorCatalog.lengthCm(m.width)!!, 0.01f)
    }

    @Test
    fun sizesReadInTheirUnits() {
        assertEquals(55.88f, VendorCatalog.lengthCm("22\" (23cm)")!!, 0.01f)
        assertEquals(182.88f, VendorCatalog.lengthCm("6 Ft")!!, 0.01f)
        assertEquals(207.264f, VendorCatalog.lengthCm("6.8 Ft")!!, 0.01f)
        assertEquals(121.92f, VendorCatalog.lengthCm("4'")!!, 0.01f)
        assertEquals(120f, VendorCatalog.lengthCm("120 cm")!!, 0.01f)
        assertEquals(36.83f, VendorCatalog.lengthCm("14 1/2\"")!!, 0.01f)
        assertNull(VendorCatalog.lengthCm("Confirm on product page"))
    }

    @Test
    fun realSizeSetsTheBox() {
        // 100 cm per picture height, photo twice as wide as tall.
        val map = HouseMap(aspect = 2f, scaleA = MapPoint(0f, 0.5f), scaleB = MapPoint(1f, 0.5f), scaleCm = 200f)
        val box = map.boxAround(MapPoint(0.5f, 0.5f), widthCm = 40f, heightCm = 20f)!!
        assertEquals(0.4f, box[0].x, 1e-4f); assertEquals(0.4f, box[0].y, 1e-4f)
        assertEquals(0.6f, box[1].x, 1e-4f); assertEquals(0.6f, box[1].y, 1e-4f)
        assertNull(HouseMap().boxAround(MapPoint(0.5f, 0.5f), 40f, 20f))
    }
}
