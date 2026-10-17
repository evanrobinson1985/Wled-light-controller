package com.wledcontroller.app.data

/** What kind of lights a device or segment drives; the app tunes suggestions and defaults to it. */
enum class LightProfile(
    val label: String,
    val description: String,
    /** Effects that read well on this kind of light (matched by name). Matrix uses 2D effects instead. */
    val suggestedEffects: List<String>,
    /** Speed set when picking an effect, or null to leave it alone. */
    val effectSpeed: Int?,
    /** Show colours as distinct bands instead of blended gradients. */
    val preferBands: Boolean,
    val speedScale: Float,
) {
    STRIP(
        "LED strip",
        "Densely packed LEDs. Smooth gradients and fast effects look their best.",
        listOf(
            "Rainbow", "Colorwaves", "Pacifica", "Fire 2012", "Meteor", "Meteor Smooth", "Scanner", "Scanner Dual",
            "Flow", "Lake", "Aurora", "Juggle", "Comet", "Running Dual", "Sinelon", "Palette", "Ripple", "Plasma",
        ),
        effectSpeed = null, preferBands = false, speedScale = 1f,
    ),
    BULLETS(
        "Bullet / pixel string",
        "Widely spaced bulbs. Effects start slower and holiday colours show as distinct bands, so each bulb reads as one clear colour.",
        listOf(
            "Solid", "Twinkle", "Twinklefox", "Twinklecat", "Running", "Theater", "Chase 3", "Fade", "Breathe",
            "Candle Multi", "Fireworks", "Glitter", "Sparkle", "Dissolve", "Random Colors", "Colorful", "Halloween Eyes",
            "Merry Christmas",
        ),
        effectSpeed = 80, preferBands = true, speedScale = 0.7f,
    ),
    MATRIX(
        "Matrix / panel",
        "A 2D grid of LEDs. 2D effects are suggested first.",
        emptyList(),
        effectSpeed = null, preferBands = false, speedScale = 1f,
    );

    fun suggests(name: String, meta: EffectMeta): Boolean =
        if (this == MATRIX) meta.is2D else suggestedEffects.any { it.equals(name, ignoreCase = true) }

    companion object {
        fun deviceKey(deviceId: String) = deviceId
        fun segmentKey(deviceId: String, segId: Int) = "$deviceId#$segId"
        fun parse(name: String?): LightProfile? = name?.let { n -> entries.firstOrNull { it.name == n } }
    }
}

/** Segment setting wins over the device setting. */
fun Map<String, String>.profileFor(deviceId: String, segId: Int?): LightProfile? =
    (segId?.let { LightProfile.parse(this[LightProfile.segmentKey(deviceId, it)]) })
        ?: LightProfile.parse(this[LightProfile.deviceKey(deviceId)])
