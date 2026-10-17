package com.wledcontroller.app.show

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/** A controller from xLights' network setup and the channels it occupies (1-based). */
data class XlightsController(val name: String, val ip: String, val startChannel: Int, val channels: Int)

/**
 * Reads xlights_networks.xml. Controllers take consecutive channel ranges in file order; each
 * one's size is the sum of its outputs' MaxChannels. Older files list bare <network> entries
 * with the IP in ComPort.
 */
object XlightsNetworks {
    fun parse(input: InputStream): List<XlightsController> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        data class Raw(val name: String, val ip: String, var channels: Int)
        val raws = ArrayList<Raw>()
        var current: Raw? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Controller" -> current = Raw(
                        parser.getAttributeValue(null, "Name") ?: "Controller",
                        parser.getAttributeValue(null, "IP") ?: "",
                        0,
                    )
                    "network" -> {
                        val max = parser.getAttributeValue(null, "MaxChannels")?.toIntOrNull() ?: 0
                        val c = current
                        if (c != null) c.channels += max
                        else raws += Raw(
                            parser.getAttributeValue(null, "Description") ?: "Output",
                            parser.getAttributeValue(null, "ComPort") ?: "",
                            max,
                        )
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "Controller") { current?.let { raws += it }; current = null }
            }
            event = parser.next()
        }
        var start = 1
        return raws.map { r -> XlightsController(r.name, r.ip.trim(), start, r.channels).also { start += r.channels } }
    }
}
