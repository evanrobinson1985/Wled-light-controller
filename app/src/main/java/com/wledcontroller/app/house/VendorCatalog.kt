package com.wledcontroller.app.house

import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

/** A prop maker (or fixture library) in xLights' list. */
data class Vendor(val name: String, val link: String)

data class VendorWiring(val name: String, val xmodelLink: String)

data class VendorModel(
    val id: String,
    val name: String,
    val type: String,
    val categoryIds: List<String>,
    val width: String,
    val height: String,
    val pixelCount: String,
    val pixelSpacing: String,
    val imageUrl: String?,
    val webLink: String?,
    val wirings: List<VendorWiring>,
)

/** A category with its full path, e.g. "Christmas › Arches › 6 Ft". */
data class VendorCategory(val id: String, val path: String)

data class VendorInventory(val vendor: String, val categories: List<VendorCategory>, val models: List<VendorModel>)

/**
 * The catalogue xLights uses for prop makers' models and the DMX fixture library: a list of
 * vendors, each with an inventory of models and .xmodel downloads.
 */
object VendorCatalog {
    const val VENDORS_URL = "https://raw.githubusercontent.com/xLightsSequencer/xLights/master/download/xlights_vendors.xml"

    private fun parse(xml: String): Element {
        val f = DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        return f.newDocumentBuilder().parse(xml.trimStart('﻿', ' ', '\n', '\r', '\t').byteInputStream()).documentElement
    }

    private fun Element.kids(tag: String) =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName.equals(tag, ignoreCase = true) }
    private fun Element.text(tag: String) = kids(tag).firstOrNull()?.textContent?.trim().orEmpty()

    fun parseVendors(xml: String): List<Vendor> =
        parse(xml).kids("vendor").mapNotNull { v ->
            val name = v.text("name"); val link = v.text("link")
            if (name.isBlank() || link.isBlank()) null else Vendor(name, link)
        }

    fun parseInventory(xml: String): VendorInventory {
        val root = parse(xml)
        val cats = ArrayList<VendorCategory>()
        fun walk(parent: Element, prefix: String) {
            for (c in parent.kids("categories").flatMap { it.kids("category") }) {
                val path = if (prefix.isEmpty()) c.text("name") else "$prefix › ${c.text("name")}"
                cats += VendorCategory(c.text("id"), path)
                walk(c, path)
            }
        }
        walk(root, "")
        val models = root.kids("models").flatMap { it.kids("model") }.map { m ->
            VendorModel(
                id = m.text("id"),
                name = m.text("name"),
                type = m.text("type"),
                categoryIds = m.kids("categoryid").map { it.textContent.trim() },
                width = m.text("width"),
                height = m.text("height"),
                pixelCount = m.text("pixelcount"),
                pixelSpacing = m.text("pixelspacing"),
                imageUrl = m.text("imagefile").takeIf { it.startsWith("http") },
                webLink = m.text("weblink").takeIf { it.isNotBlank() },
                wirings = m.kids("wiring").mapNotNull { w ->
                    val link = w.text("xmodellink").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    VendorWiring(w.text("name").ifBlank { "Standard wiring" }, link)
                },
            )
        }.filter { it.wirings.isNotEmpty() }
        return VendorInventory(root.kids("vendor").firstOrNull()?.text("name").orEmpty(), cats, models)
    }

    /**
     * A vendor's size text in centimetres: 28", 22" (23cm), 6 Ft, 6.8 Ft, 4', 120 cm, 1.2 m.
     * Plain numbers are taken as inches (as most listings are). Null if unreadable.
     */
    fun lengthCm(text: String): Float? {
        val t = text.trim().lowercase()
        val m = Regex("(\\d+(?:\\.\\d+)?)(?:[\\s-]+(\\d+)/(\\d+))?\\s*(ft|feet|foot|'|in|inch|inches|\"|”|cm|mm|m)?").find(t) ?: return null
        var v = m.groupValues[1].toFloat()
        if (m.groupValues[2].isNotEmpty()) v += m.groupValues[2].toFloat() / m.groupValues[3].toFloat().coerceAtLeast(1f)
        val cm = when (m.groupValues[4]) {
            "ft", "feet", "foot", "'" -> v * 30.48f
            "cm" -> v
            "mm" -> v / 10f
            "m" -> v * 100f
            else -> v * 2.54f
        }
        return cm.takeIf { it > 0f }
    }

    /** Fetches text over HTTP(S); spaces in vendors' links are allowed, as xLights allows them. */
    fun fetch(client: OkHttpClient, url: String): String {
        val req = Request.Builder().url(url.trim().replace(" ", "%20")).header("User-Agent", "WLED-Controller").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("The vendor's site answered ${resp.code}.")
            return resp.body?.string() ?: throw IOException("Nothing came back from the vendor's site.")
        }
    }
}
