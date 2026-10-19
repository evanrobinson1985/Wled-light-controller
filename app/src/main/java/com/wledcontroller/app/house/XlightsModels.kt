package com.wledcontroller.app.house

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/** A prop from xLights, ready to become a light run on the house map. */
data class XlModel(
    val name: String,
    val displayAs: String,
    val ledCount: Int,
    /** Wiring for boxed props (matrix, tree, star, custom); null for props traced as a line. */
    val model: PropModel?,
    val prop: PropType,
    val submodels: List<Submodel>,
    /** Where it starts: on a named controller (with [startChannel] 1-based on it), chained after another model, or an absolute channel. */
    val controller: String?,
    val startChannel: Int?,
    val chainedAfter: String?,
    val absoluteChannel: Int?,
)

/** Everything worth bringing over from an xLights layout. */
data class XlLayout(val models: List<XlModel>, val groups: List<Pair<String, List<String>>>) {
    /** Controllers the props are on, by xLights name. */
    val controllers get() = models.mapNotNull { it.controller }.distinct()
}

/**
 * Reads xLights prop models: a single exported or vendor model (.xmodel) or the whole layout in
 * xlights_rgbeffects.xml (models, their submodels and model groups).
 */
object XlightsModels {

    private fun parseXml(input: InputStream): Element {
        val f = DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        return f.newDocumentBuilder().parse(input).documentElement
    }

    /** A model file's model; newer exports wrap it: <models type="exported"><model …/></models>. */
    private fun modelRoot(input: InputStream): Element =
        parseXml(input).let { r -> if (r.tagName.equals("models", true)) r.children("model").firstOrNull() ?: r else r }

    private fun Element.children(tag: String) =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName.equals(tag, ignoreCase = true) }

    /** A single prop from an .xmodel file. */
    fun parseXmodel(input: InputStream): XlModel {
        val root = modelRoot(input)
        val displayAs = root.getAttribute("DisplayAs").ifBlank {
            when (root.tagName.lowercase()) {
                "custommodel" -> "Custom"
                else -> root.tagName.removeSuffix("model").replaceFirstChar(Char::uppercase)
            }
        }
        return modelFrom(root, displayAs) ?: throw IllegalArgumentException("That model type isn't supported yet.")
    }

    /** Whether an xLights model file describes a DMX fixture rather than pixels. */
    fun isDmx(xml: String): Boolean {
        val head = xml.take(4000)
        return head.contains("<dmxmodel", ignoreCase = true) || Regex("DisplayAs=\"Dmx", RegexOption.IGNORE_CASE).containsMatchIn(head)
    }

    /**
     * A DMX fixture from an xLights fixture model: its channels, with roles from the
     * Dmx…Channel settings and the channel names. The output, universe and address are left
     * for the user to set.
     */
    fun parseDmxFixture(input: InputStream): com.wledcontroller.app.data.DmxFixture {
        val e = modelRoot(input)
        val count = e.int("parm1", 0).coerceIn(1, 512)
        val names = e.getAttribute("NodeNames").split(',').map { it.trim() }
        val byAttr = HashMap<Int, com.wledcontroller.app.data.DmxRole>()
        fun attr(key: String, role: com.wledcontroller.app.data.DmxRole) {
            e.getAttribute(key).toIntOrNull()?.takeIf { it in 1..count }?.let { byAttr[it - 1] = role }
        }
        attr("DmxRedChannel", com.wledcontroller.app.data.DmxRole.RED)
        attr("DmxGreenChannel", com.wledcontroller.app.data.DmxRole.GREEN)
        attr("DmxBlueChannel", com.wledcontroller.app.data.DmxRole.BLUE)
        attr("DmxWhiteChannel", com.wledcontroller.app.data.DmxRole.WHITE)
        attr("DmxPanChannel", com.wledcontroller.app.data.DmxRole.PAN)
        attr("DmxTiltChannel", com.wledcontroller.app.data.DmxRole.TILT)
        attr("DmxShutterChannel", com.wledcontroller.app.data.DmxRole.SHUTTER)
        val shutterOpen = e.getAttribute("DmxShutterOpen").toIntOrNull()?.takeIf { it in 1..255 } ?: 255
        val channels = List(count) { i ->
            val name = names.getOrElse(i) { "" }
            val n = name.lowercase()
            val role = byAttr[i] ?: when {
                n.contains("fine") && n.contains("pan") -> com.wledcontroller.app.data.DmxRole.PAN_FINE
                n.contains("fine") && n.contains("tilt") -> com.wledcontroller.app.data.DmxRole.TILT_FINE
                n.contains("fine") -> com.wledcontroller.app.data.DmxRole.NONE
                n == "dimmer" || n.startsWith("dimmer") || n.contains("intensity") || n == "master" -> com.wledcontroller.app.data.DmxRole.DIMMER
                n == "amber" -> com.wledcontroller.app.data.DmxRole.AMBER
                n == "uv" || n.startsWith("uv ") -> com.wledcontroller.app.data.DmxRole.UV
                n == "red" -> com.wledcontroller.app.data.DmxRole.RED
                n == "green" -> com.wledcontroller.app.data.DmxRole.GREEN
                n == "blue" -> com.wledcontroller.app.data.DmxRole.BLUE
                n == "white" -> com.wledcontroller.app.data.DmxRole.WHITE
                n == "pan" -> com.wledcontroller.app.data.DmxRole.PAN
                n == "tilt" -> com.wledcontroller.app.data.DmxRole.TILT
                else -> com.wledcontroller.app.data.DmxRole.NONE // strobe, programs, macros … stay at 0
            }
            com.wledcontroller.app.data.DmxChannel(role, if (role == com.wledcontroller.app.data.DmxRole.SHUTTER) shutterOpen else 0, name)
        }
        return com.wledcontroller.app.data.DmxFixture(
            name = e.getAttribute("name").ifBlank { "DMX fixture" },
            outputId = "",
            channels = channels,
            type = e.getAttribute("DmxStyle").ifBlank { "Flood light" },
        )
    }

    /** All props and groups from xlights_rgbeffects.xml. */
    fun parseLayout(input: InputStream): XlLayout {
        val root = parseXml(input)
        require(root.tagName == "xrgb") { "That isn't an xLights layout (xlights_rgbeffects.xml)." }
        val models = root.children("models").flatMap { it.children("model") }
            .mapNotNull { m -> modelFrom(m, m.getAttribute("DisplayAs")) }
        val groups = root.children("modelGroups").flatMap { it.children("modelGroup") }.map { g ->
            g.getAttribute("name") to g.getAttribute("models").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
        return XlLayout(models, groups)
    }

    private fun Element.int(name: String, default: Int) = getAttribute(name).trim().toIntOrNull() ?: default

    private fun modelFrom(e: Element, displayAs: String): XlModel? {
        val name = e.getAttribute("name").ifBlank { "Imported prop" }
        val type = displayAs.lowercase()
        if (type.contains("dmx") || type == "image" || type == "label" || type == "channel block") return null
        val p1 = e.int("parm1", 1).coerceAtLeast(1)
        val p2 = e.int("parm2", 1).coerceAtLeast(1)
        val p3 = e.int("parm3", 1).coerceAtLeast(1)
        // Wiring as xLights describes it; "Dir" L/R and "StartSide" T/B set the start corner.
        val fromRight = e.getAttribute("Dir").equals("R", true)
        val fromTop = e.getAttribute("StartSide").equals("T", true)
        val corner = when {
            fromTop && fromRight -> StartCorner.TOP_RIGHT
            fromTop -> StartCorner.TOP_LEFT
            fromRight -> StartCorner.BOTTOM_RIGHT
            else -> StartCorner.BOTTOM_LEFT
        }
        var model: PropModel? = null
        var prop = PropType.LINE
        var count = p1 * p2
        when {
            type == "custom" -> {
                model = customModel(e)
                count = model?.ledCount ?: 0
                prop = PropType.MATRIX
            }
            type.contains("matrix") -> {
                model = PropModel(ModelKind.MATRIX, strings = p1, nodes = p2, start = corner, vertical = type.startsWith("vert"), zigzag = true)
                prop = PropType.MATRIX
            }
            type.startsWith("tree") -> {
                val degrees = Regex("\\d+").find(type)?.value?.toIntOrNull() ?: if (type.contains("flat")) 180 else 360
                model = PropModel(ModelKind.MEGA_TREE, strings = p1, nodes = p2, degrees = degrees.coerceIn(90, 360), fromBottom = !fromTop, zigzag = true)
                prop = PropType.TREE
            }
            type == "star" -> {
                model = PropModel(ModelKind.STAR, points = p3.coerceAtLeast(3), nodes = (p1 * p2 / p3.coerceAtLeast(3)).coerceAtLeast(2))
                prop = PropType.STAR
                count = model.ledCount
            }
            type.startsWith("arch") -> prop = PropType.ARCH
            type.startsWith("candy") -> prop = PropType.CANDY_CANE
            type.startsWith("window") -> { prop = PropType.WINDOW; count = p1 * 2 + p2 + p3 }
            type.startsWith("circle") || type.startsWith("wreath") || type.startsWith("sphere") || type.startsWith("spinner") -> prop = PropType.STAR
            else -> prop = PropType.LINE // single line, poly line, icicles, cube …
        }
        if (count <= 0) return null

        val subs = ArrayList<Submodel>()
        // Multi-prop models (several arches or canes in one) get a part per prop.
        if ((type.startsWith("arch") || type.startsWith("candy")) && p1 > 1) {
            for (i in 0 until p1) subs += Submodel("${if (prop == PropType.ARCH) "Arch" else "Cane"} ${i + 1}", listOf(i * p2 until (i + 1) * p2))
        }
        for (sm in e.children("subModel")) {
            val smName = sm.getAttribute("name")
            if (smName.isBlank()) continue
            if (!sm.getAttribute("type").equals("ranges", true) && sm.getAttribute("type").isNotBlank()) continue
            val ranges = ArrayList<IntRange>()
            var i = 0
            while (sm.hasAttribute("line$i")) {
                sm.getAttribute("line$i").split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                    val bits = part.split('-').mapNotNull { it.trim().toIntOrNull() }
                    if (bits.isNotEmpty()) {
                        val a = minOf(bits.first(), bits.last()); val b = maxOf(bits.first(), bits.last())
                        if (a >= 1 && b <= count) ranges += (a - 1)..(b - 1)
                    }
                }
                i++
            }
            if (ranges.isNotEmpty()) subs += Submodel(smName, ranges)
        }

        // StartChannel: "!Controller:ch", ">Model:ch" (after another model), "#universe:ch" or a plain channel.
        val start = e.getAttribute("StartChannel").trim()
        var controller: String? = e.getAttribute("Controller").takeIf { it.isNotBlank() && it != "Use Start Channel" }
        var startChannel: Int? = null; var chained: String? = null; var absolute: Int? = null
        when {
            start.startsWith("!") -> { controller = start.substring(1).substringBeforeLast(':'); startChannel = start.substringAfterLast(':').toIntOrNull() }
            start.startsWith(">") -> chained = start.substring(1).substringBeforeLast(':')
            start.startsWith("@") -> chained = start.substring(1).substringBeforeLast(':')
            // "#universe:channel" needs the network setup to place; the import asks instead.
            else -> absolute = start.toIntOrNull()
        }
        if (controller != null && startChannel == null) startChannel = absolute
        return XlModel(name, displayAs, count, model, prop, subs, controller, startChannel, chained, absolute)
    }

    /** A custom grid: "CustomModel" rows split by ';', cells by ',' (layers by '|'), or the compressed "node,row,col[,layer];…" form. */
    private fun customModel(e: Element): PropModel? {
        // Older files give the grid as parm1 × parm2, newer ones as CustomWidth × CustomHeight.
        val w = e.int("CustomWidth", e.int("parm1", 1)).coerceAtLeast(1)
        val h = e.int("CustomHeight", e.int("parm2", 1)).coerceAtLeast(1)
        val firstCell = HashMap<Int, Int>()
        val compressed = e.getAttribute("CustomModelCompressed")
        if (compressed.isNotBlank()) {
            for (entry in compressed.split(';')) {
                val v = entry.split(',').mapNotNull { it.trim().toIntOrNull() }
                if (v.size >= 3) firstCell.putIfAbsent(v[0], v[1] * w + v[2])
            }
        } else {
            val data = e.getAttribute("CustomModel")
            if (data.isBlank()) return null
            for (layer in data.split('|')) {
                layer.split(';').forEachIndexed { row, line ->
                    line.split(',').forEachIndexed { col, cell ->
                        cell.trim().toIntOrNull()?.let { node -> if (row < h && col < w) firstCell.putIfAbsent(node, row * w + col) }
                    }
                }
            }
        }
        val maxNode = firstCell.keys.maxOrNull() ?: return null
        return PropModel(ModelKind.CUSTOM, customW = w, customH = h, customCells = List(maxNode) { firstCell[it + 1] ?: -1 })
    }

    /**
     * Where each model starts: its controller and first LED there (0-based), following chains of
     * models. Models on an absolute channel go on [defaultController].
     */
    fun resolveStarts(models: List<XlModel>, defaultController: String?): Map<String, Pair<String?, Int>> {
        val byName = models.associateBy { it.name }
        val out = HashMap<String, Pair<String?, Int>>()
        fun resolve(m: XlModel, depth: Int = 0): Pair<String?, Int> {
            out[m.name]?.let { return it }
            val r = when {
                m.chainedAfter != null && depth < 50 -> byName[m.chainedAfter]?.let { prev ->
                    val (c, s) = resolve(prev, depth + 1)
                    c to s + prev.ledCount
                } ?: (m.controller to 0)
                m.controller != null -> m.controller to ((m.startChannel ?: 1) - 1) / 3
                else -> defaultController to ((m.absoluteChannel ?: 1) - 1) / 3
            }
            out[m.name] = r
            return r
        }
        models.forEach { resolve(it) }
        return out
    }
}
