package com.wledcontroller.app.house

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** Effects computed from each LED's position on the house, so they flow across every controller. */
/**
 * Whole-house effects. [holiday] puts shadow-figure effects under their holiday; [figures] are
 * the shapes whose shadows move about the lit house (several mix together in a parade).
 */
enum class HouseEffect(val label: String, val holiday: String? = null, val figures: List<Figure> = emptyList()) {
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
    GHOSTS("Ghosts", "Halloween", listOf(Figure.GHOST)),
    CLOWNS("Clowns", "Halloween", listOf(Figure.CLOWN)),
    SPIDERS("Crawling spiders", "Halloween", listOf(Figure.SPIDER)),
    WITCHES("Flying witches", "Halloween", listOf(Figure.WITCH)),
    HALLOWEEN_PARADE("Halloween parade", "Halloween", listOf(Figure.GHOST, Figure.WITCH, Figure.SPIDER, Figure.CLOWN)),
    SLEIGH("Santa's sleigh", "Christmas", listOf(Figure.SLEIGH)),
    BUNNIES("Hopping bunnies", "Easter", listOf(Figure.BUNNY)),
    TURKEYS("Strutting turkeys", "Thanksgiving", listOf(Figure.TURKEY)),
    HEARTS("Floating hearts", "Valentine's Day", listOf(Figure.HEART)),
    SHAMROCKS("Drifting shamrocks", "St Patrick's Day", listOf(Figure.SHAMROCK)),
    EAGLES("Soaring eagles", "Independence Day", listOf(Figure.EAGLE)),
    ;

    /** What the size / number / speed sliders call one of the figures, and several. */
    val noun get() = figures.singleOrNull()?.noun ?: "figure"
    val plural get() = figures.singleOrNull()?.plural ?: "figures"
}

@kotlinx.serialization.Serializable
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
    /** How many there are, for effects with separate things moving about (ghosts). */
    val count: Int = 3,
)

object HouseRenderer {

    internal fun frac(v: Float) = v - floor(v)

    /** Cheap deterministic hash → 0..1. */
    internal fun hash(a: Int, b: Int = 0): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    internal fun lerp(a: Int, b: Int, f: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - f) + ((b shr s) and 0xFF) * f).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Smooth cyclic palette through the colours. */
    internal fun palette(colors: List<Int>, pos: Float): Int {
        val n = colors.size.coerceAtLeast(1)
        val p = frac(pos) * n
        val i = p.toInt() % n
        return lerp(colors[i], colors[(i + 1) % n], p - p.toInt())
    }

    internal fun scale(rgb: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        return ((((rgb shr 16) and 0xFF) * k).toInt() shl 16) or ((((rgb shr 8) and 0xFF) * k).toInt() shl 8) or ((rgb and 0xFF) * k).toInt()
    }

    internal fun hsv(h: Float, v: Float = 1f): Int = android.graphics.Color.HSVToColor(floatArrayOf(frac(h) * 360f, 1f, v)) and 0xFFFFFF

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
            HouseEffect.GHOSTS -> ghosts(p, c, x, y, t)
            HouseEffect.SNOW -> {
                val lane = floor(x * 40f).toInt()
                val fall = frac(y * 0.8f - t * sp * (0.15f + hash(lane, 5) * 0.15f) + hash(lane, 9))
                val flake = if (fall < 0.06f) 1f - fall / 0.06f else 0f
                maxRgb(scale(c.getOrElse(1) { c[0] }, 0.12f), scale(c[0], flake))
            }
            HouseEffect.BREATHE -> scale(palette(c, x * 0.5f), 0.15f + 0.85f * (0.5f + 0.5f * sin(t * sp * 1.5f).toFloat()))
            // Clowns, spiders, witches and the other holiday shadows.
            else -> ShadowFigures.render(p, p.effect.figures.ifEmpty { listOf(Figure.GHOST) }, c, x, y, t)
        }
        return scale(rgb, p.brightness)
    }

    /**
     * The whole house lit in the colours, except where ghosts' shadows drift about: each ghost
     * is a dark shape with a round head and a wavy hem (with two faint eyes), wandering the
     * house on its own path. [HouseEffectParams.size] sets their size, [HouseEffectParams.count]
     * how many, and [HouseEffectParams.speed] how fast they float.
     */
    private fun ghosts(p: HouseEffectParams, c: List<Int>, x: Float, y: Float, t: Float): Int {
        // The lit house: the colours spread gently across it, slowly shimmering.
        val lit = palette(c, x * 0.6f + t * 0.02f)
        val speed = 0.15f + p.speed * 1.6f
        val w = 0.08f + p.size * 0.32f          // ghost width, in house widths
        val h = w * 1.35f                        // a little taller than wide
        var shadow = 0f
        var eye = 0f
        for (k in 0 until p.count.coerceIn(1, MAX_GHOSTS)) {
            // Each ghost floats on its own slow, looping path, bobbing as it goes.
            val ax = speed * (0.22f + hash(k, 1) * 0.18f)
            val ay = speed * (0.17f + hash(k, 3) * 0.14f)
            val gx = 0.5f + 0.45f * sin(t * ax + hash(k, 2) * 6.283f)
            val gy = 0.08f + 0.62f * (0.5f + 0.5f * sin(t * ay + hash(k, 4) * 6.283f)) + 0.02f * sin(t * 2.6f + k)
            val u = (x - gx) / w                 // −0.5 … 0.5 across the ghost
            val v = (y - gy) / h                 // 0 at the top of its head, 1 at its hem
            val inside = ghostShape(u, v, t + k)
            // Soft edges: a little outside the shape is still in half-shadow.
            val edge = maxOf(inside, 0.5f * ghostShape(u / 1.18f, (v - 0.5f) / 1.18f + 0.5f, t + k))
            if (edge > shadow) shadow = edge
            // Two eyes, faintly glowing in the dark.
            val ex = abs(u) - 0.17f; val ey = v - 0.33f
            if (inside > 0f && ex * ex + ey * ey < 0.009f) eye = 1f
        }
        val dark = scale(lit, 1f - shadow * 0.96f)
        return if (eye > 0f) maxRgb(dark, scale(c.getOrElse(1) { c[0] }, 0.55f)) else dark
    }

    /** 1 inside a ghost (local coords: u across −0.5…0.5, v down 0…1), else 0. */
    private fun ghostShape(u: Float, v: Float, t: Float): Float {
        if (abs(u) > 0.5f || v < 0f) return 0f
        if (v < 0.5f) {
            // Round head.
            val dv = (v - 0.5f) / 0.5f; val du = u / 0.5f
            return if (du * du + dv * dv <= 1f) 1f else 0f
        }
        // Body down to a wavy, rippling hem.
        val hem = 0.88f + 0.1f * sin(u * 6f * PI.toFloat() + t * 4f)
        return if (v <= hem) 1f else 0f
    }

    private const val MAX_GHOSTS = 8

    internal fun maxRgb(a: Int, b: Int) =
        (maxOf((a shr 16) and 0xFF, (b shr 16) and 0xFF) shl 16) or
            (maxOf((a shr 8) and 0xFF, (b shr 8) and 0xFF) shl 8) or
            maxOf(a and 0xFF, b and 0xFF)
}
