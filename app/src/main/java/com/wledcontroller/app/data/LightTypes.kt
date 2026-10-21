package com.wledcontroller.app.data

import com.wledcontroller.app.house.HouseEffect
import com.wledcontroller.app.house.PropType
import com.wledcontroller.app.show.SeqEffect

/** Kinds of lights, for finding the effects that look best on each. */
enum class LightType(val label: String, val description: String) {
    SINGLE_STRIP("Single strip", "One line of closely spaced LEDs — rooflines, eaves, outlines. Smooth movement along the line."),
    PIXEL_STRING("Pixel string", "Spaced bulbs or bullet pixels, where each light reads as its own dot. Twinkles, chases and solid colours."),
    MATRIX("Matrix / panel", "A grid of LEDs — panels, garage-door matrices. Pictures, text and 2D effects."),
    MEGA_TREE("Mega tree", "Strings fanned into a tree. Effects that fall, rise, spin or spiral."),
    ARCH_CANE("Arches & canes", "Short props that read as one shape — arches, candy canes, stars. Effects that run end to end."),
    SPOT_FLOOD("Spots & floods", "Single bulbs, floods and DMX lights, where the whole light changes at once. Fades, pulses and colour changes."),
}

/** Which effects suit which kinds of lights, for every effect list in the app. */
object EffectFit {

    private fun names(vararg n: String) = n.map { it.lowercase() }.toSet()

    // WLED's own effects (and common audio-reactive ones), by name.
    private val strip = names(
        "Wipe", "Wipe Random", "Sweep", "Sweep Random", "Rainbow Runner", "Scan", "Scan Dual", "Running", "Running Dual", "Saw",
        "Chase", "Chase Random", "Chase Rainbow", "Chase Flash", "Chase Flash Rnd", "Chase 2", "Android", "Aurora", "Stream", "Stream 2",
        "Scanner", "Scanner Dual", "Lighthouse", "Tetrix", "Gradient", "Loading", "Rolling Balls", "Two Dots", "Tri Wipe", "ICU",
        "Multi Comet", "Oscillate", "Pride 2015", "Juggle", "Palette", "Fire 2012", "Colorwaves", "Bpm", "Fill Noise", "Noise 1",
        "Noise 2", "Noise 3", "Noise 4", "Lake", "Meteor", "Meteor Smooth", "Ripple", "Ripple Rainbow", "Fireworks 1D", "Bouncing Balls",
        "Sinelon", "Sinelon Dual", "Sinelon Rainbow", "Drip", "Plasma", "Percent", "Pacifica", "Phased", "Phased Noise", "Sine",
        "Noise Pal", "Flow", "Chunchun", "Dancing Shadows", "Washing Machine", "Blends", "Dynamic Smooth", "Rain", "Spots Fade",
        "Theater Rainbow", "Comet", "Larson Scanner",
        "Gravimeter", "Gravcenter", "Gravcentric", "Juggles", "Midnoise", "Noisemeter", "Freqwave", "Freqmap", "Waterfall",
        "Pixelwave", "Puddlepeak", "Puddles", "Rocktaves", "DJ Light", "Ripple Peak", "Noisefire", "Plasmoid", "Blurz",
    )
    private val pixels = names(
        "Solid", "Blink", "Twinkle", "Twinklefox", "Twinklecat", "Twinkleup", "Colortwinkles", "Fairy", "Fairytwinkle", "Theater",
        "Chase 3", "Fade", "Breathe", "Candle", "Candle Multi", "Fireworks", "Glitter", "Solid Glitter", "Sparkle", "Sparkle Dark",
        "Sparkle+", "Dissolve", "Dissolve Rnd", "Random Colors", "Colorful", "Halloween Eyes", "Merry Christmas", "Railway", "Tri Fade",
        "Solid Pattern", "Solid Pattern Tri", "Fire Flicker", "Spots", "Dynamic", "Popcorn", "Lightning", "Heartbeat", "Running",
        "Traffic Light", "Halloween", "Christmas", "Pixels",
    )
    private val tree = names(
        "Fireworks Starburst", "Rain", "Drip", "Meteor", "Meteor Smooth", "Sinelon", "Rolling Balls", "Bouncing Balls", "Popcorn",
        "Fire 2012", "Colorwaves", "Rainbow Runner", "Spots", "Sweep", "Twinklefox", "Colortwinkles", "Lake", "Aurora", "Pacifica",
        "Ripple", "Plasma", "Theater Rainbow", "Chase Rainbow", "Juggle", "Multi Comet", "Stream", "Flow", "Glitter", "Sparkle",
    )
    private val arches = names(
        "Wipe", "Sweep", "Scanner", "Scan", "Running", "Chase", "Theater", "Theater Rainbow", "Rainbow Runner", "Merry Christmas",
        "Railway", "Bouncing Balls", "Lighthouse", "Two Dots", "Tri Wipe", "Loading", "Percent", "Meteor", "Sinelon", "Colorloop",
        "Oscillate", "Gradient", "ICU", "Chase 2", "Solid Pattern", "Larson Scanner", "Android", "Comet",
    )
    private val spots = names(
        "Solid", "Blink", "Breathe", "Fade", "Colorloop", "Rainbow", "Strobe", "Strobe Rainbow", "Strobe Mega", "Blink Rainbow",
        "Lightning", "Candle", "Fire Flicker", "Heartbeat", "Tri Fade", "Random Colors", "Sunrise", "TV Simulator", "Dynamic",
    )

    /** The kinds of lights a WLED effect suits (2D effects suit matrices and mega trees). */
    fun wled(name: String, meta: EffectMeta? = null): Set<LightType> {
        val n = name.trim().lowercase()
        val is2D = meta?.is2D == true || n.startsWith("2d ")
        return buildSet {
            if (is2D) { add(LightType.MATRIX); add(LightType.MEGA_TREE) }
            if (n in strip) add(LightType.SINGLE_STRIP)
            if (n in pixels) add(LightType.PIXEL_STRING)
            if (n in tree) add(LightType.MEGA_TREE)
            if (n in arches) add(LightType.ARCH_CANE)
            if (n in spots) add(LightType.SPOT_FLOOD)
        }
    }

    /** The kinds of lights a show effect suits. */
    fun show(e: SeqEffect): Set<LightType> {
        val s = LightType.SINGLE_STRIP; val p = LightType.PIXEL_STRING; val m = LightType.MATRIX
        val t = LightType.MEGA_TREE; val a = LightType.ARCH_CANE; val f = LightType.SPOT_FLOOD
        return when (e) {
            SeqEffect.ON -> setOf(s, p, m, t, a, f)
            SeqEffect.WIPE -> setOf(s, a, t)
            SeqEffect.CHASE -> setOf(s, p, a)
            SeqEffect.COLOR_WAVE -> setOf(s, t, m)
            SeqEffect.RAINBOW -> setOf(s, t, m, a)
            SeqEffect.COMET -> setOf(s, a, t)
            SeqEffect.BARS -> setOf(m, t)
            SeqEffect.PULSE -> setOf(f, p, s)
            SeqEffect.TWINKLE -> setOf(p, t)
            SeqEffect.SPARKLE -> setOf(p, t, m)
            SeqEffect.SHIMMER -> setOf(p, f)
            SeqEffect.STROBE -> setOf(f, p)
            SeqEffect.FIREWORKS -> setOf(m, t)
            SeqEffect.SNOW -> setOf(m, t, p)
            SeqEffect.SPIRALS -> setOf(t, m)
            SeqEffect.BUTTERFLY -> setOf(m, t)
            SeqEffect.METEORS -> setOf(t, m, s)
            SeqEffect.TEXT -> setOf(m)
            SeqEffect.PICTURE -> setOf(m)
            SeqEffect.VU_METER -> setOf(s, a, t)
            SeqEffect.MUSIC_PULSE -> setOf(f, p, s)
            SeqEffect.BEAT_FLASH -> setOf(f, p)
            SeqEffect.FIRE -> setOf(m, s, t)
            SeqEffect.RIPPLE -> setOf(m)
            SeqEffect.SHOCKWAVE -> setOf(m, t)
            SeqEffect.CURTAIN -> setOf(m, t, s)
            SeqEffect.PINWHEEL -> setOf(m, t)
            SeqEffect.GALAXY -> setOf(m)
            SeqEffect.PLASMA -> setOf(m, s)
            SeqEffect.MARQUEE -> setOf(s, p)
            SeqEffect.GARLANDS -> setOf(t, s)
            SeqEffect.SING -> setOf(m)
            SeqEffect.OFF -> setOf(s, p, m, t, a, f)
            SeqEffect.COLOR_WASH -> setOf(f, p, s, a, t, m)
            SeqEffect.FILL -> setOf(s, a, t)
            SeqEffect.CANDLE -> setOf(p, f)
            SeqEffect.CIRCLES -> setOf(m)
            SeqEffect.FAN -> setOf(m, t)
            SeqEffect.KALEIDOSCOPE -> setOf(m)
            SeqEffect.LIFE -> setOf(m)
            SeqEffect.LIGHTNING -> setOf(m, t, f)
            SeqEffect.LINES -> setOf(m)
            SeqEffect.LIQUID -> setOf(m, t)
            SeqEffect.MORPH -> setOf(s, a, t)
            SeqEffect.SHAPE -> setOf(m)
            SeqEffect.SINGLE_STRAND -> setOf(s, a, p)
            SeqEffect.SNOWFLAKES -> setOf(m, t)
            SeqEffect.SPIROGRAPH -> setOf(m)
            SeqEffect.TENDRIL -> setOf(m)
            SeqEffect.TREE -> setOf(t)
            SeqEffect.WARP -> setOf(m, t)
            SeqEffect.WAVE -> setOf(m, t)
            SeqEffect.PIANO -> setOf(m, s)
            SeqEffect.VIDEO -> setOf(m, t)
            SeqEffect.MUSIC -> setOf(m, t, a)
            SeqEffect.STATE -> setOf(m, a, p)
            SeqEffect.SKETCH -> setOf(m)
            SeqEffect.SHADER -> setOf(m, t, s)
        }
    }

    /** The kinds of lights a whole-house effect looks best on (most of the house being that kind). */
    fun house(e: HouseEffect): Set<LightType> {
        val s = LightType.SINGLE_STRIP; val p = LightType.PIXEL_STRING; val m = LightType.MATRIX
        val t = LightType.MEGA_TREE; val f = LightType.SPOT_FLOOD
        if (e.isShadow) return setOf(s, p, m)
        return when (e) {
            HouseEffect.WAVE, HouseEffect.RAINBOW -> setOf(s, m, t)
            HouseEffect.GLOW -> setOf(s, p, f)
            HouseEffect.WIPE -> setOf(s, p, m)
            HouseEffect.CHASE -> setOf(p, s)
            HouseEffect.RISE -> setOf(s, m, t)
            HouseEffect.PULSE -> setOf(m, s)
            HouseEffect.TWINKLE -> setOf(p, t)
            HouseEffect.FIREWORKS -> setOf(m, p, t)
            HouseEffect.SNOW -> setOf(p, m, t)
            HouseEffect.BREATHE -> setOf(f, p)
            HouseEffect.SOLID -> setOf(f, p, s)
            else -> emptySet()
        }
    }

    /** The light type matching a controller's light profile. */
    fun of(profile: LightProfile): LightType = when (profile) {
        LightProfile.STRIP -> LightType.SINGLE_STRIP
        LightProfile.BULLETS -> LightType.PIXEL_STRING
        LightProfile.MATRIX -> LightType.MATRIX
    }

    /** The light type a prop on the house map is. */
    fun of(prop: PropType): LightType = when (prop) {
        PropType.TREE -> LightType.MEGA_TREE
        PropType.MATRIX -> LightType.MATRIX
        PropType.ARCH, PropType.CANDY_CANE, PropType.STAR -> LightType.ARCH_CANE
        PropType.BUSH -> LightType.PIXEL_STRING
        else -> LightType.SINGLE_STRIP
    }
}
