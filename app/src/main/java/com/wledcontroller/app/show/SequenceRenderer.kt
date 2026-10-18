package com.wledcontroller.app.show

import com.wledcontroller.app.house.Direction
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.HouseRenderer
import com.wledcontroller.app.house.PlacedLed
import com.wledcontroller.app.house.placedLeds
import kotlin.math.PI
import kotlin.math.abs
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
)

object SequenceRenderer {

    fun prepare(map: HouseMap, tracks: List<Track>): PreparedLayout {
        val leds = map.placedLeds()
        val runs = map.runs
        val runCounts = runs.map { it.count }
        val trackLayouts = tracks.map { track ->
            val runIdxs: Set<Int> = when (track.target.kind) {
                TrackTarget.Kind.HOUSE -> runs.indices.toSet()
                TrackTarget.Kind.PROP -> runs.indices.filter { runs[it].id == track.target.refId }.toSet()
                TrackTarget.Kind.GROUP -> {
                    val ids = map.groups.firstOrNull { it.id == track.target.refId }?.runIds?.toSet() ?: emptySet()
                    runs.indices.filter { runs[it].id in ids }.toSet()
                }
            }
            val idx = leds.indices.filter { leds[it].runIndex in runIdxs }.toIntArray()
            val xs = FloatArray(idx.size) { leds[idx[it]].x * map.aspect }
            val ys = FloatArray(idx.size) { leds[idx[it]].y }
            val minX = xs.minOrNull() ?: 0f; val maxX = xs.maxOrNull() ?: 1f
            val minY = ys.minOrNull() ?: 0f; val maxY = ys.maxOrNull() ?: 1f
            val sx = (maxX - minX).takeIf { it > 1e-4f }; val sy = (maxY - minY).takeIf { it > 1e-4f }
            TrackLayout(
                ledIndex = idx,
                along = FloatArray(idx.size) {
                    val l = leds[idx[it]]
                    val n = runCounts[l.runIndex]
                    if (n <= 1) 0f else l.orderInRun / (n - 1f)
                },
                lx = FloatArray(idx.size) { i -> sx?.let { (xs[i] - minX) / it } ?: 0.5f },
                // Flip so 0 is the bottom: "up" effects read naturally.
                ly = FloatArray(idx.size) { i -> sy?.let { 1f - (ys[i] - minY) / it } ?: 0.5f },
                natural = Array(idx.size) { runs[leds[idx[it]].runIndex].prop.natural },
                aspect = if (sx != null && sy != null) sx / sy else if (sx != null) 8f else 1f,
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
    ): IntArray {
        out.fill(0)
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
            if (intensity <= 0f) continue
            val t = local / 1000f
            for (k in tl.ledIndex.indices) {
                val li = tl.ledIndex[k]
                val dir = if (clip.direction == Direction.AUTO) tl.natural[k] else clip.direction
                val pos = position(dir, tl.along[k], tl.lx[k], tl.ly[k])
                val reveal = transition(clip, local, pos, tl.lx[k], tl.ly[k], li)
                val c = if (reveal <= 0f) 0
                else HouseRenderer.scale(effectColor(clip, pos, tl.lx[k], tl.ly[k], t, progress, li, tl.aspect, level, beat), intensity * reveal)
                out[li] = mix(track.blend, out[li], c)
            }
        }
        return out
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
        aspect: Float = 1f, level: Float = 0f, beat: Float = 0f,
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
        }
    }
}
