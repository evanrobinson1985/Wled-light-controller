package com.wledcontroller.app.show

import org.w3c.dom.Element
import java.io.InputStream
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

/** One effect from an xLights sequence. */
data class XsqEffect(val name: String, val startMs: Long, val endMs: Long, val settings: Map<String, String>, val palette: Map<String, String>)

/** A model (or one of its submodels) in an xLights sequence, with its effect layers (top first). */
data class XsqElement(val name: String, val submodel: String?, val layers: List<List<XsqEffect>>) {
    val label get() = if (submodel != null) "$name / $submodel" else name
    val effectCount get() = layers.sumOf { it.size }
}

data class XsqSequence(
    val durationMs: Long,
    val mediaFile: String?,
    val marks: List<Long>,
    val elements: List<XsqElement>,
)

/**
 * Reads xLights sequences (.xsq) so they can be edited here: each model's effect layers become
 * tracks on the matching props, groups or prop parts, with xLights effects turned into the
 * nearest effects this app has.
 */
object XsqImport {

    fun parse(input: InputStream): XsqSequence {
        val f = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = f.newDocumentBuilder().parse(input)
        val root = doc.documentElement
        require(root.tagName == "xsequence") { "That isn't an xLights sequence (.xsq)." }
        fun Element.children(tag: String) = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }
        fun Element.child(tag: String) = children(tag).firstOrNull()

        val head = root.child("head")
        val durationMs = head?.child("sequenceDuration")?.textContent?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0L
        val media = head?.child("mediaFile")?.textContent?.trim()?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }

        val palettes = root.child("ColorPalettes")?.children("ColorPalette")?.map { settingsOf(it.textContent) }.orEmpty()
        val effectDb = root.child("EffectDB")?.children("Effect")?.map { settingsOf(it.textContent) }.orEmpty()

        fun effectsIn(layer: Element) = layer.children("Effect").mapNotNull { e ->
            val start = e.getAttribute("startTime").toLongOrNull() ?: return@mapNotNull null
            val end = e.getAttribute("endTime").toLongOrNull() ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            val ref = e.getAttribute("ref").toIntOrNull()
            val settings = ref?.let { effectDb.getOrNull(it) } ?: settingsOf(e.textContent)
            val pal = e.getAttribute("palette").toIntOrNull()?.let { palettes.getOrNull(it) }.orEmpty()
            XsqEffect(e.getAttribute("name"), start, end, settings, pal)
        }

        val marks = ArrayList<Long>()
        val elements = ArrayList<XsqElement>()
        for (el in root.child("ElementEffects")?.children("Element").orEmpty()) {
            val name = el.getAttribute("name")
            when (el.getAttribute("type")) {
                "timing" -> if (marks.isEmpty()) {
                    el.children("EffectLayer").firstOrNull()?.children("Effect")?.forEach { e -> e.getAttribute("startTime").toLongOrNull()?.let(marks::add) }
                }
                "model" -> {
                    val layers = el.children("EffectLayer").map(::effectsIn)
                    if (layers.any { it.isNotEmpty() }) elements += XsqElement(name, null, layers)
                    for (sub in el.children("SubModelEffectLayer")) {
                        // Strands of a model come through as layers too; keep only real effects.
                        val effects = effectsIn(sub)
                        if (effects.isNotEmpty()) elements += XsqElement(name, sub.getAttribute("name").ifBlank { "Part" }, listOf(effects))
                    }
                }
            }
        }
        val end = maxOf(durationMs, elements.flatMap { e -> e.layers.flatten() }.maxOfOrNull { it.endMs } ?: 0L)
        return XsqSequence(end, media, marks.sorted(), elements)
    }

    /** "A=1,B=x,y" style settings (values may contain commas only inside other fields' text). */
    private fun settingsOf(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var key: String? = null
        val sb = StringBuilder()
        // Split on commas that start a new KEY= (keys look like X_..._Name).
        for (part in text.split(',')) {
            val eq = part.indexOf('=')
            if (eq > 0 && part.substring(0, eq).matches(Regex("[A-Z]{1,2}_[A-Za-z0-9_]+"))) {
                key?.let { out[it] = sb.toString() }
                key = part.substring(0, eq); sb.setLength(0); sb.append(part.substring(eq + 1))
            } else if (key != null) {
                sb.append(',').append(part)
            }
        }
        key?.let { out[it] = sb.toString() }
        return out
    }

    /** The nearest effect here to an xLights effect, or null to skip it. */
    fun effectFor(xlights: String): SeqEffect? = when (xlights.lowercase()) {
        "on", "color wash", "colorwash", "fill" -> SeqEffect.ON
        "bars" -> SeqEffect.BARS
        "garlands" -> SeqEffect.GARLANDS
        "butterfly", "liquid", "kaleidoscope" -> SeqEffect.BUTTERFLY
        "plasma" -> SeqEffect.PLASMA
        "chase", "single strand", "singlestrand" -> SeqEffect.CHASE
        "marquee" -> SeqEffect.MARQUEE
        "wave", "color wave", "colorwave" -> SeqEffect.COLOR_WAVE
        "wipe" -> SeqEffect.WIPE
        "curtain" -> SeqEffect.CURTAIN
        "fire", "candle" -> SeqEffect.FIRE
        "shimmer" -> SeqEffect.SHIMMER
        "fireworks" -> SeqEffect.FIREWORKS
        "shockwave" -> SeqEffect.SHOCKWAVE
        "ripple", "circles" -> SeqEffect.RIPPLE
        "meteors" -> SeqEffect.METEORS
        "morph", "lightning" -> SeqEffect.COMET
        "spirals", "spirograph" -> SeqEffect.SPIRALS
        "pinwheel", "fan" -> SeqEffect.PINWHEEL
        "galaxy" -> SeqEffect.GALAXY
        "snowflakes", "snowstorm" -> SeqEffect.SNOW
        "strobe" -> SeqEffect.STROBE
        "text" -> SeqEffect.TEXT
        "twinkle" -> SeqEffect.TWINKLE
        "sparkle", "glediator" -> SeqEffect.SPARKLE
        "vu meter", "vumeter" -> SeqEffect.VU_METER
        "music" -> SeqEffect.MUSIC_PULSE
        "faces" -> SeqEffect.SING
        "pictures", "video" -> SeqEffect.PICTURE
        "rainbow" -> SeqEffect.RAINBOW
        "pulse" -> SeqEffect.PULSE
        else -> null
    }

    private fun colorsOf(palette: Map<String, String>): List<Int> {
        val cols = (1..8).mapNotNull { i ->
            if (palette["C_CHECKBOX_Palette$i"] != "1") return@mapNotNull null
            val raw = palette["C_BUTTON_Palette$i"] ?: return@mapNotNull null
            Regex("#([0-9A-Fa-f]{6})").find(raw)?.groupValues?.get(1)?.toLong(16)?.toInt()?.let { it or (0xFF shl 24) }
        }
        return cols.ifEmpty { listOf(0xFFFFFFFF.toInt()) }.take(3)
    }

    private fun transitionFor(name: String?): Transition = when (name?.lowercase()) {
        "wipe", "slide bars", "slide checks" -> Transition.WIPE
        "from middle", "clock" -> Transition.FROM_MIDDLE
        "square explode", "circle explode", "circles", "star" -> Transition.CIRCLE
        "dissolve", "pinwheel" -> Transition.DISSOLVE
        "blinds", "blend" -> Transition.BLINDS
        else -> Transition.FADE
    }

    /**
     * Turns [xsq] into a show. [targets] maps each element's [XsqElement.label] to where it goes
     * here; elements mapped to null are left out. Returns the show and how many effects had no
     * equivalent here.
     */
    fun toSequence(xsq: XsqSequence, name: String, targets: Map<String, TrackTarget?>): Pair<Sequence, Int> {
        var skipped = 0
        val tracks = ArrayList<Track>()
        for (el in xsq.elements) {
            val target = targets[el.label] ?: continue
            el.layers.forEachIndexed { li, layer ->
                val clips = layer.mapNotNull { e ->
                    val fx = effectFor(e.name) ?: run { skipped++; return@mapNotNull null }
                    val s = e.settings
                    fun seconds(key: String) = s[key]?.toFloatOrNull()?.let { (it * 1000).toLong() } ?: 0L
                    val dur = e.endMs - e.startMs
                    Clip(
                        id = UUID.randomUUID().toString(),
                        startMs = e.startMs,
                        durationMs = dur,
                        effect = fx,
                        colors = colorsOf(e.palette),
                        intensity = (e.palette["C_SLIDER_Brightness"]?.toFloatOrNull()?.div(100f) ?: 1f).coerceIn(0f, 1f),
                        fadeInMs = seconds("T_TEXTCTRL_Fadein").coerceAtMost(dur / 2),
                        fadeOutMs = seconds("T_TEXTCTRL_Fadeout").coerceAtMost(dur / 2),
                        inTransition = transitionFor(s["T_CHOICE_In_Transition_Type"]),
                        outTransition = transitionFor(s["T_CHOICE_Out_Transition_Type"]),
                        text = s.entries.firstOrNull { it.key.startsWith("E_TEXTCTRL_Text") && it.key != "E_TEXTCTRL_Text_Font" }?.value,
                    )
                }
                if (clips.isEmpty()) return@forEachIndexed
                tracks += Track(
                    UUID.randomUUID().toString(),
                    el.label + if (el.layers.size > 1) " · layer ${li + 1}" else "",
                    target,
                    clips.sortedBy { it.startMs },
                    // Upper layers cover the ones below, as in xLights.
                    blend = if (li < el.layers.lastIndex) BlendMode.NORMAL else BlendMode.MAX,
                )
            }
        }
        val seq = Sequence(
            id = UUID.randomUUID().toString(),
            name = name,
            durationMs = xsq.durationMs.coerceAtLeast(1000),
            tracks = tracks,
            marks = xsq.marks,
            audioTitle = xsq.mediaFile,
        )
        return seq to skipped
    }
}
