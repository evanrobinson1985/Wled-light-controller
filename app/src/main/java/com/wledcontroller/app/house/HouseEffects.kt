package com.wledcontroller.app.house

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** Effects computed from each LED's position on the house, so they flow across every controller. */
enum class HouseEffect(val label: String) {
    WAVE("Colour wave"),
    RAINBOW("Rainbow sweep"),
    WIPE("Colour wipe"),
    CHASE("Candy chase"),
    RISE("Rising glow"),
    PULSE("Ripple from centre"),
    TWINKLE("Twinkle"),
    FIREWORKS("Fireworks"),
    SNOW("Falling snow"),
    BREATHE("Breathe"),
    SOLID("Solid"),
}

data class HouseEffectParams(
    val effect: HouseEffect = HouseEffect.WAVE,
    /** Three ARGB colours the effects draw from. */
    val colors: List<Int> = listOf(0xFFFF5A00.toInt(), 0xFF8000FF.toInt(), 0xFF40FF00.toInt()),
    /** 0..1 */
    val speed: Float = 0.5f,
    /** 0..1: how large features are across the house. */
    val size: Float = 0.5f,
    /** 0..1 */
    val brightness: Float = 0.8f,
)

object HouseRenderer {

    private fun frac(v: Float) = v - floor(v)

    /** Cheap deterministic hash → 0..1. */
    private fun hash(a: Int, b: Int = 0): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun lerp(a: Int, b: Int, f: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - f) + ((b shr s) and 0xFF) * f).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Smooth cyclic palette through the colours. */
    private fun palette(colors: List<Int>, pos: Float): Int {
        val n = colors.size.coerceAtLeast(1)
        val p = frac(pos) * n
        val i = p.toInt() % n
        return lerp(colors[i], colors[(i + 1) % n], p - p.toInt())
    }

    private fun scale(rgb: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        return ((((rgb shr 16) and 0xFF) * k).toInt() shl 16) or ((((rgb shr 8) and 0xFF) * k).toInt() shl 8) or ((rgb and 0xFF) * k).toInt()
    }

    private fun hsv(h: Float, v: Float = 1f): Int = android.graphics.Color.HSVToColor(floatArrayOf(frac(h) * 360f, 1f, v)) and 0xFFFFFF

    /**
     * Colour (0xRRGGBB, brightness applied) of an LED at ([x], [y]) at time [t] seconds.
     * [id] is a stable per-LED number for randomness; [order] its position within its run.
     */
    fun color(p: HouseEffectParams, x: Float, y: Float, t: Float, id: Int, order: Int): Int {
        val sp = 0.1f + p.speed * 1.9f          // 0.1 .. 2
        val sz = 0.25f + (1f - p.size) * 2.75f  // features per house width: 3 (small) .. 0.25 (large)
        val c = p.colors.ifEmpty { listOf(0xFFFFFF) }
        val rgb = when (p.effect) {
            HouseEffect.SOLID -> c[0]
            HouseEffect.WAVE -> palette(c, x * sz - t * sp * 0.25f)
            HouseEffect.RAINBOW -> hsv(x * sz * 0.5f - t * sp * 0.2f)
            HouseEffect.WIPE -> {
                // Each colour sweeps across the house in turn.
                val phase = t * sp * 0.25f
                val k = floor(phase).toInt()
                val edge = frac(phase) * 1.2f - 0.1f
                if (x < edge) c[(k + 1) % c.size] else c[k % c.size]
            }
            HouseEffect.CHASE -> c[((order + floor(t * sp * 6f).toInt()) / (1 + (p.size * 4).toInt())).mod(c.size)]
            HouseEffect.RISE -> palette(c, (1f - y) * sz * 0.6f + t * sp * 0.2f)
            HouseEffect.PULSE -> {
                val d = hypot(x - 0.5f, y - 0.5f)
                palette(c, d * sz * 1.5f - t * sp * 0.4f)
            }
            HouseEffect.TWINKLE -> {
                val rate = 0.3f + hash(id, 7) * 0.7f
                val ph = t * sp * rate + hash(id, 3) * 10f
                val cycle = floor(ph).toInt()
                val f = frac(ph)
                val glow = sin(f * PI).toFloat().let { it * it }
                scale(c[(hash(id, cycle) * c.size).toInt().coerceAtMost(c.size - 1)], 0.08f + glow * 0.92f)
            }
            HouseEffect.FIREWORKS -> {
                // A new burst somewhere on the house every ~1.2 s; rings expand and fade.
                var acc = 0
                val slotLen = 1.2f / sp
                val now = floor(t / slotLen).toInt()
                for (k in now - 2..now) {
                    val age = t - k * slotLen
                    if (age < 0) continue
                    val cx = hash(k, 11); val cy = hash(k, 13) * 0.7f
                    val r = age * 0.35f * sp
                    val d = hypot(x - cx, y - cy)
                    val ring = exp(-((d - r) * (d - r)) / 0.0018f) * (1f - age / (slotLen * 3f)).coerceAtLeast(0f)
                    val col = scale(c[(hash(k, 17) * c.size).toInt().coerceAtMost(c.size - 1)], ring)
                    acc = maxRgb(acc, col)
                }
                acc
            }
            HouseEffect.SNOW -> {
                val lane = floor(x * 40f).toInt()
                val fall = frac(y * 0.8f - t * sp * (0.15f + hash(lane, 5) * 0.15f) + hash(lane, 9))
                val flake = if (fall < 0.06f) 1f - fall / 0.06f else 0f
                maxRgb(scale(c.getOrElse(1) { c[0] }, 0.12f), scale(c[0], flake))
            }
            HouseEffect.BREATHE -> scale(palette(c, x * 0.5f), 0.15f + 0.85f * (0.5f + 0.5f * sin(t * sp * 1.5f).toFloat()))
        }
        return scale(rgb, p.brightness)
    }

    private fun maxRgb(a: Int, b: Int) =
        (maxOf((a shr 16) and 0xFF, (b shr 16) and 0xFF) shl 16) or
            (maxOf((a shr 8) and 0xFF, (b shr 8) and 0xFF) shl 8) or
            maxOf(a and 0xFF, b and 0xFF)
}
