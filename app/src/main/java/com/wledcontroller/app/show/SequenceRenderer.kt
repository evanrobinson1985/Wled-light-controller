package com.wledcontroller.app.show

import com.wledcontroller.app.house.Direction
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.HouseRenderer
import com.wledcontroller.app.house.PlacedLed
import com.wledcontroller.app.house.allSubmodels
import com.wledcontroller.app.house.placedLeds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Everything about the house layout a sequence needs, precomputed once per map/track set:
 * every placed LED, and for each track the LEDs it covers with coordinates relative to it.
 */
class PreparedLayout(
    val leds: List<PlacedLed>,
    /** Per track (same order as the sequence): indices into [leds] and their effect coordinates. */
    val tracks: List<TrackLayout>,
)

class TrackLayout(
    val ledIndex: IntArray,
    /** Position along the LED's own prop, 0..1. */
    val along: FloatArray,
    /** Position within the track target's bounding box, 0..1. */
    val lx: FloatArray,
    val ly: FloatArray,
    /** Direction used for [Direction.AUTO]: the LED's prop default. */
    val natural: Array<Direction>,
    /** Width / height of the area the track covers, in real proportions (for text and pictures). */
    val aspect: Float = 1f,
    /** For singing faces: which part each LED is (see [PropModel.facePart]), or -1. */
    val part: IntArray = IntArray(0),
)

object SequenceRenderer {

    fun prepare(map: HouseMap, tracks: List<Track>): PreparedLayout {
        val leds = map.placedLeds()
        val runs = map.runs
        val runCounts = runs.map { it.count }
        val trackLayouts = tracks.map { track ->
            val sub = if (track.target.kind == TrackTarget.Kind.SUBMODEL) {
                val runId = track.target.refId.substringBefore('/'); val name = track.target.refId.substringAfter('/')
                runs.indexOfFirst { it.id == runId }.takeIf { it >= 0 }?.let { r -> r to runs[r].allSubmodels().firstOrNull { it.name == name } }
            } else null
            val runIdxs: Set<Int> = when (track.target.kind) {
                TrackTarget.Kind.HOUSE -> runs.indices.toSet()
                TrackTarget.Kind.PROP -> runs.indices.filter { runs[it].id == track.target.refId }.toSet()
                TrackTarget.Kind.GROUP -> {
                    val ids = map.groups.firstOrNull { it.id == track.target.refId }?.runIds?.toSet() ?: emptySet()
                    runs.indices.filter { runs[it].id in ids }.toSet()
                }
                TrackTarget.Kind.SUBMODEL -> setOfNotNull(sub?.first)
            }
            val subModel = sub?.second
            val idx = leds.indices.filter { leds[it].runIndex in runIdxs && (sub == null || subModel?.contains(leds[it].orderInRun) == true) }
                .let { list -> if (sub != null) list.sortedBy { leds[it].orderInRun } else list }
                .toIntArray()
            val xs = FloatArray(idx.size) { leds[idx[it]].x * map.aspect }
            val ys = FloatArray(idx.size) { leds[idx[it]].y }
            val minX = xs.minOrNull() ?: 0f; val maxX = xs.maxOrNull() ?: 1f
            val minY = ys.minOrNull() ?: 0f; val maxY = ys.maxOrNull() ?: 1f
            val sx = (maxX - minX).takeIf { it > 1e-4f }; val sy = (maxY - minY).takeIf { it > 1e-4f }
            TrackLayout(
                ledIndex = idx,
                along = FloatArray(idx.size) {
                    // A submodel runs along its own LEDs; anything else along its whole prop.
                    if (sub != null) return@FloatArray if (idx.size <= 1) 0f else it / (idx.size - 1f)
                    val l = leds[idx[it]]
                    val n = runCounts[l.runIndex]
                    if (n <= 1) 0f else l.orderInRun / (n - 1f)
                },
                lx = FloatArray(idx.size) { i -> sx?.let { (xs[i] - minX) / it } ?: 0.5f },
                // Flip so 0 is the bottom: "up" effects read naturally.
                ly = FloatArray(idx.size) { i -> sy?.let { 1f - (ys[i] - minY) / it } ?: 0.5f },
                natural = Array(idx.size) { runs[leds[idx[it]].runIndex].prop.natural },
                aspect = if (sx != null && sy != null) sx / sy else if (sx != null) 8f else 1f,
                part = IntArray(idx.size) { i ->
                    val l = leds[idx[i]]
                    runs[l.runIndex].model?.facePart(l.orderInRun) ?: -1
                },
            )
        }
        return PreparedLayout(leds, trackLayouts)
    }

    /**
     * Colours (0xRRGGBB) of every LED in [layout] at [timeMs]. Tracks are layered from the bottom
     * of the list up, each mixing in with its [BlendMode]. [audio] drives the music effects.
     */
    fun render(
        seq: Sequence,
        layout: PreparedLayout,
        timeMs: Long,
        out: IntArray = IntArray(layout.leds.size),
        audio: AudioEnvelope? = null,
        /** When given (2 per LED), filled with where moving heads point: pan, tilt (0..1). */
        motion: FloatArray? = null,
    ): IntArray {
        out.fill(0)
        motion?.fill(0.5f)
        val level = audio?.at(timeMs)?.coerceIn(0f, 1f) ?: 0f
        val beat = audio?.let { beatStrength(it, timeMs) } ?: 0f
        for (ti in seq.tracks.indices.reversed()) {
            val track = seq.tracks[ti]
            if (track.muted) continue
            val tl = layout.tracks.getOrNull(ti) ?: continue
            // The latest-starting clip that covers this moment wins within a track.
            val stored = track.clips.lastOrNull { timeMs >= it.startMs && timeMs < it.endMs } ?: continue
            val local = timeMs - stored.startMs
            val progress = local.toFloat() / stored.durationMs.coerceAtLeast(1)
            val intensity = stored.intensityCurveOrRamp?.at(stored.intensity, progress) ?: stored.intensity
            // Speed uses its average so far, so motion stays continuous as the speed changes.
            val speed = stored.speedCurveOrRamp?.let { averageOf(it, stored.speed, progress) } ?: stored.speed
            val size = stored.sizeCurve?.at(stored.size, progress) ?: stored.size
            val clip = if (speed != stored.speed || size != stored.size) stored.copy(speed = speed, size = size) else stored
            if (motion != null && (stored.pan != null || stored.tilt != null)) {
                val pan = stored.pan?.let { stored.panCurve?.at(it, progress) ?: it }
                val tilt = stored.tilt?.let { stored.tiltCurve?.at(it, progress) ?: it }
                for (li in tl.ledIndex) {
                    if (pan != null) motion[li * 2] = pan
                    if (tilt != null) motion[li * 2 + 1] = tilt
                }
            }
            if (intensity <= 0f) continue
            val t = local / 1000f
            // Layer settings: turned (and spinning), zoomed and mirrored.
            val layered = clip.hasLayerSettings
            val angle = (clip.rotation + clip.spin * progress) * 2f * PI.toFloat()
            val zoom = (clip.zoomEnd?.let { clip.zoom + (it - clip.zoom) * progress } ?: clip.zoom).coerceAtLeast(0.05f)
            val cosA = cos(angle); val sinA = sin(angle)
            val turned = angle != 0f || zoom != 1f
            val sparkleStep = floor(t * 12f).toInt()
            for (k in tl.ledIndex.indices) {
                val li = tl.ledIndex[k]
                var lx = tl.lx[k]; var ly = tl.ly[k]; var along = tl.along[k]
                if (layered) {
                    if (clip.mirrorX) { lx = 1f - lx; along = 1f - along }
                    if (clip.mirrorY) ly = 1f - ly
                    if (turned) {
                        val dx = (lx - 0.5f) * tl.aspect; val dy = ly - 0.5f
                        lx = (dx * cosA - dy * sinA) / zoom / tl.aspect + 0.5f
                        ly = (dx * sinA + dy * cosA) / zoom + 0.5f
                        along = (along - 0.5f) / zoom + 0.5f
                    }
                }
                val dir = if (clip.direction == Direction.AUTO) tl.natural[k] else clip.direction
                val pos = position(dir, along, lx, ly)
                val reveal = transition(clip, local, pos, lx, ly, li)
                var color = if (reveal <= 0f) 0 else effectColor(clip, pos, lx, ly, t, progress, li, tl.aspect, level, beat, tl.part.getOrElse(k) { -1 })
                if (layered && reveal > 0f) {
                    if (clip.hueShift != 0f && color != 0) color = hueShift(color, clip.hueShift)
                    if (clip.sparkles > 0f && HouseRenderer.hash(li, sparkleStep) > 1f - clip.sparkles * 0.2f) color = 0xFFFFFF
                }
                val c = if (reveal <= 0f) 0 else HouseRenderer.scale(color, intensity * reveal)
                out[li] = mix(track.blend, out[li], c)
            }
        }
        return out
    }

    /** [rgb] turned [shift] (0..1) of the way round the colour wheel. */
    internal fun hueShift(rgb: Int, shift: Float): Int {
        val r = ((rgb shr 16) and 0xFF) / 255f; val g = ((rgb shr 8) and 0xFF) / 255f; val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        if (d <= 1e-4f) return rgb
        var h = when (max) {
            r -> ((g - b) / d).let { if (it < 0) it + 6f else it }
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
        h = HouseRenderer.frac(h + shift)
        val s = d / max; val v = max
        val i = floor(h * 6f).toInt(); val f = h * 6f - i
        val p = v * (1 - s); val q = v * (1 - s * f); val tt = v * (1 - s * (1 - f))
        val (rr, gg, bb) = when (i % 6) {
            0 -> Triple(v, tt, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, tt)
            3 -> Triple(p, q, v); 4 -> Triple(tt, p, v); else -> Triple(v, p, q)
        }
        return ((rr * 255).toInt() shl 16) or ((gg * 255).toInt() shl 8) or (bb * 255).toInt()
    }

    /** 0 → 1 → 0 as [v] goes from 0 to 1, repeating: something bouncing between two walls. */
    private fun bounce(v: Float) = 1f - abs(1f - 2f * HouseRenderer.frac(v))

    /** Distance from (px, py) to the segment (ax, ay)–(bx, by). */
    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        val k = if (l2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + k * dx), py - (ay + k * dy))
    }

    /**
     * Conway's Game of Life on a small grid, for the Life effect: generation [gen] of a board
     * seeded by [seed]. The last board asked for is kept, so a frame computes it once; it starts
     * afresh every 64 generations so it never dies out for long.
     */
    private object Life {
        private class Board(val key: Long, var gen: Int, var cells: BooleanArray)
        private val boards = HashMap<Long, Board>()

        fun alive(seed: Int, w: Int, h: Int, density: Float, gen: Int, x: Int, y: Int): Boolean = synchronized(this) {
            val era = gen / 64; val g = gen % 64
            val key = (seed.toLong() shl 32) xor (era.toLong() shl 20) xor (w.toLong() shl 10) xor h.toLong()
            var b = boards[key]
            if (b == null || b.gen > g) {
                b = Board(key, 0, BooleanArray(w * h) { HouseRenderer.hash(seed + era * 7919, it) < density })
                if (boards.size > 16) boards.clear()
                boards[key] = b
            }
            while (b.gen < g) {
                val c = b.cells
                b.cells = BooleanArray(w * h) { i ->
                    val cx = i % w; val cy = i / w
                    var n = 0
                    for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) {
                        if (c[((cy + dy).mod(h)) * w + (cx + dx).mod(w)]) n++
                    }
                    n == 3 || (n == 2 && c[i])
                }
                b.gen++
            }
            b.cells[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        }
    }

    private const val FACE_EYES = 0
    private const val FACE_CLOSED = 1
    private const val FACE_OPEN = 2
    private const val FACE_WIDE = 3

    /** Smooth 2D value noise, 0..1. */
    private fun noise(x: Float, y: Float): Float {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        val fx = x - ix; val fy = y - iy
        fun s(v: Float) = v * v * (3 - 2 * v)
        fun h(a: Int, b: Int) = HouseRenderer.hash(a * 73856093 xor b * 19349663, 31)
        val top = h(ix, iy) + (h(ix + 1, iy) - h(ix, iy)) * s(fx)
        val bottom = h(ix, iy + 1) + (h(ix + 1, iy + 1) - h(ix, iy + 1)) * s(fx)
        return top + (bottom - top) * s(fy)
    }

    /** Black → red → orange → yellow → white. */
    private fun fireColor(h: Float): Int {
        val v = h.coerceIn(0f, 1f) * 3f
        return when {
            v < 1f -> ((v * 255).toInt() shl 16)
            v < 2f -> (255 shl 16) or (((v - 1f) * 200).toInt() shl 8)
            else -> (255 shl 16) or ((200 + (v - 2f) * 55).toInt() shl 8) or ((v - 2f) * 160).toInt()
        }
    }

    /** Mean of a curve from the start to [progress] (16-step trapezoid). */
    private fun averageOf(curve: ValueCurve, start: Float, progress: Float): Float {
        if (progress <= 0.001f) return start
        val n = 16
        var sum = 0f
        for (i in 0..n) {
            val w = if (i == 0 || i == n) 0.5f else 1f
            sum += w * curve.at(start, progress * i / n)
        }
        return sum / n
    }

    /** How strongly a beat is hitting: loudness jumping above the last moment's average. */
    private fun beatStrength(env: AudioEnvelope, timeMs: Long): Float {
        val now = env.at(timeMs)
        var avg = 0f
        for (i in 1..10) avg += env.at((timeMs - i * 40L).coerceAtLeast(0))
        avg /= 10f
        return ((now - avg) * 5f).coerceIn(0f, 1f)
    }

    /** How much of the effect shows at an LED while it comes in or goes out. */
    private fun transition(clip: Clip, local: Long, pos: Float, lx: Float, ly: Float, id: Int): Float {
        var e = 1f
        if (clip.fadeInMs > 0 && local < clip.fadeInMs) e = reveal(clip.inTransition, local.toFloat() / clip.fadeInMs, pos, lx, ly, id)
        val remaining = clip.durationMs - local
        if (clip.fadeOutMs > 0 && remaining < clip.fadeOutMs) e = minOf(e, reveal(clip.outTransition, remaining.toFloat() / clip.fadeOutMs, pos, lx, ly, id))
        return e.coerceIn(0f, 1f)
    }

    private fun reveal(tr: Transition, p: Float, pos: Float, lx: Float, ly: Float, id: Int): Float {
        fun edge(d: Float) = ((p * 1.15f - d) / 0.15f).coerceIn(0f, 1f)
        return when (tr) {
            Transition.FADE -> p
            Transition.WIPE -> edge(pos)
            Transition.FROM_MIDDLE -> edge(abs(pos - 0.5f) * 2f)
            Transition.FROM_ENDS -> edge(1f - abs(pos - 0.5f) * 2f)
            Transition.CIRCLE -> edge((hypot(lx - 0.5f, ly - 0.5f) * 1.4142f).coerceAtMost(1f))
            Transition.DISSOLVE -> if (HouseRenderer.hash(id, 23) < p) 1f else 0f
            Transition.BLINDS -> edge(HouseRenderer.frac(pos * 6f))
        }
    }

    /** One LED of a track mixed onto what the tracks below made. */
    internal fun mix(mode: BlendMode, below: Int, top: Int): Int {
        fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF
        fun rgb(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        val a = maxOf(ch(top, 16), ch(top, 8), ch(top, 0)) / 255f
        return when (mode) {
            BlendMode.MAX -> HouseRenderer.maxRgb(below, top)
            BlendMode.NORMAL -> rgb(
                (ch(below, 16) + (ch(top, 16) - ch(below, 16)) * a).toInt(),
                (ch(below, 8) + (ch(top, 8) - ch(below, 8)) * a).toInt(),
                (ch(below, 0) + (ch(top, 0) - ch(below, 0)) * a).toInt(),
            )
            BlendMode.ADD -> rgb(ch(below, 16) + ch(top, 16), ch(below, 8) + ch(top, 8), ch(below, 0) + ch(top, 0))
            BlendMode.SUBTRACT -> rgb(ch(below, 16) - ch(top, 16), ch(below, 8) - ch(top, 8), ch(below, 0) - ch(top, 0))
            BlendMode.AVERAGE -> rgb((ch(below, 16) + ch(top, 16)) / 2, (ch(below, 8) + ch(top, 8)) / 2, (ch(below, 0) + ch(top, 0)) / 2)
            BlendMode.MASK -> HouseRenderer.scale(below, 1f - a)
            BlendMode.REVEAL -> HouseRenderer.scale(below, a)
        }
    }

    private fun position(dir: Direction, along: Float, lx: Float, ly: Float): Float = when (dir) {
        Direction.AUTO, Direction.ALONG -> along
        Direction.REVERSE -> 1f - along
        Direction.ENDS_IN -> 1f - abs(2f * along - 1f)
        Direction.CENTER_OUT -> (hypot(lx - 0.5f, ly - 0.5f) * 1.4142f).coerceAtMost(1f)
        Direction.LEFT_RIGHT -> lx
        Direction.RIGHT_LEFT -> 1f - lx
        Direction.UP -> ly
        Direction.DOWN -> 1f - ly
    }

    private fun effectColor(
        c: Clip, pos: Float, lx: Float, ly: Float, t: Float, progress: Float, id: Int,
        aspect: Float = 1f, level: Float = 0f, beat: Float = 0f, part: Int = -1,
    ): Int {
        val cols = c.colors.ifEmpty { listOf(0xFFFFFF) }
        val rate = 0.1f + c.speed * 2.9f         // cycles per second-ish
        val count = 1f + (1f - c.size) * 7f      // bands across the target
        return when (c.effect) {
            SeqEffect.ON -> cols[0]
            SeqEffect.WIPE -> if (pos <= progress * 1.02f) HouseRenderer.palette(cols, pos * (cols.size - 1f) / cols.size) else 0
            SeqEffect.CHASE -> {
                val band = floor(pos * count * 2f - t * rate * 2f).toInt()
                cols[band.mod(cols.size)]
            }
            SeqEffect.COLOR_WAVE -> HouseRenderer.palette(cols, pos * count * 0.5f - t * rate * 0.5f)
            SeqEffect.RAINBOW -> HouseRenderer.hsv(pos * count * 0.25f - t * rate * 0.3f)
            SeqEffect.COMET -> {
                val head = HouseRenderer.frac(t * rate * 0.5f) * 1.3f
                val d = head - pos
                val tail = 0.05f + c.size * 0.45f
                if (d < 0 || d > tail) 0 else HouseRenderer.scale(cols[0], 1f - d / tail)
            }
            SeqEffect.BARS -> {
                val band = floor(pos * count - t * rate * 0.5f * (if (c.speed < 0.02f) 0f else 1f)).toInt()
                cols[band.mod(cols.size)]
            }
            SeqEffect.PULSE -> HouseRenderer.scale(cols[0], 0.5f + 0.5f * sin(t * rate * 2f * PI.toFloat()))
            SeqEffect.TWINKLE -> {
                val r = 0.3f + HouseRenderer.hash(id, 7) * 0.7f
                val ph = t * rate * r + HouseRenderer.hash(id, 3) * 10f
                val cyc = floor(ph).toInt()
                val g = sin(HouseRenderer.frac(ph) * PI.toFloat()).let { it * it }
                HouseRenderer.scale(cols[(HouseRenderer.hash(id, cyc) * cols.size).toInt().coerceAtMost(cols.size - 1)], 0.06f + g * 0.94f)
            }
            SeqEffect.SPARKLE -> {
                val step = floor(t * (5f + rate * 10f)).toInt()
                if (HouseRenderer.hash(id, step) > 0.93f - c.size * 0.15f) 0xFFFFFF else HouseRenderer.scale(cols[0], 0.6f)
            }
            SeqEffect.SHIMMER -> {
                val step = floor(t * (8f + rate * 20f)).toInt()
                if ((step + id) % 2 == 0) cols[0] else HouseRenderer.scale(cols[0], 0.15f)
            }
            SeqEffect.STROBE -> if (HouseRenderer.frac(t * (2f + rate * 4f)) < 0.15f) cols[0] else 0
            SeqEffect.FIREWORKS -> {
                var acc = 0
                val slot = 1.2f / (0.2f + rate)
                val now = floor(t / slot).toInt()
                for (k in now - 2..now) {
                    val age = t - k * slot
                    if (age < 0) continue
                    val cx = HouseRenderer.hash(k, 11); val cy = 0.4f + HouseRenderer.hash(k, 13) * 0.6f
                    val r = age * 0.4f * (0.2f + rate)
                    val d = hypot(lx - cx, ly - cy)
                    val ring = exp(-((d - r) * (d - r)) / 0.002f) * (1f - age / (slot * 3f)).coerceAtLeast(0f)
                    acc = HouseRenderer.maxRgb(acc, HouseRenderer.scale(cols[(HouseRenderer.hash(k, 17) * cols.size).toInt().coerceAtMost(cols.size - 1)], ring))
                }
                acc
            }
            SeqEffect.SNOW -> {
                val lane = floor(lx * 40f).toInt()
                val fall = HouseRenderer.frac((1f - ly) * 0.8f - t * rate * (0.15f + HouseRenderer.hash(lane, 5) * 0.15f) + HouseRenderer.hash(lane, 9))
                val flake = if (fall < 0.06f) 1f - fall / 0.06f else 0f
                HouseRenderer.maxRgb(HouseRenderer.scale(cols.getOrElse(1) { cols[0] }, 0.1f), HouseRenderer.scale(cols[0], flake))
            }
            SeqEffect.SPIRALS -> {
                val band = HouseRenderer.frac(lx * count * 0.5f + ly * (0.5f + c.size * 2f) - t * rate * 0.3f)
                cols[(band * cols.size).toInt().coerceAtMost(cols.size - 1)]
            }
            SeqEffect.BUTTERFLY -> {
                val v = sin(lx * 9f + t * rate) + sin(ly * 7f - t * rate * 1.3f) + sin((lx + ly) * 6f + t * rate * 0.7f)
                HouseRenderer.palette(cols, v / 6f + 0.5f + t * 0.05f)
            }
            SeqEffect.METEORS -> {
                val lanes = 10f + (1f - c.size) * 30f
                val lane = floor(lx * lanes).toInt()
                val head = HouseRenderer.frac(t * rate * (0.15f + HouseRenderer.hash(lane, 5) * 0.25f) + HouseRenderer.hash(lane, 9)) * 1.3f
                val d = head - (1f - ly)
                val tail = 0.08f + c.size * 0.3f
                if (d < 0 || d > tail) 0 else HouseRenderer.scale(cols[lane.mod(cols.size)], 1f - d / tail)
            }
            SeqEffect.TEXT -> {
                val r = Rasters.text(c.text ?: "") ?: return 0
                val h = 0.4f + c.size * 0.6f
                val w = r.width.toFloat() / r.height * h / aspect.coerceAtLeast(0.05f)
                val left = if (c.speed < 0.02f) (1f - w) / 2f else 1f - HouseRenderer.frac(t * rate * 0.12f / (1f + w)) * (1f + w)
                val px = r.sample((lx - left) / w, ((1f - ly) - (1f - h) / 2f) / h)
                val alpha = ((px ushr 24) and 0xFF) / 255f
                if (alpha <= 0f) 0 else HouseRenderer.scale(HouseRenderer.palette(cols, lx * 0.999f), alpha)
            }
            SeqEffect.PICTURE -> {
                val r = c.imageFile?.let { Rasters.image(it) } ?: return 0
                val imgAspect = r.width.toFloat() / r.height
                // Fit the whole picture in the area; scroll it across when there's speed.
                val (w, h) = if (imgAspect > aspect) 1f to aspect / imgAspect else imgAspect / aspect to 1f
                val left = if (c.speed < 0.02f) (1f - w) / 2f else 1f - HouseRenderer.frac(t * rate * 0.12f / (1f + w)) * (1f + w)
                val px = r.sample((lx - left) / w, ((1f - ly) - (1f - h) / 2f) / h)
                HouseRenderer.scale(px and 0xFFFFFF, ((px ushr 24) and 0xFF) / 255f)
            }
            SeqEffect.VU_METER -> {
                val l = level.toDouble().pow(0.8).toFloat()
                if (l > 0.01f && pos <= l) HouseRenderer.palette(cols, pos * 0.999f) else 0
            }
            SeqEffect.MUSIC_PULSE -> HouseRenderer.scale(HouseRenderer.palette(cols, pos * count * 0.2f + t * 0.05f), level)
            SeqEffect.BEAT_FLASH -> HouseRenderer.scale(cols[floor(t * 2f).toInt().mod(cols.size)], beat)
            SeqEffect.FIRE -> {
                // Flames rise from the bottom; size sets how tall they reach.
                val heat = (noise(lx * aspect * 9f, ly * 3f - t * (1f + rate * 2f)) * (1.25f - ly * (1.6f - c.size))).coerceIn(0f, 1f)
                if (c.colors.size >= 2) HouseRenderer.palette(cols, heat * 0.999f) else fireColor(heat)
            }
            SeqEffect.RIPPLE -> {
                val d = hypot((lx - 0.5f) * aspect, ly - 0.5f)
                val wave = 0.5f + 0.5f * sin((d * count * 4f - t * rate * 3f) * 2f * PI.toFloat())
                HouseRenderer.scale(HouseRenderer.palette(cols, d), wave * wave)
            }
            SeqEffect.SHOCKWAVE -> {
                // One ring per beat of the effect, bursting out from the middle.
                val period = 0.4f + (1f - c.speed) * 2f
                val age = HouseRenderer.frac(t / period)
                val d = hypot((lx - 0.5f) * aspect, ly - 0.5f) / (0.5f * maxOf(aspect, 1f) * 1.4142f)
                val ring = exp(-((d - age) * (d - age)) / (0.002f + c.size * 0.01f)) * (1f - age)
                HouseRenderer.scale(cols[floor(t / period).toInt().mod(cols.size)], ring)
            }
            SeqEffect.CURTAIN -> {
                // Opens from the middle over the effect; the edge is soft.
                val open = progress * 1.1f
                val d = abs(lx - 0.5f) * 2f
                val f = ((open - d) / 0.1f).coerceIn(0f, 1f)
                HouseRenderer.scale(HouseRenderer.palette(cols, ly), f)
            }
            SeqEffect.PINWHEEL -> {
                val arms = 2 + ((1f - c.size) * 6).toInt()
                val a = atan2(ly - 0.5f, (lx - 0.5f) * aspect) / (2f * PI.toFloat())
                val v = HouseRenderer.frac(a * arms + t * rate * 0.4f)
                cols[(v * cols.size).toInt().coerceAtMost(cols.size - 1)]
            }
            SeqEffect.GALAXY -> {
                val dx = (lx - 0.5f) * aspect; val dy = ly - 0.5f
                val r = hypot(dx, dy)
                val a = atan2(dy, dx) / (2f * PI.toFloat())
                val v = HouseRenderer.frac(a * 2f + r * (2f + c.size * 6f) - t * rate * 0.3f)
                HouseRenderer.scale(HouseRenderer.palette(cols, r * 1.5f), (1f - abs(v * 2f - 1f)).let { it * it })
            }
            SeqEffect.PLASMA -> {
                val v = sin(lx * aspect * 6f + t * rate) + sin(ly * 6f + t * rate * 0.8f) +
                    sin(hypot(lx * aspect * 6f - 3f, ly * 6f - 3f) - t * rate * 1.3f)
                HouseRenderer.palette(cols, v / 6f + 0.5f)
            }
            SeqEffect.MARQUEE -> {
                val w = 2f + c.size * 6f
                val band = floor(pos * 60f / w - t * rate * 4f).toInt()
                if (band.mod(3) == 0) 0 else cols[band.mod(cols.size)]
            }
            SeqEffect.GARLANDS -> {
                // Swags along the prop: bright where each garland hangs low, dim at its hooks.
                val swags = 2f + (1f - c.size) * 6f
                val sag = 0.5f - 0.5f * cos(HouseRenderer.frac(pos * swags + t * rate * 0.1f) * 2f * PI.toFloat())
                HouseRenderer.scale(HouseRenderer.palette(cols, pos), 0.15f + 0.85f * sag * sag)
            }
            SeqEffect.SING -> {
                // On a face: eyes stay lit; the mouth opens with the song. Elsewhere: pulse with it.
                when (part) {
                    FACE_EYES -> cols[0]
                    FACE_CLOSED -> if (level < 0.15f) cols.getOrElse(1) { cols[0] } else 0
                    FACE_OPEN -> if (level in 0.15f..0.45f) cols.getOrElse(1) { cols[0] } else 0
                    FACE_WIDE -> if (level > 0.45f) cols.getOrElse(1) { cols[0] } else 0
                    else -> HouseRenderer.scale(cols[0], level)
                }
            }
            SeqEffect.OFF -> 0
            SeqEffect.COLOR_WASH -> HouseRenderer.palette(cols, progress * (1f + floor(c.speed * 4f)) * 0.999f)
            SeqEffect.FILL -> {
                // Fills from the start in one colour, then the next colour fills over it.
                val v = t * rate * 0.3f
                val cyc = floor(v).toInt()
                if (pos <= HouseRenderer.frac(v)) cols[cyc.mod(cols.size)] else if (cyc > 0) cols[(cyc - 1).mod(cols.size)] else 0
            }
            SeqEffect.CANDLE -> {
                val flicker = noise(id * 0.37f, t * (2f + rate * 4f))
                HouseRenderer.scale(cols[0], 0.35f + 0.65f * flicker * flicker.coerceAtLeast(0.5f))
            }
            SeqEffect.CIRCLES -> {
                var acc = 0
                val n = 2 + ((1f - c.size) * 6f).toInt()
                val r = 0.06f + c.size * 0.16f
                for (b in 0 until n) {
                    val cx = bounce(HouseRenderer.hash(b, 3) + t * rate * (0.08f + HouseRenderer.hash(b, 5) * 0.1f)) * aspect
                    val cy = bounce(HouseRenderer.hash(b, 7) + t * rate * (0.06f + HouseRenderer.hash(b, 9) * 0.1f))
                    val d = hypot(lx * aspect - cx, ly - cy)
                    if (d < r) acc = HouseRenderer.maxRgb(acc, HouseRenderer.scale(cols[b % cols.size], 1f - (d / r) * (d / r) * 0.6f))
                }
                acc
            }
            SeqEffect.FAN -> {
                // Blades swinging round from the bottom middle.
                val blades = 2 + ((1f - c.size) * 8).toInt()
                val a = atan2(ly, (lx - 0.5f) * aspect) / PI.toFloat()
                val v = HouseRenderer.frac(a * blades + t * rate * 0.5f)
                if (v < 0.5f) HouseRenderer.palette(cols, hypot((lx - 0.5f) * aspect, ly) * 0.999f) else 0
            }
            SeqEffect.KALEIDOSCOPE -> {
                // A pattern folded into mirrored wedges round the middle.
                val wedges = 3 + ((1f - c.size) * 6).toInt()
                val dx = (lx - 0.5f) * aspect; val dy = ly - 0.5f
                val r = hypot(dx, dy)
                val seg = 2f * PI.toFloat() / wedges
                var a = (atan2(dy, dx) + t * rate * 0.2f).mod(seg)
                if (a > seg / 2f) a = seg - a
                val u = r * cos(a) * 6f; val v = r * sin(a) * 6f
                HouseRenderer.palette(cols, (sin(u + t * rate) + sin(v * 1.7f - t * rate * 0.7f) + sin((u + v) * 0.8f)) / 6f + 0.5f)
            }
            SeqEffect.LIFE -> {
                val h = (8 + ((1f - c.size) * 16)).toInt()
                val w = (h * aspect).toInt().coerceIn(4, 96)
                val gen = floor(t * (1f + rate * 4f)).toInt()
                val x = (lx * w).toInt(); val y = ((1f - ly) * h).toInt()
                if (Life.alive(c.id.hashCode(), w, h, 0.35f, gen, x, y)) cols[((x + y) / 4).mod(cols.size)] else 0
            }
            SeqEffect.LIGHTNING -> {
                // A jagged bolt from the top, flashing then fading; a new one each strike.
                val period = 0.6f + (1f - c.speed) * 2.4f
                val k = floor(t / period).toInt()
                val age = t / period - k
                val flash = if (age < 0.05f) 1f else if (age < 0.12f) 0.3f else if (age < 0.16f) 0.9f else (1f - (age - 0.16f) * 3f).coerceAtLeast(0f) * 0.6f
                val x0 = 0.2f + HouseRenderer.hash(k, 3) * 0.6f
                val down = 1f - ly
                val pathX = x0 + (noise(down * 6f, k * 3.1f) - 0.5f) * 0.35f + (noise(down * 18f, k * 7.7f) - 0.5f) * 0.08f
                val width = (0.01f + c.size * 0.04f) / aspect.coerceAtLeast(0.2f)
                val d = abs(lx - pathX)
                if (d < width * 2f) HouseRenderer.scale(cols[0], flash * (1f - d / (width * 2f))) else HouseRenderer.scale(cols.getOrElse(1) { 0 }, flash * 0.08f)
            }
            SeqEffect.LINES -> {
                // Straight lines whose ends bounce round the edges.
                var acc = 0
                val n = 1 + ((1f - c.size) * 4).toInt()
                val thick = 0.02f + c.size * 0.05f
                for (l in 0 until n) {
                    fun p(salt: Int, sp: Float) = bounce(HouseRenderer.hash(l, salt) + t * rate * sp)
                    val ax = p(1, 0.11f) * aspect; val ay = p(2, 0.07f); val bx = p(3, 0.09f) * aspect; val by = p(4, 0.13f)
                    val d = segDist(lx * aspect, ly, ax, ay, bx, by)
                    if (d < thick) acc = HouseRenderer.maxRgb(acc, HouseRenderer.scale(cols[l % cols.size], 1f - d / thick))
                }
                acc
            }
            SeqEffect.LIQUID -> {
                val wx = noise(lx * aspect * 2f + t * rate * 0.2f, ly * 2f)
                val v = noise(lx * aspect * 3f + wx * 2f, ly * 3f - t * rate * 0.4f)
                HouseRenderer.palette(cols, v * 0.999f)
            }
            SeqEffect.MORPH -> {
                // A band that travels from one end to the other over the effect, head bright, tail fading.
                val len = 0.1f + c.size * 0.6f
                val head = progress * (1f + len)
                val d = head - pos
                if (d < 0f || d > len) 0 else HouseRenderer.palette(listOf(cols[0], cols.getOrElse(1) { cols[0] }), (d / len) * 0.999f).let {
                    HouseRenderer.scale(it, 1f - d / len * 0.7f)
                }
            }
            SeqEffect.SHAPE -> {
                // Stars appearing round the area, growing and fading.
                var acc = 0
                val slot = 0.5f / (0.2f + rate)
                val now = floor(t / slot).toInt()
                for (k in now - 3..now) {
                    val age = (t - k * slot) / (slot * 4f)
                    if (age !in 0f..1f) continue
                    val cx = HouseRenderer.hash(k, 21) * aspect; val cy = 0.15f + HouseRenderer.hash(k, 23) * 0.7f
                    val dx = lx * aspect - cx; val dy = ly - cy
                    val a = atan2(dy, dx)
                    val size = age * (0.15f + c.size * 0.35f)
                    val star = size * (0.65f + 0.35f * cos(5f * a))
                    val d = abs(hypot(dx, dy) - star)
                    if (d < 0.025f) acc = HouseRenderer.maxRgb(acc, HouseRenderer.scale(cols[k.mod(cols.size)], (1f - age) * (1f - d / 0.025f)))
                }
                acc
            }
            SeqEffect.SINGLE_STRAND -> {
                // A block bouncing end to end along the strand, leaving a fading trail.
                val w = 0.04f + c.size * 0.25f
                val head = bounce(t * rate * 0.3f) * (1f + w) - w / 2f
                val d = abs(pos - head)
                if (d > w) 0 else HouseRenderer.scale(HouseRenderer.palette(cols, head.coerceIn(0f, 0.999f)), 1f - d / w)
            }
            SeqEffect.SNOWFLAKES -> {
                var acc = HouseRenderer.scale(cols.getOrElse(1) { 0 }, 0.05f)
                val n = 6 + ((1f - c.size) * 20).toInt()
                val r = 0.02f + c.size * 0.04f
                for (f in 0 until n) {
                    val fall = HouseRenderer.frac(HouseRenderer.hash(f, 2) + t * rate * (0.05f + HouseRenderer.hash(f, 4) * 0.08f))
                    val fx = (HouseRenderer.hash(f, 6) + 0.04f * sin(t * 1.3f + f)) * aspect
                    val fy = 1.05f - fall * 1.1f
                    val dx = abs(lx * aspect - fx); val dy = abs(ly - fy)
                    // Six-armed: a cross and its diagonals.
                    val arm = minOf(dx, dy, abs(dx - dy) * 0.7071f)
                    if (hypot(dx, dy) < r && arm < r * 0.25f) acc = HouseRenderer.maxRgb(acc, cols[0])
                }
                acc
            }
            SeqEffect.SPIROGRAPH -> {
                // A spirograph curve, slowly turning.
                val big = 0.42f; val small = 0.12f + c.size * 0.18f; val pen = 0.2f
                var best = 9f; var at = 0f
                val dx = (lx - 0.5f) * aspect; val dy = ly - 0.5f
                val turn = t * rate * 0.3f
                for (i in 0 until 160) {
                    val th = i / 160f * 2f * PI.toFloat() * 6f
                    val x = (big - small) * cos(th + turn) + pen * cos((big - small) / small * th + turn)
                    val y = (big - small) * sin(th + turn) - pen * sin((big - small) / small * th + turn)
                    val d = hypot(dx - x * minOf(aspect, 1.6f), dy - y)
                    if (d < best) { best = d; at = i / 160f }
                }
                if (best < 0.03f) HouseRenderer.scale(HouseRenderer.palette(cols, at * 0.999f), 1f - best / 0.03f) else 0
            }
            SeqEffect.TENDRIL -> {
                // A wandering line with a fading tail.
                val tailLen = 30
                var best = 9f; var bestK = 0
                for (k in 0 until tailLen) {
                    val tt = t - k * 0.04f * (1.5f - c.speed)
                    val x = noise(tt * rate * 0.6f, 1.3f) * aspect
                    val y = noise(5.1f, tt * rate * 0.6f)
                    val d = hypot(lx * aspect - x, ly - y)
                    if (d < best) { best = d; bestK = k }
                }
                val thick = 0.02f + c.size * 0.05f
                if (best < thick) HouseRenderer.scale(HouseRenderer.palette(cols, bestK / tailLen.toFloat()), (1f - bestK / tailLen.toFloat()) * (1f - best / thick)) else 0
            }
            SeqEffect.TREE -> {
                // Rows of branches, each with lights chasing along it, coloured by row.
                val rows = 4 + ((1f - c.size) * 10).toInt()
                val row = floor(ly * rows).toInt()
                val dirn = if (row % 2 == 0) 1f else -1f
                val v = HouseRenderer.frac(lx * 6f * dirn - t * rate * 0.5f)
                val within = HouseRenderer.frac(ly * rows)
                if (within > 0.75f) 0 else HouseRenderer.scale(cols[row.mod(cols.size)], 0.25f + 0.75f * v)
            }
            SeqEffect.WARP -> {
                // Flying through a tunnel of rings.
                val r = hypot((lx - 0.5f) * aspect, ly - 0.5f).coerceAtLeast(0.01f)
                val v = HouseRenderer.frac(1f / (r * (2f + c.size * 6f)) - t * rate * 0.6f)
                HouseRenderer.scale(HouseRenderer.palette(cols, v), (r * 2.5f).coerceAtMost(1f) * (1f - abs(v * 2f - 1f)))
            }
            SeqEffect.WAVE -> {
                // A wave line rolling across, filled underneath.
                val cycles = 0.5f + (1f - c.size) * 3f
                val wy = 0.5f + 0.35f * sin((lx * cycles - t * rate * 0.4f) * 2f * PI.toFloat())
                when {
                    abs(ly - wy) < 0.05f -> cols[0]
                    ly < wy -> HouseRenderer.scale(cols.getOrElse(1) { cols[0] }, 0.4f)
                    else -> 0
                }
            }
            SeqEffect.PIANO -> {
                // Keys across the area pressed with the music: louder plays more keys.
                val keys = 12 + ((1f - c.size) * 24).toInt()
                val key = floor(lx * keys).toInt()
                val step = floor(t * (2f + rate * 6f)).toInt()
                val pressed = HouseRenderer.hash(key, step) < level * 0.7f + beat * 0.2f
                val black = (key % 12) in intArrayOf(1, 3, 6, 8, 10)
                when {
                    pressed -> cols[0]
                    black -> 0
                    else -> HouseRenderer.scale(cols.getOrElse(1) { 0xFFFFFF }, 0.15f)
                }
            }
        }
    }
}
