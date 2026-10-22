package com.wledcontroller.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareTest {
    private val json = """{"tag_name":"v0.15.1","assets":[
        {"name":"WLED_0.15.1_ESP32.bin","browser_download_url":"https://x/esp32.bin"},
        {"name":"WLED_0.15.1_ESP32_Ethernet.bin","browser_download_url":"https://x/eth.bin"},
        {"name":"WLED_0.15.1_ESP8266.bin","browser_download_url":"https://x/8266.bin"},
        {"name":"WLED_0.15.1_ESP32.bin.gz","browser_download_url":"https://x/gz"}]}"""

    @Test
    fun picksTheRightFile() {
        val r = Firmware.parseRelease(json)!!
        assertEquals("0.15.1", r.version)
        assertEquals("https://x/esp32.bin", Firmware.assetFor(r, "ESP32")!!.url)
        assertEquals("https://x/eth.bin", Firmware.assetFor(r, "ESP32_Ethernet")!!.url)
        assertEquals("https://x/8266.bin", Firmware.assetFor(r, "ESP8266")!!.url)
        assertNull(Firmware.assetFor(r, null))
        assertNull(Firmware.assetFor(r, "Custom_Build"))
    }

    @Test
    fun comparesVersions() {
        assertTrue(Firmware.isNewer("0.15.1", "0.14.4"))
        assertTrue(Firmware.isNewer("0.15.0", "0.15.0-b3"))
        assertFalse(Firmware.isNewer("0.15.0", "0.15.0"))
        assertFalse(Firmware.isNewer("0.14.4", "0.15.0"))
    }
}
