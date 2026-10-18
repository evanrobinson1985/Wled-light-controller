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
            )
        }
        return PreparedLayout(leds, trackLayouts)
    }

    /** Colours (0xRRGGBB) of every LED in [layout] at [timeMs]. Overlapping tracks blend by keeping the brightest. */
    fun render(seq: Sequence, layout: PreparedLayout, timeMs: Long, out: IntArray = IntArray(layout.leds.size)): IntArray {
        out.fill(0)
        seq.tracks.forEachIndexed { ti, track ->
            if (track.muted) return@forEachIndexed
            val tl = layout.tracks.getOrNull(ti) ?: return@forEachIndexed
            // The latest-starting clip that covers this moment wins within a track.
            val stored = track.clips.lastOrNull { timeMs >= it.startMs && timeMs < it.endMs } ?: return@forEachIndexed
            val local = timeMs - stored.startMs
            val progress = local.toFloat() / stored.durationMs.coerceAtLeast(1)
            // Ramps: brightness follows the line; speed uses its average so far, so motion stays continuous.
            val intensity = stored.intensityEnd?.let { stored.intensity + (it - stored.intensity) * progress } ?: stored.intensity
            val clip = stored.speedEnd?.let { stored.copy(speed = stored.speed + (it - stored.speed) * progress / 2f) } ?: stored
            val env = envelope(clip, local) * intensity
            if (env <= 0f) return@forEachIndexed
            val t = local / 1000f
            for (k in tl.ledIndex.indices) {
                val li = tl.ledIndex[k]
                val dir = if (clip.direction == Direction.AUTO) tl.natural[k] else clip.direction
                val pos = position(dir, tl.along[k], tl.lx[k], tl.ly[k])
                val c = HouseRenderer.scale(effectColor(clip, pos, tl.lx[k], tl.ly[k], t, progress, li), env)
                out[li] = HouseRenderer.maxRgb(out[li], c)
            }
        }
        return out
    }

    private fun envelope(clip: Clip, local: Long): Float {
        var e = 1f
        if (clip.fadeInMs > 0 && local < clip.fadeInMs) e = local.toFloat() / clip.fadeInMs
        val remaining = clip.durationMs - local
        if (clip.fadeOutMs > 0 && remaining < clip.fadeOutMs) e = minOf(e, remaining.toFloat() / clip.fadeOutMs)
        return e.coerceIn(0f, 1f)
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

    private fun effectColor(c: Clip, pos: Float, lx: Float, ly: Float, t: Float, progress: Float, id: Int): Int {
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
        }
    }
}
