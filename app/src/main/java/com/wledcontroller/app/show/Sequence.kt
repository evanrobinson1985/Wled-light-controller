package com.wledcontroller.app.show

import com.wledcontroller.app.house.Direction
import kotlinx.serialization.Serializable

/** What a track lights: the whole house, a prop group, or one prop (light run). */
@Serializable
data class TrackTarget(val kind: Kind, val refId: String = "") {
    @Serializable
    enum class Kind { HOUSE, GROUP, PROP }
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
) {
    val endMs get() = startMs + durationMs
}

@Serializable
data class Track(
    val id: String,
    val name: String,
    val target: TrackTarget,
    val clips: List<Clip> = emptyList(),
    val muted: Boolean = false,
)

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
)

fun formatTime(ms: Long, tenths: Boolean = false): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val base = "%d:%02d".format(total / 60, total % 60)
    return if (tenths) base + ".${(ms % 1000) / 100}" else base
}
