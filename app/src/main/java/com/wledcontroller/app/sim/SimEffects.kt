package com.wledcontroller.app.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** One segment's effect settings, as WLED holds them. */
internal class SegParams(
    val len: Int,
    val fx: Int,
    val sx: Int,
    val ix: Int,
    val c1: Int,
    val c2: Int,
    val c3: Int,
    /** Three colour slots, 0xRRGGBB. */
    val colors: IntArray,
    val pal: Int,
    val seed: Int,
)

/**
 * Close look-alikes of WLED's 1D effects, computed from the time alone (no per-frame state),
 * so any number of segments of any length can be drawn at any moment.
 */
internal object SimEffects {

    fun render(p: SegParams, timeMs: Long, out: IntArray) {
        val n = p.len
        if (n <= 0) return
        val t = timeMs / 1000f
        val s = p.sx / 255f
        val iy = p.ix / 255f
        val c0 = p.colors[0]; val cb = p.colors[1]; val cc = p.colors[2]
        val name = SimCatalog.effects.getOrElse(p.fx) { "Solid" }
        // Cycles per second, roughly matching WLED's speed slider feel.
        val rate = 0.04f + s * s * 2.5f
        val seed = p.seed

        fun pal(pos: Float, slot: Int = 0) = palette(p, pos, t, slot)
        fun palNative(pos: Float) = if (p.pal == 0) rainbow(pos) else palette(p, pos, t, 0)
        fun x(k: Int) = if (n <= 1) 0f else k / (n - 1f)
        fun each(f: (Int) -> Int) { for (k in 0 until n) out[k] = f(k) }

        when (name) {
            "Solid" -> each { c0 }
            "Blink", "Strobe", "Strobe Mega", "Blink Rainbow", "Strobe Rainbow" -> {
                val hz = when (name) { "Strobe" -> 2f + s * 10f; "Strobe Mega" -> 6f + s * 20f; else -> 0.5f + s * 4f }
                val phase = frac(t * hz)
                val duty = if (name.startsWith("Strobe")) 0.12f else 0.1f + iy * 0.8f
                val on = phase < duty
                val col = if (name.contains("Rainbow")) hsv(floor(t * hz) * 0.13f) else c0
                each { if (on) col else cb }
            }
            "Breathe" -> { val k = 0.5f - 0.5f * cosw(t * rate); each { blend(cb, pal(x(it)), 0.1f + 0.9f * k) } }
            "Fade" -> { val k = 0.5f - 0.5f * cosw(t * rate); each { blend(cb, pal(x(it)), k) } }
            "Wipe", "Sweep", "Wipe Random", "Sweep Random", "Tri Wipe" -> {
                val ph = t * rate * 0.6f
                val cyc = floor(ph).toInt(); val f = frac(ph)
                val sweep = name.startsWith("Sweep")
                each { k ->
                    val pos = if (sweep && cyc % 2 == 1) 1f - x(k) else x(k)
                    when (name) {
                        "Wipe Random", "Sweep Random" -> if (pos <= f) hsv(hash(cyc, seed)) else hsv(hash(cyc - 1, seed))
                        "Tri Wipe" -> { val cols = intArrayOf(c0, cb, cc); if (pos <= f) cols[(cyc + 1) % 3] else cols[cyc % 3] }
                        else -> if ((pos <= f) xor (cyc % 2 == 1)) pal(pos) else cb
                    }
                }
            }
            "Random Colors" -> {
                val ph = t * rate * 0.3f; val cyc = floor(ph).toInt()
                val col = blend(hsv(hash(cyc - 1, seed)), hsv(hash(cyc, seed)), smooth(frac(ph) * 2f))
                each { col }
            }
            "Dynamic", "Dynamic Smooth" -> {
                val ph = t * rate * 0.8f; val cyc = floor(ph).toInt()
                each { k ->
                    if (name == "Dynamic") hsv(hash(k * 31 + cyc, seed))
                    else blend(hsv(hash(k * 31 + cyc - 1, seed)), hsv(hash(k * 31 + cyc, seed)), frac(ph))
                }
            }
            "Colorloop" -> { val col = palNative(t * rate * 0.2f); each { col } }
            "Rainbow" -> each { k -> palNative(x(k) * (0.5f + iy * 2f) - t * rate * 0.3f) }
            "Scan", "Scan Dual", "Two Dots", "ICU" -> {
                val pos = tri(t * rate * 0.4f)
                val w = 1 + (iy * n / 20f).toInt()
                each { k ->
                    val a = abs(k - pos * (n - 1)) < w
                    val b = name != "Scan" && abs(k - (1f - pos) * (n - 1)) < w
                    when { a -> pal(x(k)); b -> if (name == "Two Dots") cc.takeIf { it != 0 } ?: cb.takeIf { it != 0 } ?: c0 else pal(x(k)); else -> if (name == "ICU") 0 else cb }
                }
            }
            "Theater", "Theater Rainbow", "Railway" -> {
                val step = floor(t * rate * 6f).toInt()
                val gap = 3 + (iy * 4).toInt()
                each { k ->
                    val on = (k + step) % gap == 0
                    if (name == "Theater Rainbow") (if (on) hsv(x(k) + t * 0.1f) else cb)
                    else if (on) pal(x(k)) else if (name == "Railway") c0.let { scale(it, 0.15f) } else cb
                }
            }
            "Running", "Saw", "Running Dual", "Sine", "Oscillate" -> {
                val w = 2f + (1f - iy) * 18f
                each { k ->
                    val ph = k / w - t * rate * 2f
                    val v = when (name) {
                        "Saw" -> frac(ph)
                        "Running Dual" -> 0.5f + 0.5f * sin((k / w + t * rate * 2f) * 2 * PI.toFloat()) * sin(ph * 2 * PI.toFloat())
                        "Oscillate" -> tri(k / (n.toFloat()) * 2f + sin(t * rate) * 0.5f)
                        else -> 0.5f + 0.5f * sin(ph * 2 * PI.toFloat())
                    }
                    blend(cb, pal(x(k)), v)
                }
            }
            "Twinkle", "Twinklefox", "Twinklecat", "Colortwinkles", "Twinkleup", "Fairy", "Fairytwinkle", "Glitter", "Solid Glitter" -> {
                val density = 0.15f + iy * 0.75f
                val glitter = name.endsWith("Glitter")
                each { k ->
                    val period = 1.2f + (1f - s) * 4f
                    val ph = t / period + hash(k, seed + 7)
                    val cyc = floor(ph).toInt()
                    val on = hash(k * 7 + cyc, seed) < density
                    val env = if (name == "Twinkleup" || name == "Twinklecat") frac(ph).let { if (it < 0.8f) it / 0.8f else (1f - it) * 5f }
                        else sin(frac(ph) * PI.toFloat())
                    val col = when (name) {
                        "Twinkle", "Fairy", "Fairytwinkle" -> pal(hash(k, cyc + seed))
                        "Glitter" -> palNative(x(k) - t * 0.05f)
                        "Solid Glitter" -> c0
                        else -> pal(hash(k * 3 + cyc, seed))
                    }
                    if (glitter) {
                        if (hash(k * 13 + floor(t * 25).toInt(), seed) < 0.02f + iy * 0.08f) 0xFFFFFF else col
                    } else if (on) scale(col, env) else if (name == "Fairy" || name == "Fairytwinkle") scale(col, 0.15f) else cb
                }
            }
            "Dissolve", "Dissolve Rnd" -> {
                val ph = t * rate * 0.3f; val cyc = floor(ph).toInt(); val f = frac(ph)
                each { k ->
                    val done = hash(k, cyc + seed) < f
                    val a = if (name == "Dissolve Rnd") hsv(hash(k + cyc * 997, seed)) else if (cyc % 2 == 0) c0 else cb
                    val b = if (name == "Dissolve Rnd") hsv(hash(k + (cyc - 1) * 997, seed)) else if (cyc % 2 == 0) cb else c0
                    if (done) a else b
                }
            }
            "Sparkle", "Sparkle Dark", "Sparkle+" -> {
                val frame = floor(t * (10f + s * 40f)).toInt()
                val chance = if (name == "Sparkle+") 0.03f + iy * 0.2f else 0.008f + iy * 0.03f
                each { k ->
                    val spark = hash(k * 17 + frame, seed) < chance
                    when (name) {
                        "Sparkle" -> if (spark) c0 else pal(x(k), 1).let { if (p.pal == 0) cb else it }
                        "Sparkle Dark" -> if (spark) cb else pal(x(k))
                        else -> if (spark) cb.takeIf { it != 0 } ?: 0xFFFFFF else pal(x(k))
                    }
                }
            }
            "Android" -> {
                val ph = t * rate * 0.5f
                val size = (0.1f + 0.4f * tri(ph * 0.7f)) * n
                val start = frac(ph) * n
                each { k -> if (((k - start) % n + n) % n < size) pal(x(k)) else cb }
            }
            "Chase", "Chase Random", "Chase Rainbow", "Chase 2", "Rainbow Runner", "Chase Flash", "Chase Flash Rnd" -> {
                val w = 3 + (iy * n / 8f).toInt()
                val head = frac(t * rate * 0.5f) * n
                val flash = name.startsWith("Chase Flash") && frac(t * 4f) < 0.15f
                each { k ->
                    val d = ((head - k) % n + n) % n
                    val inDot = d < w
                    when {
                        flash -> if (name == "Chase Flash Rnd") hsv(hash(floor(t * 4).toInt(), seed)) else 0xFFFFFF
                        name == "Chase Rainbow" -> if (inDot) c0 else hsv(x(k) + t * 0.1f)
                        name == "Rainbow Runner" -> if (inDot) hsv(x(k)) else 0
                        name == "Chase Random" -> if (inDot) hsv(hash(floor(t * rate * 0.5f).toInt(), seed)) else cb
                        name == "Chase 2" -> if (inDot) cb else pal(x(k))
                        else -> if (inDot) cb.takeIf { it != 0 } ?: cc else pal(x(k))
                    }
                }
            }
            "Colorful", "Chase 3", "Solid Pattern", "Solid Pattern Tri", "Traffic Light" -> {
                val cols = when (name) {
                    "Colorful" -> if (p.pal == 0) intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFA000) else IntArray(4) { pal(it / 4f) }
                    "Solid Pattern" -> intArrayOf(c0, cb)
                    "Traffic Light" -> intArrayOf(0xFF0000, 0xFFA000, 0x00FF00)
                    else -> intArrayOf(c0, cb, cc)
                }
                val w = maxOf(1, 1 + (iy * 9).toInt())
                val moving = name == "Colorful" || name == "Chase 3"
                val shift = if (moving) floor(t * rate * 8f).toInt() else 0
                if (name == "Traffic Light") { val c = cols[floor(t * rate * 0.6f).toInt() % 3]; each { c } }
                else each { k -> cols[(((k + shift) / w) % cols.size + cols.size) % cols.size] }
            }
            "Tri Fade", "Blends" -> {
                if (name == "Blends") each { k -> pal(x(k) * 0.5f + t * rate * 0.1f) }
                else { val ph = t * rate * 0.3f; val i0 = floor(ph).toInt(); val cols = intArrayOf(c0, cb, cc)
                    val col = blend(cols[i0 % 3], cols[(i0 + 1) % 3], frac(ph)); each { col } }
            }
            "Aurora", "Stream", "Stream 2", "Lake", "Pacifica", "Flow", "Fill Noise", "Noise 1", "Noise 2", "Noise 3", "Noise 4", "Noise Pal", "Phased Noise", "Plasma", "Dancing Shadows" -> {
                val scaleK = 2f + iy * 10f
                each { k ->
                    val xx = x(k) * scaleK
                    val v = when (name) {
                        "Plasma" -> 0.5f + 0.25f * sin(xx * 2f + t * rate * 4f) + 0.25f * sin(xx * 3.3f - t * rate * 2.7f)
                        "Pacifica", "Lake" -> 0.5f + 0.5f * sin(xx * 1.7f + t * rate * 2f) * noise(xx + t * rate, seed)
                        else -> noise(xx + t * rate * 1.5f, seed + k / 50)
                    }
                    val col = palNative(v + if (name == "Flow" || name == "Stream") t * rate * 0.2f else 0f)
                    when (name) {
                        "Aurora", "Dancing Shadows" -> scale(col, smooth(noise(xx * 0.7f - t * rate, seed + 3)))
                        "Pacifica", "Lake" -> blend(0x001030, col, v)
                        else -> col
                    }
                }
            }
            "Scanner", "Scanner Dual", "Lighthouse", "Meteor", "Meteor Smooth", "Multi Comet", "Sinelon", "Sinelon Dual", "Sinelon Rainbow", "Gradient", "Loading", "Juggle", "Bpm", "Chunchun", "Washing Machine", "Rolling Balls", "Bouncing Balls" -> {
                val trail = 0.03f + (if (name.startsWith("Meteor")) iy else 1f - iy) * 0.3f
                val heads = when (name) {
                    "Scanner" -> floatArrayOf(tri(t * rate * 0.5f))
                    "Scanner Dual", "Sinelon Dual" -> tri(t * rate * 0.5f).let { floatArrayOf(it, 1f - it) }
                    "Sinelon", "Sinelon Rainbow" -> floatArrayOf(0.5f + 0.5f * sin(t * rate * 3f))
                    "Lighthouse", "Meteor", "Meteor Smooth", "Gradient", "Loading" -> floatArrayOf(frac(t * rate * 0.5f) * 1.2f)
                    "Multi Comet" -> FloatArray(6) { frac(t * rate * (0.3f + hash(it, seed) * 0.4f) + hash(it + 9, seed)) }
                    "Juggle" -> FloatArray(8) { 0.5f + 0.5f * sin(t * rate * (1f + it * 0.37f) + it) }
                    "Chunchun" -> FloatArray(5) { 0.5f + 0.5f * sin(t * rate * 2f - it * 0.35f) }
                    "Washing Machine" -> floatArrayOf(frac(sin(t * rate * 2f) * 2f))
                    "Rolling Balls" -> FloatArray(4) { tri(t * rate * (0.2f + hash(it, seed) * 0.6f) + hash(it + 3, seed)) }
                    "Bouncing Balls" -> FloatArray(3) { b -> val period = 1.2f + b * 0.35f; val f = frac(t * (0.4f + s) / period + b * 0.3f); 1f - 4f * (f - 0.5f) * (f - 0.5f) }
                    else -> floatArrayOf(frac(t * rate * 0.5f))
                }
                val gradient = name == "Gradient" || name == "Loading"
                each { k ->
                    val pos = x(k)
                    var best = 0f; var hi = 0
                    for ((hIdx, h) in heads.withIndex()) {
                        val d = if (name.startsWith("Meteor") || name == "Lighthouse" || gradient) h - pos else abs(h - pos)
                        val v = if (d < 0) (if (gradient) 0f else 0f) else exp(-d / trail)
                        if (v > best) { best = v; hi = hIdx }
                    }
                    val col = when (name) {
                        "Sinelon Rainbow", "Juggle", "Multi Comet", "Rolling Balls", "Bouncing Balls" -> if (p.pal == 0) hsv(hi * 0.17f + hash(hi, seed)) else pal(hi / heads.size.toFloat())
                        "Meteor", "Meteor Smooth" -> if (name == "Meteor" && hash(k + floor(t * 20).toInt(), seed) < 0.2f) scale(pal(pos), 0.6f) else pal(pos)
                        else -> pal(pos)
                    }
                    if (gradient) blend(cb, col, best.coerceAtLeast(0.1f)) else if (best < 0.02f) cb else blend(cb, col, best)
                }
            }
            "Fireworks", "Fireworks Starburst", "Fireworks 1D", "Popcorn", "Rain", "Ripple", "Ripple Rainbow", "Spots", "Spots Fade", "Drip", "Lightning", "Heartbeat", "Tetrix", "Halloween Eyes" -> {
                when (name) {
                    "Lightning" -> {
                        val cyc = floor(t * (0.3f + s)).toInt(); val f = frac(t * (0.3f + s))
                        val flash = hash(cyc, seed) < 0.5f + iy * 0.5f && (f < 0.05f || (f in 0.1f..0.13f))
                        val a = hash(cyc, seed + 1); val b = a + 0.2f + hash(cyc, seed + 2) * 0.5f
                        each { k -> if (flash && x(k) in a..b) 0xFFFFFF else cb }
                    }
                    "Heartbeat" -> {
                        val ph = frac(t * (0.6f + s * 1.4f))
                        val beat = exp(-ph * 12f) + 0.6f * exp(-abs(ph - 0.25f) * 14f)
                        each { k -> blend(cb, pal(x(k)), beat.coerceAtMost(1f)) }
                    }
                    "Halloween Eyes" -> {
                        val cyc = floor(t * (0.3f + s * 0.4f)).toInt(); val f = frac(t * (0.3f + s * 0.4f))
                        val c = (hash(cyc, seed) * (n - 6)).toInt()
                        val open = f < 0.7f && !(f in 0.3f..0.33f)
                        each { k -> if (open && (k == c || k == c + 4 || k == c + 1 || k == c + 5)) pal(0f) else cb }
                    }
                    "Spots", "Spots Fade" -> {
                        val gap = 6 + ((1f - iy) * 30).toInt()
                        val fade = if (name == "Spots Fade") 0.5f + 0.5f * sin(t * rate * 3f) else 1f
                        each { k -> if (k % gap < gap / 3 * fade + 1) pal(x(k)) else cb }
                    }
                    "Tetrix" -> {
                        val fill = frac(t * rate * 0.15f)
                        each { k -> if (x(k) < fill) pal(floor(x(k) * 8f) / 8f + hash(floor(x(k) * 8f).toInt(), seed) * 0.3f) else cb }
                    }
                    else -> {
                        // Bursts: each slot launches at a random place, fades / spreads.
                        val slots = 3 + (iy * 12).toInt()
                        each { k -> cb }
                        for (b in 0 until slots) {
                            val period = 0.8f + (1f - s) * 2.5f
                            val ph = t / period + hash(b, seed + 5)
                            val cyc = floor(ph).toInt(); val f = frac(ph)
                            val center = hash(b * 31 + cyc, seed)
                            val col = if (name == "Ripple Rainbow" || (p.pal == 0 && name != "Drip")) hsv(hash(b + cyc * 7, seed)) else pal(hash(b + cyc * 7, seed))
                            val spread = when (name) { "Ripple", "Ripple Rainbow" -> f * 0.25f; "Fireworks Starburst" -> f * 0.12f; "Rain" -> 0f; "Drip", "Popcorn" -> 0f; else -> 0.01f }
                            val pos = when (name) { "Rain" -> frac(center + f * 0.4f); "Drip" -> f * f; "Popcorn" -> 1f - 4f * (f - 0.5f) * (f - 0.5f); else -> center }
                            val bright = when (name) { "Drip" -> 1f; "Popcorn" -> 1f; "Rain" -> 1f - f; else -> (1f - f).let { it * it } }
                            for (k in 0 until n) {
                                val d = abs(x(k) - pos)
                                val v = if (spread > 0) (if (abs(d - spread) < 1.5f / n || d < 1f / n) bright else 0f) else if (d < 1.2f / n) bright else 0f
                                if (v > 0) out[k] = maxRgb(out[k], scale(col, v))
                            }
                        }
                    }
                }
            }
            "Fire 2012", "Fire Flicker", "Candle", "Candle Multi" -> {
                each { k ->
                    when (name) {
                        "Fire 2012" -> {
                            val h = (noise(x(k) * 8f - t * (1f + s * 3f), seed) * (1.2f - x(k) * (0.6f + (1f - iy) * 0.5f))).coerceIn(0f, 1f)
                            if (p.pal == 0) fireColor(h) else pal(h)
                        }
                        else -> {
                            val nk = if (name == "Candle Multi") k else 0
                            val f = 0.55f + 0.45f * noise(t * (3f + s * 10f) + nk * 3.1f, seed + nk)
                            scale(if (name == "Fire Flicker") c0 else pal(x(k)), f)
                        }
                    }
                }
            }
            "Pride 2015", "Palette", "Colorwaves", "Phased" -> {
                each { k ->
                    when (name) {
                        "Palette" -> palNative(x(k) - t * rate * 0.3f)
                        "Phased" -> scale(pal(x(k)), 0.5f + 0.5f * sin(k * 0.5f * (1f + iy * 3f) - t * rate * 8f))
                        else -> {
                            val w = sin(t * rate * 0.7f) * 0.4f
                            val v = 0.6f + 0.4f * sin(x(k) * 12f * (0.5f + iy) + t * rate * 5f)
                            scale(palNative(x(k) * (1f + w) - t * rate * 0.25f), v)
                        }
                    }
                }
            }
            "Percent" -> each { k -> if (x(k) <= iy) c0 else cb }
            "Sunrise" -> {
                val f = (t / 60f / ((p.sx.coerceAtLeast(1)) / 10f)).coerceIn(0f, 1f)
                each { k -> val v = f * (1f - abs(x(k) - 0.5f) * (1f - f) * 2f); fireColor(v.coerceIn(0f, 1f)) }
            }
            "TV Simulator" -> {
                val cyc = floor(t * (0.5f + s * 2f)).toInt()
                val col = blend(hsv(hash(cyc, seed), 0.6f), 0xFFFFFF, hash(cyc, seed + 2) * 0.5f)
                val dim = 0.4f + 0.6f * hash(floor(t * 8).toInt(), seed)
                each { scale(col, dim) }
            }
            else -> each { k -> pal(x(k) - t * rate * 0.2f) }
        }
    }

    /** Colour at [pos] (wraps) from the segment's palette; 0 = the effect's own colour [slot]. */
    fun palette(p: SegParams, pos: Float, t: Float, slot: Int): Int {
        val c = p.colors
        val f = frac(pos)
        return when (p.pal) {
            0 -> c[slot].let { if (it == 0 && slot == 0) 0xFF8000 else it }
            1 -> hsv(f + t * 0.02f)
            2 -> c[0]
            3 -> if (f < 0.5f) blend(c[0], c[1], f * 2f) else blend(c[1], c[0], (f - 0.5f) * 2f)
            4 -> when { f < 1 / 3f -> blend(c[0], c[1], f * 3f); f < 2 / 3f -> blend(c[1], c[2], f * 3f - 1f); else -> blend(c[2], c[0], f * 3f - 2f) }
            5 -> c[(f * 3f).toInt().coerceIn(0, 2)]
            else -> gradient(SimCatalog.palettes.getOrNull(p.pal)?.second ?: return hsv(f), f)
        }
    }

    private fun gradient(stops: List<Pair<Int, Int>>, f: Float): Int {
        if (stops.isEmpty()) return 0
        val pos = f * 255f
        var prev = stops.first()
        for (s in stops) {
            if (pos <= s.first) {
                val span = (s.first - prev.first).toFloat()
                return if (span <= 0f) s.second else blend(prev.second, s.second, (pos - prev.first) / span)
            }
            prev = s
        }
        return stops.last().second
    }

    // -------------------------------------------------------------- maths helpers

    fun frac(v: Float) = v - floor(v)
    private fun tri(v: Float) = 1f - abs(frac(v) * 2f - 1f)
    private fun cosw(v: Float) = kotlin.math.cos(v * 2f * PI.toFloat())
    private fun smooth(v: Float) = v.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

    fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    /** Smooth 1D value noise, 0..1. */
    fun noise(x: Float, seed: Int): Float {
        val i = floor(x).toInt(); val f = x - i
        return hash(i, seed) + (hash(i + 1, seed) - hash(i, seed)) * smooth(f)
    }

    fun rainbow(pos: Float) = hsv(pos)

    fun hsv(h: Float, sat: Float = 1f): Int {
        val hh = frac(h) * 6f
        val i = hh.toInt(); val f = hh - i
        val p = 1f - sat; val q = 1f - sat * f; val tt = 1f - sat * (1f - f)
        val (r, g, b) = when (i) { 0 -> Triple(1f, tt, p); 1 -> Triple(q, 1f, p); 2 -> Triple(p, 1f, tt); 3 -> Triple(p, q, 1f); 4 -> Triple(tt, p, 1f); else -> Triple(1f, p, q) }
        return ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
    }

    private fun fireColor(h: Float): Int {
        val v = h.coerceIn(0f, 1f) * 3f
        return when {
            v < 1f -> ((v * 255).toInt() shl 16)
            v < 2f -> (255 shl 16) or (((v - 1f) * 255).toInt() shl 8)
            else -> (255 shl 16) or (255 shl 8) or ((v - 2f) * 255).toInt()
        }
    }

    fun blend(a: Int, b: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        fun ch(sh: Int) = (((a shr sh) and 0xFF) + ((((b shr sh) and 0xFF) - ((a shr sh) and 0xFF)) * k)).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun scale(rgb: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        return ((((rgb shr 16) and 0xFF) * k).toInt() shl 16) or ((((rgb shr 8) and 0xFF) * k).toInt() shl 8) or ((rgb and 0xFF) * k).toInt()
    }

    fun maxRgb(a: Int, b: Int) =
        (maxOf((a shr 16) and 0xFF, (b shr 16) and 0xFF) shl 16) or (maxOf((a shr 8) and 0xFF, (b shr 8) and 0xFF) shl 8) or maxOf(a and 0xFF, b and 0xFF)
}
