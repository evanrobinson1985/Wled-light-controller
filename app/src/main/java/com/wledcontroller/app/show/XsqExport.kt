package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap

/**
 * Writes a show as an xLights sequence (.xsq), so it can be opened and carried on in xLights.
 * Each track's lights become an xLights model (props by name, groups as model groups, prop
 * parts as submodels; tracks on the same lights become layers), effects become the nearest
 * xLights effects with their colours and fades, and beat marks and lyrics become timing tracks.
 * The models need the same names in the xLights layout.
 */
object XsqExport {

    /** The xLights effect closest to [e]. */
    fun xlightsName(e: SeqEffect): String = when (e) {
        SeqEffect.ON, SeqEffect.PULSE -> "On"
        SeqEffect.OFF -> "Off"
        SeqEffect.WIPE, SeqEffect.CURTAIN -> "Curtain"
        SeqEffect.CHASE, SeqEffect.SINGLE_STRAND -> "Single Strand"
        SeqEffect.COLOR_WAVE, SeqEffect.RAINBOW, SeqEffect.COLOR_WASH -> "Color Wash"
        SeqEffect.COMET, SeqEffect.METEORS -> "Meteors"
        SeqEffect.BARS -> "Bars"
        SeqEffect.TWINKLE, SeqEffect.SPARKLE -> "Twinkle"
        SeqEffect.SHIMMER -> "Shimmer"
        SeqEffect.STROBE -> "Strobe"
        SeqEffect.FIREWORKS -> "Fireworks"
        SeqEffect.SNOW, SeqEffect.SNOWFLAKES -> "Snowflakes"
        SeqEffect.SPIRALS -> "Spirals"
        SeqEffect.BUTTERFLY -> "Butterfly"
        SeqEffect.TEXT -> "Text"
        SeqEffect.PICTURE -> "Pictures"
        SeqEffect.VIDEO -> "Video"
        SeqEffect.VU_METER, SeqEffect.MUSIC_PULSE, SeqEffect.BEAT_FLASH -> "VU Meter"
        SeqEffect.MUSIC -> "Music"
        SeqEffect.PIANO -> "Piano"
        SeqEffect.FIRE -> "Fire"
        SeqEffect.RIPPLE -> "Ripple"
        SeqEffect.SHOCKWAVE -> "Shockwave"
        SeqEffect.PINWHEEL -> "Pinwheel"
        SeqEffect.GALAXY -> "Galaxy"
        SeqEffect.PLASMA -> "Plasma"
        SeqEffect.MARQUEE -> "Marquee"
        SeqEffect.GARLANDS -> "Garlands"
        SeqEffect.SING -> "Faces"
        SeqEffect.FILL -> "Fill"
        SeqEffect.CANDLE -> "Candle"
        SeqEffect.CIRCLES -> "Circles"
        SeqEffect.FAN -> "Fan"
        SeqEffect.KALEIDOSCOPE -> "Kaleidoscope"
        SeqEffect.LIFE -> "Life"
        SeqEffect.LIGHTNING -> "Lightning"
        SeqEffect.LINES -> "Lines"
        SeqEffect.LIQUID -> "Liquid"
        SeqEffect.MORPH -> "Morph"
        SeqEffect.SHAPE -> "Shape"
        SeqEffect.SPIROGRAPH -> "Spirograph"
        SeqEffect.TENDRIL -> "Tendril"
        SeqEffect.TREE -> "Tree"
        SeqEffect.WARP -> "Warp"
        SeqEffect.WAVE -> "Wave"
        SeqEffect.STATE -> "State"
        SeqEffect.SKETCH -> "Sketch"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun transitionName(t: Transition) = when (t) {
        Transition.FADE -> "Fade"
        Transition.WIPE -> "Wipe"
        Transition.FROM_MIDDLE -> "From Middle"
        Transition.FROM_ENDS -> "From Middle"
        Transition.CIRCLE -> "Circle Explode"
        Transition.DISSOLVE -> "Dissolve"
        Transition.BLINDS -> "Blinds"
    }

    /** The name the track's lights have in xLights, and its submodel when it's a prop part. */
    private fun modelOf(t: Track, map: HouseMap): Pair<String, String?> = when (t.target.kind) {
        TrackTarget.Kind.HOUSE -> "House" to null
        TrackTarget.Kind.PROP -> (map.runs.firstOrNull { it.id == t.target.refId }?.name ?: t.name) to null
        TrackTarget.Kind.GROUP -> (map.groups.firstOrNull { it.id == t.target.refId }?.name ?: t.name) to null
        TrackTarget.Kind.SUBMODEL -> {
            val run = map.runs.firstOrNull { it.id == t.target.refId.substringBefore('/') }
            (run?.name ?: t.name) to t.target.refId.substringAfter('/')
        }
    }

    fun export(seq: Sequence, map: HouseMap): String {
        val palettes = LinkedHashMap<String, Int>()
        val effects = LinkedHashMap<String, Int>()
        fun paletteOf(c: Clip): Int {
            val cols = c.colors.ifEmpty { listOf(0xFFFFFF) }.take(8)
            val s = buildString {
                for (i in 1..8) {
                    val col = cols.getOrNull(i - 1)
                    append("C_BUTTON_Palette$i=#%06X,".format((col ?: 0xFFFFFF) and 0xFFFFFF))
                    append("C_CHECKBOX_Palette$i=${if (col != null) 1 else 0},")
                }
                append("C_SLIDER_Brightness=${(c.intensity * 100).toInt()}")
            }
            return palettes.getOrPut(s) { palettes.size }
        }
        fun settingsOf(c: Clip): Int {
            val s = buildList {
                if (c.fadeInMs > 0) { add("T_TEXTCTRL_Fadein=%.2f".format(java.util.Locale.US, c.fadeInMs / 1000f)); add("T_CHOICE_In_Transition_Type=${transitionName(c.inTransition)}") }
                if (c.fadeOutMs > 0) { add("T_TEXTCTRL_Fadeout=%.2f".format(java.util.Locale.US, c.fadeOutMs / 1000f)); add("T_CHOICE_Out_Transition_Type=${transitionName(c.outTransition)}") }
                if (c.effect == SeqEffect.TEXT && !c.text.isNullOrBlank()) add("E_TEXTCTRL_Text=${c.text.replace(",", " ")}")
                if (c.rotation != 0f) add("B_SLIDER_Rotation=${(c.rotation * 100).toInt()}")
                if (c.zoom != 1f) add("B_SLIDER_Zoom=%.1f".format(java.util.Locale.US, c.zoom))
                if (c.blur > 0f) add("B_SLIDER_Blur=${1 + (c.blur * 14).toInt()}")
                if (c.hueShift != 0f) add("C_SLIDER_Color_HueAdjust=${(c.hueShift * 100).toInt()}")
                if (c.sparkles > 0f) add("C_SLIDER_SparkleFrequency=${(c.sparkles * 200).toInt()}")
                if (c.effect == SeqEffect.STATE) seq.timingTracks.firstOrNull { it.id == c.timingTrackId }?.let { add("E_CHOICE_State_TimingTrack=${it.name}") }
            }.joinToString(",")
            return effects.getOrPut(s) { effects.size }
        }

        // Tracks on the same lights become layers of one model, top layer first.
        val byModel = LinkedHashMap<Pair<String, String?>, MutableList<Track>>()
        for (t in seq.tracks) byModel.getOrPut(modelOf(t, map)) { ArrayList() } += t
        val models = byModel.keys.map { it.first }.distinct()

        val elements = StringBuilder()
        fun layer(clips: List<Clip>) = buildString {
            append("      <EffectLayer>\n")
            for (c in clips.sortedBy { it.startMs }) {
                append("        <Effect ref=\"${settingsOf(c)}\" name=\"${xlightsName(c.effect)}\" startTime=\"${c.startMs}\" endTime=\"${c.endMs}\" palette=\"${paletteOf(c)}\"/>\n")
            }
            append("      </EffectLayer>\n")
        }
        fun timingLayers(vararg layers: List<TimedLabel>) = buildString {
            for (l in layers) {
                append("      <EffectLayer>\n")
                l.forEach { append("        <Effect label=\"${esc(it.label)}\" startTime=\"${it.startMs}\" endTime=\"${it.endMs}\"/>\n") }
                append("      </EffectLayer>\n")
            }
        }
        if (seq.marks.isNotEmpty()) {
            val marks = seq.marks.sorted()
            val labels = marks.mapIndexed { i, m -> TimedLabel(m, marks.getOrNull(i + 1) ?: seq.durationMs, "") }
            elements.append("    <Element type=\"timing\" name=\"Beats\">\n").append(timingLayers(labels)).append("    </Element>\n")
        }
        for (tt in seq.timingTracks) {
            elements.append("    <Element type=\"timing\" name=\"${esc(tt.name)}\">\n").append(timingLayers(tt.marks)).append("    </Element>\n")
        }
        seq.lyrics?.takeIf { !it.isEmpty }?.let { l ->
            val phon = l.phonemes.map { it.copy(label = Phoneme.parse(it.label)?.let { p -> if (p == Phoneme.ETC) "etc" else if (p == Phoneme.REST) "rest" else p.name } ?: it.label) }
            elements.append("    <Element type=\"timing\" name=\"Lyrics\">\n").append(timingLayers(l.phrases, l.words, phon)).append("    </Element>\n")
        }
        for (model in models) {
            elements.append("    <Element type=\"model\" name=\"${esc(model)}\">\n")
            byModel[model to null]?.forEach { t -> elements.append(layer(t.clips)) }
            if (byModel[model to null] == null) elements.append("      <EffectLayer/>\n")
            byModel.filterKeys { it.first == model && it.second != null }.forEach { (k, tracks) ->
                elements.append("      <SubModelEffectLayer name=\"${esc(k.second!!)}\">\n")
                tracks.flatMap { it.clips }.sortedBy { it.startMs }.forEach { c ->
                    elements.append("        <Effect ref=\"${settingsOf(c)}\" name=\"${xlightsName(c.effect)}\" startTime=\"${c.startMs}\" endTime=\"${c.endMs}\" palette=\"${paletteOf(c)}\"/>\n")
                }
                elements.append("      </SubModelEffectLayer>\n")
            }
            elements.append("    </Element>\n")
        }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<xsequence BaseChannel=\"0\" ChanCtrlBasic=\"0\" ChanCtrlColor=\"0\" FixedPointTiming=\"1\" ModelBlending=\"true\">\n")
            append("  <head>\n    <version>2024.01</version>\n    <author>WLED Controller</author>\n")
            seq.audioTitle?.let { append("    <mediaFile>${esc(it)}</mediaFile>\n") }
            append("    <sequenceType>${if (seq.audioFile != null) "Media" else "Animation"}</sequenceType>\n")
            append("    <sequenceTiming>25 ms</sequenceTiming>\n")
            append("    <sequenceDuration>%.3f</sequenceDuration>\n".format(java.util.Locale.US, seq.durationMs / 1000f))
            append("  </head>\n")
            append("  <ColorPalettes>\n"); palettes.keys.forEach { append("    <ColorPalette>${esc(it)}</ColorPalette>\n") }; append("  </ColorPalettes>\n")
            append("  <EffectDB>\n"); effects.keys.forEach { append("    <Effect>${esc(it)}</Effect>\n") }; append("  </EffectDB>\n")
            append("  <DisplayElements>\n")
            if (seq.marks.isNotEmpty()) append("    <Element collapsed=\"0\" type=\"timing\" name=\"Beats\" visible=\"1\" active=\"1\"/>\n")
            seq.timingTracks.forEach { append("    <Element collapsed=\"0\" type=\"timing\" name=\"${esc(it.name)}\" visible=\"1\" active=\"0\"/>\n") }
            if (seq.lyrics?.isEmpty == false) append("    <Element collapsed=\"0\" type=\"timing\" name=\"Lyrics\" visible=\"1\" active=\"0\"/>\n")
            models.forEach { append("    <Element collapsed=\"0\" type=\"model\" name=\"${esc(it)}\" visible=\"1\"/>\n") }
            append("  </DisplayElements>\n")
            append("  <ElementEffects>\n").append(elements).append("  </ElementEffects>\n")
            append("</xsequence>\n")
        }
    }
}
