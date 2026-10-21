package com.wledcontroller.app.house

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** The shadow figures that move about a lit house, and how each one moves. */
enum class Figure(val move: Move, val noun: String, val plural: String) {
    GHOST(Move.FLOAT, "ghost", "ghosts"),
    CLOWN(Move.WALK, "clown", "clowns"),
    SPIDER(Move.CRAWL, "spider", "spiders"),
    WITCH(Move.FLY, "witch", "witches"),
    SLEIGH(Move.FLY, "sleigh", "sleighs"),
    BUNNY(Move.HOP, "bunny", "bunnies"),
    TURKEY(Move.WALK, "turkey", "turkeys"),
    HEART(Move.RISE, "heart", "hearts"),
    SHAMROCK(Move.DRIFT, "shamrock", "shamrocks"),
    EAGLE(Move.FLY, "eagle", "eagles"),
    SKULL(Move.FLOAT, "skull", "skulls"),
    PUMPKIN(Move.HOP, "pumpkin", "pumpkins"),
}

enum class Move { FLOAT, WALK, HOP, CRAWL, FLY, RISE, DRIFT }

/**
 * Draws shadow figures: the whole house glows in the effect's colours except where the figures'
 * shadows are. Figures are built from circles, lines and triangles in their own units (1 = the
 * figure's width; v runs down from the top), so they keep their shape at any size and can move
 * their legs, wings and capes as they go.
 */
internal object ShadowFigures {
    /** Width / height of a typical house photo, so figures aren't squashed. */
    private const val ASPECT = 1.5f
    private const val MAX_FIGURES = 8

    /** How far down a figure's feet (or a pumpkin's base) are, in its own units. */
    private val Figure.foot get() = when (this) {
        Figure.CLOWN -> 1.5f
        Figure.TURKEY -> 1.04f
        Figure.BUNNY -> 0.99f
        Figure.PUMPKIN -> 0.92f
        else -> 1f
    }

    /** Where one figure is: its origin on the house, whether it faces left, and its turn (radians). */
    private class Pose(val gx: Float, val gy: Float, val flip: Boolean, val rot: Float = 0f, val dangle: Boolean = false)

    fun render(p: HouseEffectParams, figures: List<Figure>, c: List<Int>, x: Float, y: Float, t: Float): Int {
        val lit = HouseRenderer.palette(c, x * 0.6f + t * 0.02f)
        val speed = 0.15f + p.speed * 1.6f
        val w = 0.06f + p.size * 0.26f
        val tt = t * (0.6f + speed)
        val s = Shape()
        var shade = 0f
        var glow = 0f
        for (k in 0 until p.count.coerceIn(1, MAX_FIGURES)) {
            val fig = figures[k % figures.size]
            val pose = pose(fig.move, k, t, speed, w, fig.foot)
            var u = (x - pose.gx) / w
            var v = (y - pose.gy) / (w * ASPECT)
            // A spider hanging on its thread: the thread runs up from it to the top of the house.
            if (pose.dangle && abs(u) < 0.6f && v < 0.3f) {
                shade = maxOf(shade, s.coverage(0.03f - abs(u)))
            }
            if (pose.flip) u = -u
            if (pose.rot != 0f) {
                val cy = 0.5f
                val du = u; val dv = v - cy
                val cr = cos(-pose.rot); val sr = sin(-pose.rot)
                u = du * cr - dv * sr
                v = du * sr + dv * cr + cy
            }
            if (!fig.near(u, v)) continue
            s.at(u, v)
            draw(fig, s, tt + k * 1.7f, k)
            shade = maxOf(shade, s.shadow)
            glow = maxOf(glow, s.lit)
        }
        val dark = HouseRenderer.scale(lit, 1f - shade * 0.96f)
        return if (glow > 0f) HouseRenderer.maxRgb(dark, HouseRenderer.scale(c.getOrElse(1) { c[0] }, 0.6f * glow)) else dark
    }

    /** A quick test that ([u], [v]) could be on the figure at all. */
    private fun Figure.near(u: Float, v: Float): Boolean = when (this) {
        Figure.SLEIGH -> u in -0.8f..1.6f && v in -0.3f..0.9f
        Figure.WITCH -> u in -0.9f..0.7f && v in -0.4f..1.0f
        Figure.CLOWN -> u in -0.7f..0.7f && v in -0.2f..1.6f
        else -> u in -0.8f..0.8f && v in -0.4f..1.5f
    }

    /** Across the house and round again, in either direction; returns x and whether it faces left. */
    private fun cross(k: Int, t: Float, vel: Float): Pair<Float, Boolean> {
        val x = -0.3f + HouseRenderer.frac(HouseRenderer.hash(k, 5) + t * vel * (0.7f + HouseRenderer.hash(k, 8) * 0.6f)) * 1.6f
        val left = HouseRenderer.hash(k, 6) < 0.5f
        return (if (left) 1f - x else x) to left
    }

    private fun pose(move: Move, k: Int, t: Float, speed: Float, w: Float, foot: Float): Pose {
        fun h(salt: Int) = HouseRenderer.hash(k, salt)
        // Walkers and hoppers each go along a line of their own: the first along the bottom of
        // the house, the others higher up, so together they cover the display top to bottom.
        val ground = 0.98f - (k % 3) * 0.26f - 0.03f * h(7)
        val standing = ground - foot * w * ASPECT
        return when (move) {
            Move.FLOAT -> {
                val gx = 0.5f + 0.45f * sin(t * speed * (0.22f + h(1) * 0.18f) + h(2) * 6.283f)
                val gy = 0.05f + 0.55f * (0.5f + 0.5f * sin(t * speed * (0.17f + h(3) * 0.14f) + h(4) * 6.283f)) + 0.02f * sin(t * 2.6f + k)
                Pose(gx, gy, false)
            }
            Move.WALK -> {
                val (gx, left) = cross(k, t, speed * 0.05f)
                val bounce = abs(sin(t * (1.5f + speed * 2f) + k)) * 0.05f
                Pose(gx, standing - bounce, left)
            }
            Move.HOP -> {
                val (gx, left) = cross(k, t, speed * 0.06f)
                val f = HouseRenderer.frac(t * (0.4f + speed * 0.6f) + h(9))
                Pose(gx, standing - sin(f * PI.toFloat()) * 0.12f, left)
            }
            Move.CRAWL -> if (k % 2 == 0) {
                // Crawling across, head first.
                val (gx, left) = cross(k, t, speed * 0.04f)
                Pose(gx, 0.08f + 0.65f * h(7) + 0.015f * sin(t * 3f + k), false, rot = if (left) -PI.toFloat() / 2 else PI.toFloat() / 2)
            } else {
                // Letting itself down on a thread and climbing back up.
                val gy = 0.02f + 0.5f * (0.5f - 0.5f * cos(t * speed * 0.5f + h(4) * 6.283f))
                Pose(0.08f + 0.84f * h(3), gy, false, rot = PI.toFloat(), dangle = true)
            }
            Move.FLY -> {
                val (gx, left) = cross(k, t, speed * 0.08f)
                Pose(gx, 0.04f + 0.42f * h(7) + 0.07f * sin(t * speed * 0.9f + k * 2f), left)
            }
            Move.RISE -> {
                val gy = 1.1f - HouseRenderer.frac(h(5) + t * speed * 0.05f) * 1.4f
                Pose(0.1f + 0.8f * h(3) + 0.05f * sin(t * 0.8f + k), gy, false)
            }
            Move.DRIFT -> {
                val gy = -0.25f + HouseRenderer.frac(h(5) + t * speed * 0.04f) * 1.45f
                Pose(0.1f + 0.8f * h(3) + 0.08f * sin(t * 0.6f + k), gy, false, rot = t * speed * 0.6f + k)
            }
        }
    }

    // ------------------------------------------------------------------ the figures

    private fun draw(fig: Figure, s: Shape, t: Float, k: Int) = when (fig) {
        Figure.GHOST -> ghost(s, t)
        Figure.CLOWN -> clown(s, t, k)
        Figure.SPIDER -> spider(s, t)
        Figure.WITCH -> witch(s, t)
        Figure.SLEIGH -> sleigh(s, t)
        Figure.BUNNY -> bunny(s)
        Figure.TURKEY -> turkey(s, t)
        Figure.HEART -> heart(s, t)
        Figure.SHAMROCK -> shamrock(s)
        Figure.EAGLE -> eagle(s, t)
        Figure.SKULL -> skull(s, t, k)
        Figure.PUMPKIN -> pumpkin(s, t, k)
    }

    private fun ghost(s: Shape, t: Float) {
        // Round head, then a body down to a rippling hem.
        s.circle(0f, 0.5f, 0.5f)
        s.tri(-0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 1.1f)
        s.tri(0.5f, 0.5f, 0.5f, 1.1f, -0.5f, 1.1f)
        val hem = 1.1f + 0.08f * sin(s.u * 6f * PI.toFloat() + t * 4f)
        if (s.v > hem) s.dark = s.dark * ((1.18f - s.v) / 0.08f).coerceIn(0f, 1f)
        s.glowCircle(-0.17f, 0.45f, 0.09f); s.glowCircle(0.17f, 0.45f, 0.09f)
    }

    private fun clown(s: Shape, t: Float, k: Int) {
        val step = sin(t * 6f)
        s.tri(-0.15f, 0.35f, 0.15f, 0.35f, 0f, 0.02f)            // pointed hat
        s.circle(0f, 0.02f, 0.07f)                                 // pompom
        s.circle(0f, 0.5f, 0.16f)                                  // head
        s.circle(-0.21f, 0.46f, 0.1f); s.circle(0.21f, 0.46f, 0.1f) // puffs of hair
        for (i in 0..3) s.circle(-0.18f + i * 0.12f, 0.72f, 0.07f) // ruffled collar
        s.circle(0f, 0.95f, 0.2f)                                  // round belly
        s.tri(-0.25f, 1.2f, 0.25f, 1.2f, 0f, 0.72f)
        s.seg(-0.08f, 1.15f, -0.12f + step * 0.06f, 1.4f, 0.05f)   // legs, walking
        s.seg(0.08f, 1.15f, 0.12f - step * 0.06f, 1.4f, 0.05f)
        s.circle(-0.19f + step * 0.06f, 1.42f, 0.08f)              // big shoes
        s.circle(0.19f - step * 0.06f, 1.42f, 0.08f)
        // Juggling: arms up, three balls going round overhead.
        val toss = sin(t * 8f) * 0.04f
        s.seg(-0.17f, 0.8f, -0.36f, 0.62f + toss, 0.045f)
        s.seg(0.17f, 0.8f, 0.36f, 0.62f - toss, 0.045f)
        for (b in 0..2) {
            val a = t * 4f + b * 2.094f + k
            s.circle(0.36f * cos(a), 0.2f - 0.22f * abs(sin(a)), 0.055f)
        }
        s.glowCircle(-0.06f, 0.47f, 0.03f); s.glowCircle(0.06f, 0.47f, 0.03f); s.glowCircle(0f, 0.53f, 0.035f)
    }

    private fun spider(s: Shape, t: Float) {
        s.circle(0f, 0.68f, 0.2f)       // abdomen
        s.circle(0f, 0.38f, 0.12f)      // head and thorax
        for (side in listOf(-1f, 1f)) for (i in 0..3) {
            val hipY = 0.32f + 0.08f * i
            val swing = 0.07f * sin(t * 9f + i * 1.6f + if (side > 0) PI.toFloat() else 0f)
            val kx = side * 0.32f; val ky = hipY - 0.1f + swing + (i - 1.5f) * 0.06f
            val fx = side * 0.5f; val fy = hipY + 0.12f + swing * 1.3f + (i - 1.5f) * 0.16f
            s.seg(side * 0.06f, hipY, kx, ky, 0.03f)
            s.seg(kx, ky, fx, fy, 0.025f)
        }
        s.glowCircle(-0.045f, 0.32f, 0.025f); s.glowCircle(0.045f, 0.32f, 0.025f)
    }

    private fun witch(s: Shape, t: Float) {
        val flap = 0.06f * sin(t * 7f)
        s.seg(-0.55f, 0.62f, 0.5f, 0.52f, 0.03f)                       // broomstick
        s.tri(-0.5f, 0.6f, -0.78f, 0.45f + flap * 0.5f, -0.78f, 0.8f)  // bristles
        s.tri(-0.25f, 0.6f, 0.15f, 0.6f, -0.02f, 0.18f)                // body, sitting
        s.tri(-0.05f, 0.25f, -0.48f, 0.36f + flap, -0.15f, 0.56f)      // cape, flying back
        s.circle(0f, 0.15f, 0.08f)                                      // head
        s.tri(-0.05f, 0.1f, -0.22f, 0.3f + flap, -0.04f, 0.26f)        // hair
        s.tri(-0.1f, 0.1f, 0.12f, 0.1f, -0.1f, -0.24f)                 // pointed hat, bent back
        s.seg(-0.19f, 0.1f, 0.21f, 0.1f, 0.025f)                        // hat brim
        s.tri(0.07f, 0.13f, 0.07f, 0.19f, 0.17f, 0.18f)                // long nose
        s.seg(-0.02f, 0.35f, 0.22f, 0.55f, 0.03f)                       // arm on the broom
        s.seg(0f, 0.58f, 0.13f, 0.74f, 0.03f)                           // leg
        s.glowCircle(0.04f, 0.13f, 0.022f)
    }

    private fun sleigh(s: Shape, t: Float) {
        // The sleigh, with Santa and his sack.
        s.tri(-0.5f, 0.55f, 0.2f, 0.55f, -0.45f, 0.25f)
        s.circle(-0.35f, 0.42f, 0.15f)
        s.seg(-0.55f, 0.68f, 0.25f, 0.68f, 0.025f)
        s.seg(0.25f, 0.68f, 0.33f, 0.59f, 0.025f)
        s.seg(-0.3f, 0.55f, -0.3f, 0.68f, 0.02f); s.seg(0.05f, 0.55f, 0.05f, 0.68f, 0.02f)
        s.circle(-0.2f, 0.3f, 0.13f)
        s.circle(-0.15f, 0.12f, 0.07f)
        s.tri(-0.22f, 0.1f, -0.06f, 0.1f, -0.26f, -0.05f)
        s.circle(-0.43f, 0.2f, 0.12f)
        s.seg(-0.1f, 0.25f, 1.0f, 0.3f, 0.012f)                         // reins
        // Two reindeer galloping ahead; the lead one is Rudolph.
        for ((j, x0) in listOf(0.6f, 1.05f).withIndex()) {
            val g = 0.06f * sin(t * 9f + j * 1.3f)
            s.circle(x0, 0.35f, 0.12f); s.circle(x0 + 0.12f, 0.33f, 0.1f)
            s.seg(x0 + 0.18f, 0.3f, x0 + 0.27f, 0.13f, 0.04f)
            s.circle(x0 + 0.3f, 0.1f, 0.06f)
            s.seg(x0 + 0.28f, 0.06f, x0 + 0.22f, -0.09f, 0.015f)
            s.seg(x0 + 0.24f, -0.02f, x0 + 0.16f, -0.04f, 0.012f)
            s.seg(x0 - 0.05f, 0.42f, x0 - 0.13f + g, 0.6f, 0.025f)
            s.seg(x0 + 0.15f, 0.42f, x0 + 0.23f - g, 0.6f, 0.025f)
            s.circle(x0 - 0.13f, 0.3f, 0.03f)
            if (j == 1) s.glowCircle(x0 + 0.36f, 0.11f, 0.035f)
        }
    }

    private fun bunny(s: Shape) {
        s.circle(0f, 0.65f, 0.25f)                       // body
        s.circle(0.25f, 0.4f, 0.15f)                     // head
        s.seg(0.21f, 0.28f, 0.13f, -0.06f, 0.05f)        // long ears
        s.seg(0.29f, 0.28f, 0.33f, -0.06f, 0.05f)
        s.circle(-0.27f, 0.58f, 0.08f)                   // tail
        s.circle(-0.05f, 0.85f, 0.14f)                   // back leg
        s.seg(0.18f, 0.75f, 0.26f, 0.9f, 0.04f)          // front paw
        s.glowCircle(0.3f, 0.37f, 0.03f)
    }

    private fun turkey(s: Shape, t: Float) {
        // Fanned tail feathers behind.
        for (i in 0..6) {
            val a = (100f + i * 18f) * PI.toFloat() / 180f
            s.seg(-0.08f, 0.58f, -0.08f + 0.45f * cos(a), 0.58f - 0.45f * sin(a), 0.07f)
        }
        s.circle(0.05f, 0.62f, 0.22f)                                  // body
        s.seg(0.2f, 0.52f, 0.3f, 0.25f, 0.05f)                         // neck
        s.circle(0.32f, 0.2f, 0.07f)                                   // head
        s.tri(0.37f, 0.17f, 0.37f, 0.23f, 0.46f, 0.21f)                // beak
        s.circle(0.37f, 0.29f, 0.035f)                                 // wattle
        val g = 0.05f * sin(t * 7f)
        s.seg(0f, 0.8f, -0.03f + g, 1.02f, 0.025f)                     // legs, strutting
        s.seg(0.1f, 0.8f, 0.13f - g, 1.02f, 0.025f)
        s.glowCircle(0.33f, 0.18f, 0.022f)
    }

    private fun heart(s: Shape, t: Float) {
        val beat = 1f + 0.06f * sin(t * 5f)
        s.circle(-0.15f * beat, 0.35f, 0.2f * beat)
        s.circle(0.15f * beat, 0.35f, 0.2f * beat)
        s.tri(-0.34f * beat, 0.42f, 0.34f * beat, 0.42f, 0f, 0.42f + 0.43f * beat)
    }

    private fun shamrock(s: Shape) {
        for ((cx, cy) in listOf(0f to 0.27f, -0.19f to 0.47f, 0.19f to 0.47f)) {
            // Each leaf is a little heart pointing to the middle.
            s.circle(cx - 0.07f, cy, 0.1f); s.circle(cx + 0.07f, cy, 0.1f)
        }
        s.circle(0f, 0.43f, 0.09f)
        s.seg(0f, 0.5f, 0.08f, 0.88f, 0.03f)
    }

    private fun eagle(s: Shape, t: Float) {
        val f = sin(t * 5f)
        s.circle(0f, 0.5f, 0.12f)                                         // body
        s.tri(-0.1f, 0.45f, -0.1f, 0.58f, -0.42f, 0.6f)                   // tail
        s.circle(0.2f, 0.42f, 0.07f)                                      // head
        s.tri(0.25f, 0.4f, 0.25f, 0.46f, 0.33f, 0.45f)                    // hooked beak
        // Wings beating: tips sweep from high above to below the body.
        val tipY = 0.5f - 0.48f * f
        s.tri(-0.12f, 0.5f, 0.12f, 0.5f, -0.1f, tipY)
        s.tri(-0.12f, 0.5f, -0.1f, tipY, -0.32f, tipY + 0.08f)
        s.glowCircle(0.21f, 0.41f, 0.02f)
    }

    private fun skull(s: Shape, t: Float, k: Int) {
        // The jaw chatters now and then.
        val chatter = 0.05f * maxOf(0f, sin(t * 11f)) * if (sin(t * 0.9f + k) > 0.2f) 1f else 0f
        s.circle(0f, 0.4f, 0.42f)                                   // cranium
        s.seg(-0.14f, 0.72f, 0.14f, 0.72f, 0.17f)                   // cheekbones and upper teeth
        s.seg(-0.1f, 0.93f + chatter, 0.1f, 0.93f + chatter, 0.12f) // jaw
        // Holes the lit house shows through: eye sockets, nose, the gaps between the teeth.
        s.cutCircle(-0.18f, 0.47f, 0.13f); s.cutCircle(0.18f, 0.47f, 0.13f)
        s.cutTri(-0.07f, 0.72f, 0.07f, 0.72f, 0f, 0.58f)
        if (chatter > 0.005f) s.cutSeg(-0.22f, 0.87f + chatter / 2, 0.22f, 0.87f + chatter / 2, chatter * 0.45f)
        for (i in -2..2) s.cutSeg(i * 0.07f, 0.82f, i * 0.07f, 0.9f + chatter, 0.012f)
        // A cold glint deep in each eye.
        s.glowCircle(-0.18f, 0.49f, 0.04f); s.glowCircle(0.18f, 0.49f, 0.04f)
    }

    private fun pumpkin(s: Shape, t: Float, k: Int) {
        s.circle(-0.24f, 0.56f, 0.3f); s.circle(0.24f, 0.56f, 0.3f) // the ribbed sides
        s.circle(0f, 0.54f, 0.37f)
        s.seg(0f, 0.22f, 0.07f, 0.04f, 0.05f)                      // stem
        s.seg(0.07f, 0.06f, 0.2f, 0.02f, 0.02f)                    // curly vine
        // The carved face glows like a candle inside: angry eyes, a nose and a jagged grin.
        val flicker = 0.7f + 0.3f * sin(t * 13f + k) * sin(t * 7.3f + k * 2f)
        s.glowTri(-0.36f, 0.38f, -0.07f, 0.5f, -0.3f, 0.56f, flicker)
        s.glowTri(0.36f, 0.38f, 0.07f, 0.5f, 0.3f, 0.56f, flicker)
        s.glowTri(-0.06f, 0.66f, 0.06f, 0.66f, 0f, 0.56f, flicker)
        s.glowTri(-0.4f, 0.66f, 0.4f, 0.66f, 0f, 0.9f, flicker)
        s.glowTri(-0.4f, 0.66f, -0.18f, 0.72f, -0.3f, 0.82f, flicker)
        s.glowTri(0.4f, 0.66f, 0.18f, 0.72f, 0.3f, 0.82f, flicker)
        // Fangs left in the grin.
        s.blockTri(-0.17f, 0.65f, -0.05f, 0.65f, -0.11f, 0.77f)
        s.blockTri(0.05f, 0.65f, 0.17f, 0.65f, 0.11f, 0.77f)
        s.blockTri(-0.08f, 0.9f, 0.08f, 0.9f, 0f, 0.8f)
    }

    /** Accumulates how much a point is in shadow (and in a glowing eye) as shapes are added. */
    private class Shape {
        var u = 0f; var v = 0f
        var dark = 0f; var glow = 0f
        /** Holes in the shadow (lit house shows through) and parts of the glow covered over. */
        private var cut = 0f; private var block = 0f
        val shadow get() = dark * (1f - cut)
        val lit get() = glow * (1f - block)
        /** Edges fade over this distance, so shapes look smooth on sparse lights. */
        private val soft = 0.05f

        fun at(u: Float, v: Float) { this.u = u; this.v = v; dark = 0f; glow = 0f; cut = 0f; block = 0f }
        fun coverage(inside: Float) = (inside / soft + 0.5f).coerceIn(0f, 1f)

        fun circle(cx: Float, cy: Float, r: Float) { dark = maxOf(dark, coverage(r - hypot(u - cx, v - cy))) }
        fun glowCircle(cx: Float, cy: Float, r: Float) { glow = maxOf(glow, coverage(r - hypot(u - cx, v - cy))) }
        fun cutCircle(cx: Float, cy: Float, r: Float) { cut = maxOf(cut, coverage(r - hypot(u - cx, v - cy))) }

        /** A thick line from (ax, ay) to (bx, by). */
        fun seg(ax: Float, ay: Float, bx: Float, by: Float, thick: Float) { dark = maxOf(dark, segIn(ax, ay, bx, by, thick)) }
        fun cutSeg(ax: Float, ay: Float, bx: Float, by: Float, thick: Float) { cut = maxOf(cut, segIn(ax, ay, bx, by, thick)) }

        private fun segIn(ax: Float, ay: Float, bx: Float, by: Float, thick: Float): Float {
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val k = if (len2 <= 0f) 0f else (((u - ax) * dx + (v - ay) * dy) / len2).coerceIn(0f, 1f)
            return coverage(thick - hypot(u - (ax + k * dx), v - (ay + k * dy)))
        }

        fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) { dark = maxOf(dark, triIn(ax, ay, bx, by, cx, cy)) }
        fun cutTri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) { cut = maxOf(cut, triIn(ax, ay, bx, by, cx, cy)) }
        fun glowTri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, level: Float = 1f) {
            glow = maxOf(glow, triIn(ax, ay, bx, by, cx, cy) * level)
        }
        fun blockTri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) { block = maxOf(block, triIn(ax, ay, bx, by, cx, cy)) }

        private fun triIn(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
            // Distance inside the nearest edge (positive inside), whichever way round the corners go.
            val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
            if (area == 0f) return 0f
            val sgn = if (area > 0f) 1f else -1f
            fun edge(px: Float, py: Float, qx: Float, qy: Float): Float {
                val len = sqrt((qx - px) * (qx - px) + (qy - py) * (qy - py)).coerceAtLeast(1e-5f)
                return sgn * ((qx - px) * (v - py) - (qy - py) * (u - px)) / len
            }
            return coverage(minOf(edge(ax, ay, bx, by), edge(bx, by, cx, cy), edge(cx, cy, ax, ay)))
        }
    }
}
