package com.wledcontroller.app.house

import kotlinx.serialization.Serializable
import kotlin.math.hypot

/** A point on the house photo, normalised: x and y run 0..1 across the photo's width and height. */
@Serializable
data class MapPoint(val x: Float, val y: Float)

/**
 * One physical run of lights (a roofline, a window, a bush …) on the photo.
 * Either traced as a line ([points]: the first point is LED [startLed], LEDs spread evenly
 * along it) or placed LED by LED ([ledPoints], from a camera scan or manual editing).
 */
@Serializable
data class LightRun(
    val id: String,
    val name: String,
    val deviceId: String,
    val startLed: Int,
    val count: Int,
    val points: List<MapPoint> = emptyList(),
    /** Exact position of each LED in the run; null = not placed (stays dark). Overrides [points]. */
    val ledPoints: List<MapPoint?>? = null,
    /** What kind of prop this run is; sets the natural direction effects travel in. */
    val prop: PropType = PropType.LINE,
    /**
     * Real distance between LED centres, in cm. When set (and the map has a scale), LEDs are
     * placed this far apart from the start of the traced line instead of spread along it.
     */
    val spacingCm: Float? = null,
    /** Stretches of the traced line the photo can't show (round a corner, behind a post …). */
    val hidden: List<HiddenStretch> = emptyList(),
    /** For runs placed LED by LED: LEDs that are out of sight from the photo. */
    val hiddenLeds: Set<Int> = emptySet(),
    /** A matrix, mega tree or star laid out from its wiring in the box between the first two points. */
    val model: PropModel? = null,
    /** Parts of the run shows can light on their own (as well as the model's own). */
    val submodels: List<Submodel> = emptyList(),
    /** A model's real size (e.g. from its maker), so it can be placed at true scale. */
    val realWidthCm: Float? = null,
    val realHeightCm: Float? = null,
    /** A model shown the other way round (left ↔ right), e.g. the right-hand one of a pair. */
    val mirrored: Boolean = false,
    /** A model shown upside down. */
    val upsideDown: Boolean = false,
    /** How far in front of the house wall it stands (yard props), in cm, for the 3D view. */
    val depthCm: Float = 0f,
    /** Traced on the 3D house model (metres), for lights the photo can't show. */
    val points3d: List<com.wledcontroller.app.house.model3d.P3>? = null,
    /**
     * How far each span of [points3d] bends at its middle, in metres: down (a strand sagging
     * between hooks) or, when negative, up (an arch). One per span; missing ones are straight.
     */
    val bends3d: List<Float>? = null,
    /**
     * How far a strand hung between hooks droops in the middle of each span, as a share of
     * the span's length (0 = pulled tight). The traced points are the hooks.
     */
    val sag: Float = 0f,
    /** Hooks spaced evenly between each pair of traced points, for sag. */
    val hooksBetween: Int = 0,
) {
    val endLed get() = startLed + count
    val isPlacedPerLed get() = ledPoints != null
    val placedCount get() = ledPoints?.count { it != null } ?: if (points.size >= 2 || (count == 1 && points.size == 1)) count else 0
}

/**
 * Part of a traced run that can't be seen in the photo, between traced points [after] and
 * [after] + 1: how long it really is ([lengthCm]) and/or how many LEDs it holds ([leds]). With
 * neither, the straight line between the two points stands in for it.
 */
@Serializable
data class HiddenStretch(val after: Int, val leds: Int? = null, val lengthCm: Float? = null)

@Serializable
data class HouseMap(
    /** Path of the downscaled photo in app storage, or null for a plain dark canvas. */
    val photoPath: String? = null,
    /** Photo width / height. */
    val aspect: Float = 1.5f,
    val runs: List<LightRun> = emptyList(),
    val groups: List<PropGroup> = emptyList(),
    /** Two points on the photo a known real distance apart ([scaleCm]), giving the map its scale. */
    val scaleA: MapPoint? = null,
    val scaleB: MapPoint? = null,
    val scaleCm: Float? = null,
    /** Show lengths in inches and feet rather than centimetres and metres. */
    val useInches: Boolean = true,
    /** The house in 3D, if one has been built or imported. */
    val model3d: com.wledcontroller.app.house.model3d.House3D? = null,
) {
    /** Real centimetres per picture height, or null until a scale is set. */
    val cmPerHeight: Float?
        get() {
            val a = scaleA ?: return null; val b = scaleB ?: return null; val cm = scaleCm ?: return null
            val d = hypot((b.x - a.x) * aspect, b.y - a.y)
            return if (d > 1e-4f && cm > 0f) cm / d else null
        }

    /** Where each LED of [run] is on this map. */
    fun positions(run: LightRun): List<MapPoint?> = run.ledPositions(aspect, cmPerHeight)

    /**
     * Where a newly added prop goes: a box centred near [center], at its real size when it has
     * one and the map a scale, otherwise a fifth of the picture high with the shape of its
     * grid ([gridAspect] = width / height). Kept inside the picture.
     */
    fun propBox(center: MapPoint, widthCm: Float?, heightCm: Float?, gridAspect: Float): List<MapPoint> {
        val real = if (widthCm != null && heightCm != null) boxAround(center, widthCm, heightCm) else null
        val (halfW, halfH) = if (real != null) (real[1].x - real[0].x) / 2f to (real[1].y - real[0].y) / 2f
        else {
            val h = 0.2f
            (h * gridAspect.coerceIn(0.2f, 5f) / aspect / 2f) to h / 2f
        }
        // Keep the box on the picture where it fits; a prop bigger than the picture stays centred.
        val cx = if (halfW < 0.5f) center.x.coerceIn(halfW, 1f - halfW) else 0.5f
        val cy = if (halfH < 0.5f) center.y.coerceIn(halfH, 1f - halfH) else 0.5f
        return listOf(MapPoint(cx - halfW, cy - halfH), MapPoint(cx + halfW, cy + halfH))
    }

    /** The corners of a box [widthCm] × [heightCm] centred on [center], or null without a scale. */
    fun boxAround(center: MapPoint, widthCm: Float, heightCm: Float): List<MapPoint>? {
        val scale = cmPerHeight ?: return null
        val halfW = widthCm / scale / aspect / 2f; val halfH = heightCm / scale / 2f
        return listOf(MapPoint(center.x - halfW, center.y - halfH), MapPoint(center.x + halfW, center.y + halfH))
    }
}

/** A named set of props (e.g. "All arches") that sequences can target together. */
@Serializable
data class PropGroup(val id: String, val name: String, val runIds: List<String>)

/** How an effect's motion is laid out over the lights it covers. */
@Serializable
enum class Direction(val label: String) {
    AUTO("Prop default"),
    ALONG("Along"),
    REVERSE("Reverse"),
    ENDS_IN("Ends → middle"),
    CENTER_OUT("Centre out"),
    LEFT_RIGHT("Left → right"),
    RIGHT_LEFT("Right → left"),
    UP("Bottom → top"),
    DOWN("Top → bottom"),
}

@Serializable
enum class PropType(val label: String, val natural: Direction) {
    LINE("Line / roofline", Direction.ALONG),
    ARCH("Arch", Direction.ALONG),
    TREE("Tree", Direction.UP),
    WINDOW("Window / outline", Direction.ALONG),
    CANDY_CANE("Candy cane", Direction.ALONG),
    STAR("Star", Direction.CENTER_OUT),
    MATRIX("Matrix / panel", Direction.LEFT_RIGHT),
    BUSH("Bush / cluster", Direction.CENTER_OUT),
}

/**
 * A placed LED: which device and physical index, where it is, and its order within its run.
 * [hidden] LEDs are out of sight in the photo: effects still use their estimated position, but
 * previews don't draw them.
 */
data class PlacedLed(
    val deviceId: String, val index: Int, val x: Float, val y: Float, val runIndex: Int, val orderInRun: Int,
    val hidden: Boolean = false,
)

/**
 * Position of every LED in the run (index = order in run); null where an LED isn't placed.
 * [cmPerHeight] is the map's scale, needed for runs with a fixed [LightRun.spacingCm].
 */
fun LightRun.ledPositions(aspect: Float, cmPerHeight: Float? = null): List<MapPoint?> {
    ledPoints?.let { lp -> return List(count) { lp.getOrNull(it) } }
    model?.let { m ->
        if (points.size < 2) return List(count) { null }
        var pos = m.layout(points[0], points[1], aspect)
        if (mirrored || upsideDown) {
            // Turned over inside its own box.
            val l = minOf(points[0].x, points[1].x) + maxOf(points[0].x, points[1].x)
            val t = minOf(points[0].y, points[1].y) + maxOf(points[0].y, points[1].y)
            pos = pos.map { p -> MapPoint(if (mirrored) l - p.x else p.x, if (upsideDown) t - p.y else p.y) }
        }
        return List(count) { pos.getOrNull(it) }
    }
    return tracedPositions(aspect, cmPerHeight)
}

/** Length of the traced line in picture heights. */
fun LightRun.tracedLength(aspect: Float): Float =
    points.zipWithNext { a, b -> hypot((b.x - a.x) * aspect, b.y - a.y) }.sum()

/**
 * LEDs along the traced line: one every [LightRun.spacingCm] from the start when the run has a
 * spacing and the map a scale (continuing straight on past the end if the line is too short),
 * otherwise [LightRun.count] LEDs spread evenly from end to end. Hidden stretches hold their
 * LEDs on the straight line between their two points.
 */
fun LightRun.tracedPositions(aspect: Float, cmPerHeight: Float? = null): List<MapPoint> = tracedLayout(aspect, cmPerHeight).first

/** Which LEDs of the run are out of sight in the photo. */
fun LightRun.hiddenFlags(aspect: Float, cmPerHeight: Float? = null): BooleanArray =
    if (ledPoints != null) BooleanArray(count) { it in hiddenLeds }
    else if (model != null) BooleanArray(count)
    else tracedLayout(aspect, cmPerHeight).second

private fun LightRun.tracedLayout(aspect: Float, cmPerHeight: Float?): Pair<List<MapPoint>, BooleanArray> {
    if (count <= 0 || points.isEmpty()) return emptyList<MapPoint>() to BooleanArray(0)
    // Hung between hooks: lay the lights along the drooping line instead.
    if ((sag > 0f || hooksBetween > 0) && points.size >= 2) return shaped(aspect).tracedLayout(aspect, cmPerHeight)
    if (points.size == 1 || count == 1) return List(count) { points.first() } to BooleanArray(count)
    val segs = points.zipWithNext { a, b -> hypot((b.x - a.x) * aspect, b.y - a.y) }
    val hiddenAt = hidden.filter { it.after in segs.indices }.associateBy { it.after }
    fun at(k: Int, f: Float): MapPoint {
        val a = points[k]; val b = points[k + 1]
        return MapPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }

    val spacing = spacingCm
    if (spacing != null && spacing > 0f && cmPerHeight != null && cmPerHeight > 0f) {
        // Walk the real length of each piece; a hidden stretch is as long as it really is.
        val step = spacing / cmPerHeight
        val lens = segs.mapIndexed { k, len ->
            val h = hiddenAt[k] ?: return@mapIndexed len
            h.lengthCm?.let { it / cmPerHeight } ?: h.leds?.let { it * step } ?: len
        }
        val pos = ArrayList<MapPoint>(count)
        val flags = BooleanArray(count)
        for (i in 0 until count) {
            var d = step * i
            var k = 0
            while (k < lens.size - 1 && d > lens[k]) { d -= lens[k]; k++ }
            // Past the end of the last piece the line carries straight on.
            val f = if (lens[k] > 0) d / lens[k] else 0f
            pos += at(k, f)
            flags[i] = hiddenAt.containsKey(k) && f <= 1f
        }
        return pos to flags
    }

    // Spread evenly: hidden stretches take their LEDs, the rest share the visible line.
    val visibleLen = segs.filterIndexed { k, _ -> !hiddenAt.containsKey(k) }.sum()
    val hiddenCounts = IntArray(segs.size) { k ->
        val h = hiddenAt[k]
        if (h == null) 0
        else h.leds ?: h.lengthCm?.let { cm ->
            // Only a length: estimate how many LEDs fit at the visible spacing.
            val perLedCm = cmPerHeight?.let { visibleLen * it / (count - 1).coerceAtLeast(1) }
            if (perLedCm != null && perLedCm > 0f) Math.round(cm / perLedCm) else null
        } ?: 0
    }
    val visibleCount = (count - hiddenCounts.sum()).coerceAtLeast(1)
    fun target(j: Int) = if (visibleCount <= 1) 0f else visibleLen * j / (visibleCount - 1)
    val pos = ArrayList<MapPoint>(count)
    val flags = ArrayList<Boolean>(count)
    var j = 0
    var c = 0f
    for (k in segs.indices) {
        if (hiddenAt.containsKey(k)) {
            val hc = hiddenCounts[k]
            for (m in 0 until hc) { pos += at(k, (m + 1f) / (hc + 1f)); flags += true }
            continue
        }
        val end = c + segs[k]
        val lastVisible = (k + 1 until segs.size).all { hiddenAt.containsKey(it) }
        while (j < visibleCount && (lastVisible || target(j) <= end + 1e-5f)) {
            val f = if (segs[k] > 0) ((target(j) - c) / segs[k]).coerceIn(0f, 1f) else 0f
            pos += at(k, f); flags += false; j++
        }
        c = end
    }
    while (pos.size < count) { pos += points.last(); flags += false }
    return pos.take(count) to flags.take(count).toBooleanArray()
}

/** The hooks of a run hung between them: the traced points, plus any extra hooks spaced between. */
fun LightRun.hooks(): List<MapPoint> {
    if (hooksBetween <= 0 || points.size < 2) return points
    val hiddenAt = hidden.map { it.after }.toSet()
    val out = ArrayList<MapPoint>()
    points.zipWithNext().forEachIndexed { k, (a, b) ->
        out += a
        if (k !in hiddenAt) for (h in 1..hooksBetween) {
            val f = h / (hooksBetween + 1f)
            out += MapPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
        }
    }
    out += points.last()
    return out
}

/**
 * The run as it really hangs: extra hooks put in, and each span between hooks drooping in
 * the middle by [LightRun.sag] of its length. Out-of-sight stretches stay straight. The result
 * has no sag of its own: its points follow the curve.
 */
fun LightRun.shaped(aspect: Float, steps: Int = 12): LightRun {
    if (points.size < 2 || (sag <= 0f && hooksBetween <= 0)) return copy(sag = 0f, hooksBetween = 0)
    val hiddenAt = hidden.associateBy { it.after }
    val out = ArrayList<MapPoint>()
    val newHidden = ArrayList<HiddenStretch>()
    points.zipWithNext().forEachIndexed { k, (a, b) ->
        val h = hiddenAt[k]
        if (h != null) { newHidden += h.copy(after = out.size); out += a; return@forEachIndexed }
        // This piece's hooks, then the droop between each pair.
        val hooks = (0..hooksBetween).map { j -> val f = j / (hooksBetween + 1f); MapPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f) } + b
        hooks.zipWithNext().forEach { (p, q) ->
            val len = hypot((q.x - p.x) * aspect, q.y - p.y)
            val drop = sag * len
            val n = if (drop > 0f) steps else 1
            for (s in 0 until n) {
                val f = s / n.toFloat()
                out += MapPoint(p.x + (q.x - p.x) * f, p.y + (q.y - p.y) * f + 4f * drop * f * (1f - f))
            }
        }
    }
    out += points.last()
    return copy(points = out, hidden = newHidden, sag = 0f, hooksBetween = 0)
}

/** Fine-tuning a traced line. */
object LineTools {
    /** All the points moved onto the one straight line that fits them best, in the same order. */
    fun straighten(points: List<MapPoint>, aspect: Float): List<MapPoint> {
        if (points.size < 2) return points
        // In real proportions, so "straight" means straight on the house.
        val xs = points.map { it.x * aspect }; val ys = points.map { it.y }
        val mx = xs.average(); val my = ys.average()
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in points.indices) { val dx = xs[i] - mx; val dy = ys[i] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        val angle = 0.5 * kotlin.math.atan2(2 * sxy, sxx - syy)
        val ux = kotlin.math.cos(angle); val uy = kotlin.math.sin(angle)
        return points.indices.map { i ->
            val t = (xs[i] - mx) * ux + (ys[i] - my) * uy
            MapPoint(((mx + ux * t) / aspect).toFloat(), (my + uy * t).toFloat())
        }
    }

    /** The piece from point [k] to the next made exactly level (or plumb, if it's nearer upright). */
    fun level(points: List<MapPoint>, k: Int, aspect: Float): List<MapPoint> {
        if (k !in 0 until points.size - 1) return points
        val a = points[k]; val b = points[k + 1]
        val out = points.toMutableList()
        if (kotlin.math.abs((b.x - a.x) * aspect) >= kotlin.math.abs(b.y - a.y)) {
            val y = (a.y + b.y) / 2; out[k] = a.copy(y = y); out[k + 1] = b.copy(y = y)
        } else {
            val x = (a.x + b.x) / 2; out[k] = a.copy(x = x); out[k + 1] = b.copy(x = x)
        }
        return out
    }

    /** Hidden stretches after point [k] is removed: the stretches either side of it go, later ones move up. */
    fun hiddenAfterRemoving(hidden: List<HiddenStretch>, k: Int): List<HiddenStretch> =
        hidden.filter { it.after != k && it.after != k - 1 }.map { if (it.after > k) it.copy(after = it.after - 1) else it }

    /** Hidden stretches after a point is put in after point [k]. */
    fun hiddenAfterInserting(hidden: List<HiddenStretch>, k: Int): List<HiddenStretch> =
        hidden.map { if (it.after > k) it.copy(after = it.after + 1) else it }
}

/** A length for people: feet and inches, or centimetres and metres. */
fun formatLength(cm: Float, inches: Boolean): String {
    if (!inches) return if (cm >= 100f) "%.2f m".format(cm / 100f) else "%.1f cm".format(cm).replace(".0 ", " ")
    val totalIn = cm / 2.54f
    if (totalIn < 12f) return (if (totalIn < 10f) "%.1f in".format(totalIn) else "%.0f in".format(totalIn)).replace(".0 ", " ")
    val ft = (totalIn / 12f).toInt()
    val rest = Math.round(totalIn - ft * 12f)
    return if (rest == 0) "$ft ft" else if (rest == 12) "${ft + 1} ft" else "$ft ft $rest in"
}

fun HouseMap.placedLeds(): List<PlacedLed> = runs.flatMapIndexed { r, run ->
    val flags = run.hiddenFlags(aspect, cmPerHeight)
    var pos = positions(run)
    // Hidden LEDs placed one by one have no spot: estimate one between their neighbours.
    if (run.ledPoints != null && run.hiddenLeds.isNotEmpty()) {
        val filled = pos.fillGaps()
        pos = pos.mapIndexed { i, p -> p ?: if (i in run.hiddenLeds) filled[i] else null }
    }
    pos.mapIndexedNotNull { i, p -> p?.let { PlacedLed(run.deviceId, run.startLed + i, it.x, it.y, r, i, flags.getOrElse(i) { false }) } }
}

/** Fills unplaced LEDs by interpolating between the nearest placed LEDs on either side. */
fun List<MapPoint?>.fillGaps(): List<MapPoint?> {
    val placed = indices.filter { this[it] != null }
    if (placed.size < 2) return this
    return List(size) { i ->
        this[i] ?: run {
            val before = placed.lastOrNull { it < i }
            val after = placed.firstOrNull { it > i }
            when {
                before != null && after != null -> {
                    val a = this[before]!!; val b = this[after]!!
                    val f = (i - before).toFloat() / (after - before)
                    MapPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
                }
                else -> null // Outside the detected span: leave for the user to place.
            }
        }
    }
}

/** The middle of what the run was placed with (its box, traced line or LEDs), or null if it isn't placed. */
fun LightRun.pivot(): MapPoint? {
    val pts = ledPoints?.filterNotNull() ?: points
    if (pts.isEmpty()) return null
    return MapPoint((pts.minOf { it.x } + pts.maxOf { it.x }) / 2f, (pts.minOf { it.y } + pts.maxOf { it.y }) / 2f)
}

/**
 * The run moved by [dx], [dy] (fractions of the picture) and scaled by [zoom] about [pivot],
 * keeping its shape. A model's real size scales with it.
 */
fun LightRun.transformed(dx: Float, dy: Float, zoom: Float = 1f, pivot: MapPoint = this.pivot() ?: MapPoint(0.5f, 0.5f)): LightRun {
    fun t(p: MapPoint) = MapPoint(pivot.x + (p.x - pivot.x) * zoom + dx, pivot.y + (p.y - pivot.y) * zoom + dy)
    return copy(
        points = points.map(::t),
        ledPoints = ledPoints?.map { it?.let(::t) },
        realWidthCm = realWidthCm?.times(zoom),
        realHeightCm = realHeightCm?.times(zoom),
    )
}

/** "Left web" → "Right web" (and back); names without a side get "(mirrored)" added or taken off. */
fun mirroredName(name: String): String {
    val side = Regex("\\b(left|right)\\b", RegexOption.IGNORE_CASE)
    if (side.containsMatchIn(name)) return side.replace(name) { m ->
        val other = if (m.value.equals("left", true)) "right" else "left"
        when {
            m.value.all { it.isUpperCase() } -> other.uppercase()
            m.value.first().isUpperCase() -> other.replaceFirstChar(Char::uppercase)
            else -> other
        }
    }
    return if (name.endsWith(" (mirrored)")) name.removeSuffix(" (mirrored)") else "$name (mirrored)"
}

/**
 * The other one of a pair: the same prop facing the other way, under a new [id], set just
 * beside this one (to the right if there's room, else to the left).
 */
fun LightRun.mirroredCopy(id: String): LightRun {
    val moved = if (points.size >= 2) {
        val left = points.minOf { it.x }; val right = points.maxOf { it.x }
        val step = (right - left) * 1.15f
        val dx = if (right + step <= 1f) step else -step
        points.map { MapPoint(it.x + dx, it.y) }
    } else points
    return copy(id = id, name = mirroredName(name).take(40), mirrored = !mirrored, points = moved, ledPoints = null)
}
