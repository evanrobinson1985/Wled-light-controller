package com.wledcontroller.app.show

import com.wledcontroller.app.house.Direction
import kotlinx.serialization.Serializable

/**
 * What a track lights: the whole house, a prop group, one prop (light run), or part of a prop
 * (a submodel: [refId] is "runId/submodel name").
 */
@Serializable
data class TrackTarget(val kind: Kind, val refId: String = "") {
    @Serializable
    enum class Kind { HOUSE, GROUP, PROP, SUBMODEL }

    companion object {
        fun submodel(runId: String, name: String) = TrackTarget(Kind.SUBMODEL, "$runId/$name")
    }
}

@Serializable
enum class SeqEffect(val label: String) {
    ON("On"),
    WIPE("Wipe on"),
    CHASE("Chase"),
    COLOR_WAVE("Colour wave"),
    RAINBOW("Rainbow"),
    COMET("Comet"),
    BARS("Bars"),
    PULSE("Pulse"),
    TWINKLE("Twinkle"),
    SPARKLE("Sparkle"),
    SHIMMER("Shimmer"),
    STROBE("Strobe"),
    FIREWORKS("Fireworks"),
    SNOW("Snow"),
    SPIRALS("Spirals"),
    BUTTERFLY("Butterfly"),
    METEORS("Meteors"),
    TEXT("Text"),
    PICTURE("Picture"),
    VU_METER("VU meter (music)"),
    MUSIC_PULSE("Music pulse"),
    BEAT_FLASH("Beat flash"),
    FIRE("Fire"),
    RIPPLE("Ripple"),
    SHOCKWAVE("Shockwave"),
    CURTAIN("Curtain"),
    PINWHEEL("Pinwheel"),
    GALAXY("Galaxy"),
    PLASMA("Plasma"),
    MARQUEE("Marquee"),
    GARLANDS("Garlands"),
    SING("Sing (faces)"),
    // More of xLights' effects.
    OFF("Off"),
    COLOR_WASH("Colour wash"),
    FILL("Fill"),
    CANDLE("Candle"),
    CIRCLES("Circles"),
    FAN("Fan"),
    KALEIDOSCOPE("Kaleidoscope"),
    LIFE("Life"),
    LIGHTNING("Lightning"),
    LINES("Lines"),
    LIQUID("Liquid"),
    MORPH("Morph"),
    SHAPE("Shapes"),
    SINGLE_STRAND("Single strand"),
    SNOWFLAKES("Snowflakes"),
    SPIROGRAPH("Spirograph"),
    TENDRIL("Tendril"),
    TREE("Tree"),
    WARP("Warp"),
    WAVE("Wave"),
    PIANO("Piano (music)"),
    VIDEO("Video"),
    MUSIC("Music (spectrum)"),
    STATE("State"),
    SKETCH("Sketch"),
    SHADER("Shader (formula)"),
    ;

    /** Effects that follow the song's loudness (need a song on the show). */
    val musical get() = this == VU_METER || this == MUSIC_PULSE || this == BEAT_FLASH || this == SING || this == PIANO || this == MUSIC
}

/** How a track mixes with the tracks below it in the list. */
@Serializable
enum class BlendMode(val label: String, val help: String) {
    MAX("Brightest", "Each light shows whichever is brighter, this track or the ones below."),
    NORMAL("Normal", "Covers the tracks below wherever this track is lit."),
    ADD("Add", "Adds its light to the tracks below."),
    SUBTRACT("Subtract", "Darkens the tracks below where this track is lit."),
    AVERAGE("Average", "Mixes half and half with the tracks below."),
    MASK("Mask", "Cuts the tracks below out wherever this track is lit."),
    REVEAL("Reveal", "Shows the tracks below only where this track is lit."),
}

/** The shape a setting follows over an effect's length, from its slider value to [ValueCurve.end]. */
@Serializable
enum class CurveShape(val label: String, val periodic: Boolean = false) {
    RAMP("Ramp"),
    UP_DOWN("Up and down"),
    EASE_IN("Ease in"),
    EASE_OUT("Ease out"),
    SINE("Wave", periodic = true),
    SAW("Saw", periodic = true),
    SQUARE("Square", periodic = true),
    RANDOM("Random", periodic = true),
}

@Serializable
data class ValueCurve(val shape: CurveShape = CurveShape.RAMP, val end: Float = 1f, val cycles: Float = 1f) {
    /** The value at [progress] (0..1 through the effect), starting from [start]. */
    fun at(start: Float, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        fun frac(v: Float) = v - kotlin.math.floor(v)
        val u = when (shape) {
            CurveShape.RAMP -> p
            CurveShape.UP_DOWN -> 1f - kotlin.math.abs(2f * p - 1f)
            CurveShape.EASE_IN -> p * p * p
            CurveShape.EASE_OUT -> 1f - (1f - p).let { it * it * it }
            CurveShape.SINE -> 0.5f - 0.5f * kotlin.math.cos(p * cycles * 2f * Math.PI.toFloat())
            CurveShape.SAW -> frac(p * cycles).let { if (p >= 1f && cycles > 0f) 1f else it }
            CurveShape.SQUARE -> if (frac(p * cycles) < 0.5f) 0f else 1f
            CurveShape.RANDOM -> {
                // Smooth random steps, a few per cycle.
                val x = p * cycles * 4f
                val i = kotlin.math.floor(x).toInt(); val f = x - i
                fun h(n: Int) = ((n * 374761393 + 668265263).let { (it xor (it ushr 13)) * 1274126177 }.let { it xor (it ushr 16) } and 0xFFFF) / 65535f
                h(i) + (h(i + 1) - h(i)) * (f * f * (3 - 2 * f))
            }
        }
        return (start + (end - start) * u).coerceIn(0f, 1f)
    }
}

/** How an effect appears at its start (or disappears at its end) over its fade time. */
@Serializable
enum class Transition(val label: String) {
    FADE("Fade"),
    WIPE("Wipe"),
    FROM_MIDDLE("From middle"),
    FROM_ENDS("From ends"),
    CIRCLE("Circle"),
    DISSOLVE("Dissolve"),
    BLINDS("Blinds"),
}

/** One effect placed on a track's timeline. */
@Serializable
data class Clip(
    val id: String,
    val startMs: Long,
    val durationMs: Long,
    val effect: SeqEffect = SeqEffect.ON,
    val colors: List<Int> = listOf(0xFFFF0000.toInt(), 0xFF00C000.toInt(), 0xFFFFFFFF.toInt()),
    /** 0..1 */
    val speed: Float = 0.5f,
    /** 0..1: size of bands / comet tail / how many features. */
    val size: Float = 0.5f,
    /** 0..1 brightness of this clip. */
    val intensity: Float = 1f,
    val direction: Direction = Direction.AUTO,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    /** When set, speed ramps from [speed] at the start to this at the end. */
    val speedEnd: Float? = null,
    /** When set, brightness ramps from [intensity] at the start to this at the end. */
    val intensityEnd: Float? = null,
    /** Curves for speed, size and brightness over the effect (the older ramps above still play). */
    val speedCurve: ValueCurve? = null,
    val sizeCurve: ValueCurve? = null,
    val intensityCurve: ValueCurve? = null,
    /** How the effect comes in over [fadeInMs] and goes out over [fadeOutMs]. */
    val inTransition: Transition = Transition.FADE,
    val outTransition: Transition = Transition.FADE,
    /** Words for the Text effect. */
    val text: String? = null,
    /** Image in app storage for the Picture effect. */
    val imageFile: String? = null,
    /** Frames in app storage for the Video effect (see [VideoFrames]). */
    val videoFile: String? = null,
    /** Where moving heads under this effect point (0..1, 0.5 = straight ahead); null leaves them. */
    val pan: Float? = null,
    val tilt: Float? = null,
    val panCurve: ValueCurve? = null,
    val tiltCurve: ValueCurve? = null,
    // Layer settings, as in xLights: the effect turned, zoomed, recoloured and sparkled.
    /** 0..1 of a full turn. */
    val rotation: Float = 0f,
    /** Keeps turning: this many turns over the effect (negative turns the other way). */
    val spin: Float = 0f,
    /** 1 = as is; above 1 zooms in, below zooms out. */
    val zoom: Float = 1f,
    /** When set, zoom goes from [zoom] at the start to this at the end, following [zoomShape]. */
    val zoomEnd: Float? = null,
    val zoomShape: CurveShape = CurveShape.RAMP,
    /** 0..1 round the colour wheel. */
    val hueShift: Float = 0f,
    /** 0..1: white sparkles over the effect. */
    val sparkles: Float = 0f,
    /** Shown left-to-right reversed / upside down. */
    val mirrorX: Boolean = false,
    val mirrorY: Boolean = false,
    /** 0..1: softens the effect along the lights. */
    val blur: Float = 0f,
    /** Curves for the hue shift and rotation over the effect. */
    val hueCurve: ValueCurve? = null,
    val rotationCurve: ValueCurve? = null,
    /** Curves for blur and sparkles over the effect. */
    val blurCurve: ValueCurve? = null,
    val sparklesCurve: ValueCurve? = null,
    /** For the State effect: the timing track whose labels pick which part of the prop lights. */
    val timingTrackId: String? = null,
    /** For the Sketch effect: the drawn path, points 0..1 across and up the lights' area. */
    val sketch: List<com.wledcontroller.app.house.MapPoint> = emptyList(),
    /** For the Shader effect: the formula (see [Formula]). */
    val formula: String? = null,
    /** When set, the colours change to these over the effect (xLights' colour curves). */
    val colorsEnd: List<Int>? = null,
) {
    /** Whether any layer setting changes the effect. */
    val hasLayerSettings get() = rotation != 0f || spin != 0f || zoom != 1f || zoomEnd != null || hueShift != 0f || sparkles > 0f || mirrorX || mirrorY || blur > 0f || hueCurve != null || rotationCurve != null || blurCurve != null || sparklesCurve != null

    val speedCurveOrRamp get() = speedCurve ?: speedEnd?.let { ValueCurve(CurveShape.RAMP, it) }
    val intensityCurveOrRamp get() = intensityCurve ?: intensityEnd?.let { ValueCurve(CurveShape.RAMP, it) }

    val endMs get() = startMs + durationMs
}

@Serializable
data class Track(
    val id: String,
    val name: String,
    val target: TrackTarget,
    val clips: List<Clip> = emptyList(),
    val muted: Boolean = false,
    /** How this track mixes with the tracks below it. */
    val blend: BlendMode = BlendMode.MAX,
    /** For a group or the whole house: one effect across them all, or one on each prop. */
    val style: RenderStyle = RenderStyle.AS_ONE,
)

/** How a track spreads an effect over several props (xLights' render buffer styles). */
@Serializable
enum class RenderStyle(val label: String, val help: String) {
    AS_ONE("As one", "One effect across all the props, as they stand on the house."),
    PER_PROP("Each prop", "The effect on every prop on its own, all in step."),
    SINGLE_LINE("One line", "The props strung end to end as one long line."),
}

@Serializable
data class Sequence(
    val id: String,
    val name: String,
    val durationMs: Long,
    val tracks: List<Track> = emptyList(),
    val modifiedAt: Long = 0,
    /** Song copied into app storage, played in sync with the timeline. */
    val audioFile: String? = null,
    val audioTitle: String? = null,
    /** Timing marks (beats), ms from the start. Effects snap to them. */
    val marks: List<Long> = emptyList(),
    /** Detected tempo, for display. */
    val bpm: Float? = null,
    /** The song's words, timed: singing faces follow them. */
    val lyrics: Lyrics? = null,
    /** Named timing tracks (verse, chorus …), like xLights': effects snap to them, State effects follow their labels. */
    val timingTracks: List<TimingTrack> = emptyList(),
    /** For shows imported from xLights: the .fseq file, played instead of [tracks]. */
    val fseqFile: String? = null,
    /** Where each controller's LEDs are in the .fseq channel data. */
    val fseqMap: List<FseqMapping> = emptyList(),
)

/** A named row of labelled times, e.g. the song's sections. */
@Serializable
data class TimingTrack(val id: String, val name: String, val marks: List<TimedLabel> = emptyList()) {
    /** The label showing at [ms], if any. */
    fun labelAt(ms: Long): String? = marks.lastOrNull { ms >= it.startMs && ms < it.endMs }?.label

    /** With a new mark at [ms] labelled [label]; each mark runs until the next one (the last to [endMs]). */
    fun withMark(ms: Long, label: String, endMs: Long): TimingTrack = copy(marks = respace(marks.filterNot { it.startMs == ms } + TimedLabel(ms, ms + 1, label), endMs))

    fun without(ms: Long, endMs: Long): TimingTrack = copy(marks = respace(marks.filterNot { it.startMs == ms }, endMs))

    private fun respace(list: List<TimedLabel>, endMs: Long): List<TimedLabel> {
        val sorted = list.sortedBy { it.startMs }
        return sorted.mapIndexed { i, m -> m.copy(endMs = (sorted.getOrNull(i + 1)?.startMs ?: endMs).coerceAtLeast(m.startMs + 1)) }
    }
}

/** A controller's place in an xLights sequence: its first channel (1-based, as xLights shows it) and LED count. */
@Serializable
data class FseqMapping(val deviceId: String, val startChannel: Int, val ledCount: Int, val enabled: Boolean = true) {
    val endChannel get() = startChannel + ledCount * 3 - 1
}

fun formatTime(ms: Long, tenths: Boolean = false): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val base = "%d:%02d".format(total / 60, total % 60)
    return if (tenths) base + ".${(ms % 1000) / 100}" else base
}
