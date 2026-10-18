package com.wledcontroller.app.house

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Shapes whose LEDs are laid out from how they're wired, like xLights models. */
@Serializable
enum class ModelKind(val label: String) {
    MATRIX("Matrix"),
    MEGA_TREE("Mega tree"),
    STAR("Star"),
}

@Serializable
enum class StartCorner(val label: String) { TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right") }

/**
 * A prop described by its wiring. It fills the box between the run's first two traced points.
 * Matrix: [strings] strings of [nodes] LEDs, running [vertical]ly from [start], snaking back
 * and forth when [zigzag]. Mega tree: [strings] strings of [nodes] from the top (or the bottom
 * when [fromBottom]), fanned across [degrees] of the tree. Star: [points] points with [nodes] LEDs
 * each, round the outline from the top going clockwise (or not, when [clockwise] is false).
 */
@Serializable
data class PropModel(
    val kind: ModelKind,
    val strings: Int = 16,
    val nodes: Int = 50,
    val start: StartCorner = StartCorner.BOTTOM_LEFT,
    val vertical: Boolean = true,
    val zigzag: Boolean = true,
    val degrees: Int = 180,
    val fromBottom: Boolean = false,
    val points: Int = 5,
    val clockwise: Boolean = true,
    /** Star: inner radius as a fraction of the outer. */
    val innerRatio: Float = 0.45f,
) {
    val ledCount: Int get() = when (kind) {
        ModelKind.MATRIX, ModelKind.MEGA_TREE -> strings.coerceAtLeast(1) * nodes.coerceAtLeast(1)
        ModelKind.STAR -> points.coerceAtLeast(3) * nodes.coerceAtLeast(2)
    }

    /** LED positions inside a box (corners [a] and [b], normalised photo coordinates), in wiring order. */
    fun layout(a: MapPoint, b: MapPoint, aspect: Float): List<MapPoint> {
        val left = minOf(a.x, b.x); val right = maxOf(a.x, b.x)
        val top = minOf(a.y, b.y); val bottom = maxOf(a.y, b.y)
        fun at(fx: Float, fy: Float) = MapPoint(left + (right - left) * fx, top + (bottom - top) * fy)
        return when (kind) {
            ModelKind.MATRIX -> {
                val s = strings.coerceAtLeast(1); val n = nodes.coerceAtLeast(1)
                val fromRight = start == StartCorner.TOP_RIGHT || start == StartCorner.BOTTOM_RIGHT
                val fromBottomEdge = start == StartCorner.BOTTOM_LEFT || start == StartCorner.BOTTOM_RIGHT
                fun frac(i: Int, of: Int) = if (of <= 1) 0.5f else i / (of - 1f)
                List(s * n) { i ->
                    val str = i / n; var node = i % n
                    if (zigzag && str % 2 == 1) node = n - 1 - node
                    if (vertical) {
                        val x = frac(str, s).let { if (fromRight) 1f - it else it }
                        val y = frac(node, n).let { if (fromBottomEdge) 1f - it else it }
                        at(x, y)
                    } else {
                        val y = frac(str, s).let { if (fromBottomEdge) 1f - it else it }
                        val x = frac(node, n).let { if (fromRight) 1f - it else it }
                        at(x, y)
                    }
                }
            }
            ModelKind.MEGA_TREE -> {
                val s = strings.coerceAtLeast(1); val n = nodes.coerceAtLeast(1)
                // Seen from the front: strings fan from the top down to the base. Only the front
                // [degrees] of the tree are spread across the width.
                val spread = (degrees.coerceIn(90, 360) / 360f)
                List(s * n) { i ->
                    val str = i / n; var node = i % n
                    if (zigzag && str % 2 == 1) node = n - 1 - node
                    val down = if (n <= 1) 1f else node / (n - 1f)
                    val f = if (fromBottom) 1f - down else down
                    val angle = (if (s <= 1) 0f else str / (s - 1f) - 0.5f) * spread * 2f * PI.toFloat()
                    // Strings round the back of a full tree land behind the front ones.
                    val x = 0.5f + 0.5f * f * sin(angle)
                    at(x, f)
                }
            }
            ModelKind.STAR -> {
                val p = points.coerceAtLeast(3); val n = nodes.coerceAtLeast(2)
                val cx = 0.5f; val cy = 0.5f
                // Radii in box fractions, so the star stays round whatever the box's shape.
                val boxW = (right - left) * aspect; val boxH = bottom - top
                val r = 0.5f
                val rx = if (boxW > boxH) r * boxH / boxW else r
                val ry = if (boxH > boxW) r * boxW / boxH else r
                val dir = if (clockwise) 1f else -1f
                // Vertices round the outline: even = a point's tip (the first at the top), odd = the
                // inner corner after it.
                fun vertex(k: Int): Pair<Float, Float> {
                    val kk = ((k % (2 * p)) + 2 * p) % (2 * p)
                    val a = (-PI / 2 + dir * kk * PI / p).toFloat()
                    val rr = if (kk % 2 == 0) 1f else innerRatio
                    return (cx + rx * rr * cos(a)) to (cy + ry * rr * sin(a))
                }
                // Each point: inner corner before it → tip → inner corner after, n LEDs along it.
                List(p * n) { i ->
                    val pt = i / n
                    val t = ((i % n) + 0.5f) / n * 2f
                    val (k0, f) = if (t < 1f) (2 * pt - 1) to t else (2 * pt) to (t - 1f)
                    val (x0, y0) = vertex(k0)
                    val (x1, y1) = vertex(k0 + 1)
                    at(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f)
                }
            }
        }
    }

    /** Parts of the prop that shows can light on their own. */
    fun submodels(): List<Submodel> = when (kind) {
        ModelKind.MATRIX -> {
            val s = strings.coerceAtLeast(1); val n = nodes.coerceAtLeast(1)
            List(s) { str -> Submodel("${if (vertical) "Column" else "Row"} ${str + 1}", listOf(str * n until (str + 1) * n)) }
        }
        ModelKind.MEGA_TREE -> {
            val s = strings.coerceAtLeast(1); val n = nodes.coerceAtLeast(1)
            List(s) { str -> Submodel("String ${str + 1}", listOf(str * n until (str + 1) * n)) }
        }
        ModelKind.STAR -> {
            val p = points.coerceAtLeast(3); val n = nodes.coerceAtLeast(2)
            List(p) { pt -> Submodel("Point ${pt + 1}", listOf(pt * n until (pt + 1) * n)) }
        }
    }
}

/** A named part of a run: LED ranges in the run's own order (0 = its first LED). */
@Serializable
data class Submodel(val name: String, val ranges: List<@Serializable(with = IntRangeSerializer::class) IntRange>) {
    fun contains(i: Int) = ranges.any { i in it }
    val size get() = ranges.sumOf { (it.last - it.first + 1).coerceAtLeast(0) }
}

/** Every submodel of a run: its model's own parts, then any the user made. */
fun LightRun.allSubmodels(): List<Submodel> = (model?.submodels().orEmpty()) + submodels

/** Parses "1-10, 15, 20-30" (1-based, as people count LEDs) into 0-based ranges. */
fun parseLedRanges(text: String, count: Int): List<IntRange>? {
    val out = ArrayList<IntRange>()
    for (part in text.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
        val bits = part.split('-').map { it.trim() }
        val a = bits.getOrNull(0)?.toIntOrNull() ?: return null
        val b = if (bits.size > 1) bits[1].toIntOrNull() ?: return null else a
        if (a < 1 || b < a || b > count) return null
        out += (a - 1)..(b - 1)
    }
    return out.takeIf { it.isNotEmpty() }
}

fun formatLedRanges(ranges: List<IntRange>) = ranges.joinToString(", ") { if (it.first == it.last) "${it.first + 1}" else "${it.first + 1}-${it.last + 1}" }

object IntRangeSerializer : kotlinx.serialization.KSerializer<IntRange> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("IntRange", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: IntRange) = encoder.encodeString("${value.first}..${value.last}")
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): IntRange {
        val (a, b) = decoder.decodeString().split("..").map { it.toInt() }
        return a..b
    }
}
