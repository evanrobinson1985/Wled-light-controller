package com.wledcontroller.app.house

import com.wledcontroller.app.house.HouseRenderer.frac
import com.wledcontroller.app.house.HouseRenderer.hash
import com.wledcontroller.app.house.HouseRenderer.lerp
import com.wledcontroller.app.house.HouseRenderer.maxRgb
import com.wledcontroller.app.house.HouseRenderer.palette
import com.wledcontroller.app.house.HouseRenderer.scale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * The holiday lighting effects (as opposed to the holiday shadows, which move over one):
 * each a whole-house animation in its holiday's colours. Same inputs as [HouseRenderer.color]:
 * the LED's place on the house ([x] across, [y] down, 0..1), the time [t] in seconds, a stable
 * [id] for randomness and its [order] along its run; [sp] the speed and [sz] the features per
 * house width.
 */
internal object HolidayEffects {
    private const val WHITE = 0xFFFFFF

    fun render(e: HouseEffect, c: List<Int>, x: Float, y: Float, t: Float, id: Int, order: Int, sp: Float, sz: Float): Int? {
        fun col(i: Int) = c[i.mod(c.size)]
        return when (e) {
            // Red and white stripes slanting across the house, sliding along.
            HouseEffect.CANDY_CANE -> {
                val s = frac((x * sz * 2f + y * 0.8f) - t * sp * 0.3f)
                // Soft edges so the stripes glide rather than step.
                val f = smooth(abs(s - 0.5f) * 4f - 0.9f)
                lerp(col(0), col(1), f)
            }
            // Big old bulbs in turn, each its own colour, glowing softly unevenly.
            HouseEffect.VINTAGE -> {
                val bulb = col(order)
                scale(bulb, 0.8f + 0.2f * flicker(id, t * sp * 0.6f))
            }
            // A dim stormy sky; now and then lightning flickers across part of the house.
            HouseEffect.THUNDERSTORM -> {
                val sky = scale(palette(c.take(2), x * 0.6f + t * sp * 0.03f), 0.12f + 0.06f * sin(t * 0.4f + x * 3f).toFloat())
                val slot = 3.5f / sp
                val k = floor(t / slot).toInt()
                val at = hash(k, 21) * slot * 0.7f
                val age = t - k * slot - at
                if (age < 0f || age > 0.6f) sky else {
                    // A double flash with a flicker, brightest where it strikes.
                    val env = exp(-age * 9f) + 0.7f * exp(-((age - 0.22f) * (age - 0.22f)) / 0.002f)
                    val near = exp(-((x - hash(k, 23)) * (x - hash(k, 23))) / 0.06f)
                    val on = if (hash(id + floor(age * 30f).toInt(), k) > 0.15f) 1f else 0.4f
                    maxRgb(sky, scale(col(2), (env * near * on).toFloat()))
                }
            }
            // Lights that won't stay on: they stutter and drop out here and there, and now and
            // then the whole house browns out.
            HouseEffect.HAUNTED -> {
                val section = floor(x * 8f).toInt()
                val tick = floor(t * sp * 6f).toInt()
                val dropped = hash(section * 31 + tick, 41) < 0.08f || hash(id, tick) < 0.03f
                val brown = floor(t * sp * 0.25f).toInt().let { w -> hash(w, 43) < 0.3f && frac(t * sp * 0.25f) < 0.12f }
                val v = when {
                    brown -> 0.15f + 0.1f * hash(tick, 47)
                    dropped -> 0.05f
                    else -> 0.75f + 0.25f * flicker(section, t * sp * 2f)
                }
                scale(palette(c, x * 0.5f), v)
            }
            // Jack-o'-lantern candlelight, flickering on every light.
            HouseEffect.PUMPKIN_GLOW -> candle(lerp(col(0), col(1), hash(id, 5) * 0.5f), id, t * sp)
            // Red and white stripes rippling like a flag, with a field of twinkling stars top left.
            HouseEffect.FLAG -> {
                val wave = 0.05f * sin(x * 9f - t * sp * 3f).toFloat()
                val yy = y + wave
                val shade = 0.8f + 0.2f * sin(x * 9f - t * sp * 3f + 1.2f).toFloat()
                if (x < 0.4f && yy < 0.5f) {
                    // The stars.
                    val star = hash(id, 3) < 0.25f && sin(t * sp * 4f + hash(id, 9) * 20f) > -0.2f
                    scale(if (star) col(1) else col(2), shade)
                } else {
                    val stripe = floor(yy.coerceIn(0f, 0.999f) * 7f).toInt()
                    scale(if (stripe % 2 == 0) col(0) else col(1), shade)
                }
            }
            // A heartbeat: two quick pulses, a rest, again.
            HouseEffect.HEARTBEAT -> {
                val beat = frac(t * sp * 0.55f)
                val v = exp(-((beat - 0.08f) * (beat - 0.08f)) / 0.0015f) + 0.7f * exp(-((beat - 0.28f) * (beat - 0.28f)) / 0.0015f)
                scale(palette(c, x * sz * 0.3f), (0.12f + 0.88f * v.toFloat()).coerceAtMost(1f))
            }
            // Golden fizz: bright bubbles rising up the house.
            HouseEffect.CHAMPAGNE -> {
                val lane = floor(x * 50f).toInt()
                val rise = frac(-y * 0.9f - t * sp * (0.2f + hash(lane, 5) * 0.2f) + hash(lane, 9))
                val bubble = if (rise < 0.05f) 1f - rise / 0.05f else 0f
                val sparkle = if (hash(id, floor(t * sp * 8f).toInt()) > 0.97f) 1f else 0f
                maxRgb(scale(col(0), 0.25f), scale(col(1), maxOf(bubble, sparkle)))
            }
            // Greens rolling across the house, glinting with gold.
            HouseEffect.POT_OF_GOLD -> {
                val base = palette(c.take(2), x * sz * 0.6f - t * sp * 0.15f)
                val glint = sin(t * sp * 3f + hash(id, 13) * 40f).toFloat()
                if (glint > 0.93f) lerp(base, col(2), (glint - 0.93f) / 0.07f) else base
            }
            // Pastel eggs: soft blobs of colour drifting gently.
            HouseEffect.PASTEL -> {
                val pos = x * sz * 0.8f - t * sp * 0.08f + 0.15f * sin(y * 6f + t * sp * 0.5f).toFloat()
                scale(palette(c, pos), 0.75f + 0.25f * sin(t * sp + x * 4f).toFloat())
            }
            // Autumn leaves drifting down, swaying as they fall, in turning colours.
            HouseEffect.LEAVES -> {
                val lane = floor((x + 0.02f * sin(y * 12f + t * sp * 2f).toFloat()) * 30f).toInt()
                val fall = frac(y * 0.7f - t * sp * (0.08f + hash(lane, 5) * 0.08f) + hash(lane, 9))
                val leaf = if (fall < 0.08f) 1f - fall / 0.08f else 0f
                val k = floor(y * 0.7f - t * sp * (0.08f + hash(lane, 5) * 0.08f) + hash(lane, 9)).toInt()
                maxRgb(scale(col(2), 0.1f), scale(col((hash(lane, k) * c.size).toInt()), leaf))
            }
            // A menorah across the house: the middle candle always lit, then one more each
            // night, left to right, until all eight burn, and round again.
            HouseEffect.MENORAH -> {
                val n = 9
                val slot = (x * n).toInt().coerceIn(0, n - 1)
                val inCandle = abs(frac(x * n) - 0.5f) < 0.36f
                val nights = 1 + (floor(t * sp * 0.5f).toInt() % 9)
                val lit = slot == 4 || (if (slot < 4) slot else slot - 1) < nights - 1
                when {
                    !inCandle -> scale(col(0), 0.08f)
                    lit -> candle(col(2), id, t * sp)
                    else -> scale(col(0), 0.25f)
                }
            }
            // A kinara: seven candles, three red, a dark one, three green, lit one by one.
            HouseEffect.KINARA -> {
                val n = 7
                val slot = (x * n).toInt().coerceIn(0, n - 1)
                val inCandle = abs(frac(x * n) - 0.5f) < 0.36f
                val lit = slot < 1 + floor(t * sp * 0.5f).toInt() % 7
                val candleCol = when { slot < 3 -> col(0); slot == 3 -> 0x303030; else -> col(1) }
                when {
                    !inCandle -> scale(col(2), 0.06f)
                    lit -> if (slot == 3) candle(col(2), id, t * sp) else maxRgb(scale(candleCol, 0.5f), candle(col(2), id, t * sp, 0.6f))
                    else -> scale(candleCol, 0.25f)
                }
            }
            // Rows of little oil lamps, each flickering warm, on a deep glow.
            HouseEffect.DIYAS -> {
                if (order % 3 == 0) candle(lerp(col(0), col(2), hash(id, 2)), id, t * sp)
                else scale(col(1), 0.18f + 0.05f * sin(t * sp + x * 5f).toFloat())
            }
            // A golden dragon weaving along the lights over red, its body rippling behind it.
            HouseEffect.DRAGON -> {
                val head = frac(t * sp * 0.12f) * 1.4f - 0.2f
                val behind = head - x
                val body = if (behind in 0f..0.35f) (1f - behind / 0.35f) * (0.6f + 0.4f * sin(behind * 60f - t * sp * 8f).toFloat()) else 0f
                maxRgb(scale(col(0), 0.55f), scale(col(1), body))
            }
            // Northern lights: slow curtains of green, teal and violet drifting overhead.
            HouseEffect.AURORA -> {
                val curtain = sin(x * sz * 3f + t * sp * 0.4f + sin(x * 7f - t * sp * 0.3f)).toFloat()
                val v = (0.5f + 0.5f * curtain) * (0.4f + 0.6f * (1f - y))
                scale(palette(c, x * 0.7f + t * sp * 0.05f + y * 0.3f), 0.1f + 0.9f * v * v)
            }
            else -> null
        }
    }

    /** 0..1 that wanders smoothly over time, different for each [id]. */
    private fun flicker(id: Int, t: Float): Float {
        val a = hash(id, 31) * 10f; val b = hash(id, 37) * 10f
        return (0.5f + 0.3f * sin(t * 2.3f + a) + 0.2f * sin(t * 5.7f + b)).toFloat().coerceIn(0f, 1f)
    }

    /** Candlelight in [color]: mostly steady, dipping and leaping a little. */
    private fun candle(color: Int, id: Int, t: Float, strength: Float = 1f): Int {
        val jump = hash(id, floor(t * 10f).toInt())
        val v = 0.65f + 0.25f * flicker(id, t * 3f) + 0.1f * jump
        return scale(lerp(color, WHITE, 0.05f * jump), v * strength)
    }

    private fun smooth(v: Float): Float {
        val k = v.coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }
}
