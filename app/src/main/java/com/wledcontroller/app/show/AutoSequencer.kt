package com.wledcontroller.app.show

import com.wledcontroller.app.house.Direction
import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.LightRun
import com.wledcontroller.app.house.ModelKind
import com.wledcontroller.app.house.PropType
import com.wledcontroller.app.house.allSubmodels
import kotlin.math.roundToLong

/**
 * Builds a whole show from a song: finds its beat and its calm, lively and big sections, works
 * out what each prop is (roofline, arches, mega tree, matrix, singing face …), and gives every
 * prop effects that suit it and the music — gentle washes in the quiet parts, chases on the beat
 * in the lively ones, and bursts, spectrum bars and fireworks when it's big. A starting point to
 * fine-tune, not a finished show.
 */
object AutoSequencer {

    enum class Energy(val label: String) { CALM("Calm"), LIVELY("Lively"), BIG("Big") }

    /** A stretch of the song with one feel. */
    data class Section(val startMs: Long, val endMs: Long, val energy: Energy)

    /** How busy to make it: 0 = gentle, 1 = everything on the beat. */
    data class Options(val colors: List<Int>, val liveliness: Float = 0.6f, val seed: Int = 0, val replace: Boolean = true)

    /** What a prop is, for choosing its effects. */
    enum class Role { OUTLINE, ARCH, TREE, MATRIX, STAR, BUSH, FACE, SPOT }

    fun roleOf(run: LightRun, fixtureIds: Set<String>): Role = when {
        run.deviceId in fixtureIds -> Role.SPOT
        run.model?.kind == ModelKind.FACE || run.model?.kind == ModelKind.SINGING_FACE ||
            run.allSubmodels().any { it.name.lowercase().replace(Regex("[^a-z]"), "").startsWith("mouth") } -> Role.FACE
        run.model?.kind == ModelKind.MEGA_TREE || run.prop == PropType.TREE -> Role.TREE
        run.model?.kind == ModelKind.MATRIX || run.model?.kind == ModelKind.CUSTOM || run.prop == PropType.MATRIX -> Role.MATRIX
        run.model?.kind == ModelKind.STAR || run.prop == PropType.STAR -> Role.STAR
        run.prop == PropType.ARCH || run.prop == PropType.CANDY_CANE -> Role.ARCH
        run.prop == PropType.BUSH -> Role.BUSH
        else -> Role.OUTLINE
    }

    /** An even beat to fall back on (120 BPM) when the song's beat isn't known. */
    fun steadyBeats(durationMs: Long, bpm: Float = 120f): List<Long> {
        val step = (60_000f / bpm).roundToLong()
        return (0 until durationMs step step).toList()
    }

    /**
     * Splits the song into sections by how loud it is around each beat (smoothed over a couple of
     * bars, then sorted into thirds), at least [minBeats] long, starting on beats.
     */
    fun sections(loudness: (Long) -> Float, beats: List<Long>, durationMs: Long, minBeats: Int = 8): List<Section> {
        if (beats.size < 2) return listOf(Section(0, durationMs, Energy.LIVELY))
        val per = beats.mapIndexed { i, b ->
            val e = beats.getOrNull(i + 1) ?: durationMs
            var sum = 0f; var n = 0
            var ms = b
            while (ms < e) { sum += loudness(ms); n++; ms += 20 }
            if (n == 0) 0f else sum / n
        }
        val smooth = per.indices.map { i -> (maxOf(0, i - 4)..minOf(per.lastIndex, i + 4)).map { per[it] }.average().toFloat() }
        val sorted = smooth.sorted()
        val lowCut = sorted[sorted.size / 3]; val highCut = sorted[sorted.size * 2 / 3]
        fun energy(v: Float) = when { v >= highCut && highCut > lowCut -> Energy.BIG; v > lowCut -> Energy.LIVELY; else -> Energy.CALM }
        // Runs of the same feel, short ones folded into what came before.
        val raw = ArrayList<Pair<Int, Energy>>()
        smooth.forEachIndexed { i, v -> val e = energy(v); if (raw.lastOrNull()?.second != e) raw += i to e }
        val merged = ArrayList<Pair<Int, Energy>>()
        raw.forEachIndexed { k, (start, e) ->
            val end = raw.getOrNull(k + 1)?.first ?: beats.size
            if (merged.isNotEmpty() && end - start < minBeats) return@forEachIndexed
            if (merged.lastOrNull()?.second == e) return@forEachIndexed
            merged += start to e
        }
        if (merged.isEmpty()) merged += 0 to Energy.LIVELY
        return merged.mapIndexed { k, (start, e) ->
            Section(if (k == 0) 0 else beats[start], merged.getOrNull(k + 1)?.let { beats[it.first] } ?: durationMs, e)
        }
    }

    /**
     * The show: [seq] with tracks for every placed prop (replacing its tracks when
     * [Options.replace]), its beat marks, and a "Sections" timing track.
     */
    fun build(
        seq: Sequence,
        map: HouseMap,
        loudness: (Long) -> Float,
        beats: List<Long>,
        fixtureIds: Set<String>,
        options: Options,
    ): Sequence {
        val dur = seq.durationMs
        val beatList = beats.filter { it < dur }.ifEmpty { steadyBeats(dur) }
        val secs = sections(loudness, beatList, dur)
        val colors = options.colors.ifEmpty { listOf(0xFFFF0000.toInt(), 0xFF00C000.toInt(), 0xFFFFFFFF.toInt()) }
        val live = options.liveliness.coerceIn(0f, 1f)
        var idCount = 0
        fun id() = "auto-${options.seed}-${idCount++}"
        fun pick(k: Int) = colors[k.mod(colors.size)]
        fun palette(k: Int) = List(3) { pick(k + it) }
        fun beatsIn(s: Section) = beatList.filter { it >= s.startMs && it < s.endMs }

        val runs = map.runs.filter { it.placedCount > 0 }
        val byRole = runs.groupBy { roleOf(it, fixtureIds) }
        val tracks = ArrayList<Track>()

        fun clip(start: Long, end: Long, fx: SeqEffect, cols: List<Int>, speed: Float = 0.5f, size: Float = 0.5f, fade: Long = 300, dir: Direction = Direction.AUTO) =
            Clip(id(), start, (end - start).coerceAtLeast(100), fx, cols, speed = speed, size = size,
                fadeInMs = minOf(fade, (end - start) / 4), fadeOutMs = minOf(fade, (end - start) / 4), direction = dir)

        /** One effect per section for a prop, chosen by its role and the section's feel. */
        fun baseClips(role: Role, index: Int): List<Clip> = secs.mapIndexed { si, s ->
            val cols = palette(si + index)
            val calm = s.energy == Energy.CALM; val big = s.energy == Energy.BIG
            when (role) {
                Role.OUTLINE -> when {
                    calm -> clip(s.startMs, s.endMs, SeqEffect.COLOR_WASH, cols, speed = 0.2f)
                    big -> clip(s.startMs, s.endMs, SeqEffect.SINGLE_STRAND, cols, speed = 0.75f, size = 0.3f)
                    else -> clip(s.startMs, s.endMs, SeqEffect.CHASE, cols, speed = 0.45f + 0.2f * live, size = 0.4f)
                }
                Role.ARCH -> when {
                    calm -> clip(s.startMs, s.endMs, SeqEffect.ON, listOf(pick(si + index)), fade = 800)
                    else -> clip(s.startMs, s.endMs, SeqEffect.MARQUEE, cols, speed = 0.5f + 0.3f * live)
                }
                Role.TREE -> when {
                    calm -> clip(s.startMs, s.endMs, SeqEffect.TWINKLE, cols, speed = 0.3f)
                    big -> clip(s.startMs, s.endMs, SeqEffect.SPIRALS, cols, speed = 0.8f, size = 0.4f)
                    else -> clip(s.startMs, s.endMs, SeqEffect.GARLANDS, cols, speed = 0.5f)
                }
                Role.MATRIX -> when {
                    calm -> clip(s.startMs, s.endMs, SeqEffect.LIQUID, cols, speed = 0.3f)
                    big -> clip(s.startMs, s.endMs, SeqEffect.MUSIC, cols)
                    else -> clip(s.startMs, s.endMs, SeqEffect.WAVE, cols, speed = 0.5f)
                }
                Role.STAR -> clip(s.startMs, s.endMs, if (calm) SeqEffect.PULSE else SeqEffect.SHIMMER, cols, speed = if (calm) 0.2f else 0.6f)
                Role.BUSH -> clip(s.startMs, s.endMs, if (big) SeqEffect.SPARKLE else SeqEffect.TWINKLE, cols, speed = if (big) 0.7f else 0.4f)
                Role.FACE -> clip(s.startMs, s.endMs, SeqEffect.SING, listOf(0xFFFFFFFF.toInt(), 0xFFFF2020.toInt(), pick(si)), fade = 0)
                Role.SPOT -> clip(s.startMs, s.endMs, SeqEffect.COLOR_WASH, cols, speed = 0.3f).copy(
                    pan = 0.5f, tilt = if (big) 0.8f else 0.6f,
                    panCurve = ValueCurve(CurveShape.SINE, end = if (calm) 0.6f else 0.9f, cycles = ((s.endMs - s.startMs) / 4000f).coerceAtLeast(1f)),
                )
            }
        }.let { list ->
            // A singing face sings the whole song in one effect.
            if (role == Role.FACE) listOf(clip(0, dur, SeqEffect.SING, listOf(0xFFFFFFFF.toInt(), 0xFFFF2020.toInt(), pick(0)), fade = 0)) else list
        }

        /** Beat accents on top, in the lively and big parts: flashes that hop from prop to prop. */
        fun accentClips(role: Role, index: Int, of: Int): List<Clip> {
            if (live < 0.25f || role == Role.FACE || role == Role.SPOT) return emptyList()
            val out = ArrayList<Clip>()
            for ((si, s) in secs.withIndex()) {
                if (s.energy == Energy.CALM) continue
                val bs = beatsIn(s)
                val every = if (s.energy == Energy.BIG || live > 0.75f) 1 else 2
                bs.forEachIndexed { bi, b ->
                    if (bi % every != 0) return@forEachIndexed
                    val next = bs.getOrNull(bi + every) ?: s.endMs
                    val len = minOf(next - b, 600L).coerceAtLeast(120)
                    when (role) {
                        // Arches, bushes and stars take turns: one lights per beat, round the yard.
                        Role.ARCH, Role.BUSH, Role.STAR -> if ((bi / every) % of == index) {
                            out += clip(b, b + len, if (role == Role.ARCH) SeqEffect.WIPE else SeqEffect.ON, listOf(pick(si + bi)), fade = len / 2).copy(fadeInMs = 0)
                        }
                        Role.MATRIX -> if (s.energy == Energy.BIG && (bi / every) % 4 == 0) out += clip(b, b + len * 2, SeqEffect.SHOCKWAVE, palette(si + bi), speed = 0.7f, fade = 0)
                        Role.TREE -> if (s.energy == Energy.BIG && (bi / every) % 8 == 4) out += clip(b, b + len * 3, SeqEffect.FIREWORKS, palette(si + bi), speed = 0.7f, fade = 0)
                        Role.OUTLINE -> if (s.energy == Energy.BIG && (bi / every) % 4 == 0) out += clip(b, b + len, SeqEffect.ON, listOf(0xFFFFFFFF.toInt()), fade = len * 3 / 4).copy(fadeInMs = 0)
                        else -> Unit
                    }
                }
            }
            return out
        }

        for ((role, list) in byRole) {
            list.forEachIndexed { i, run ->
                val target = TrackTarget(TrackTarget.Kind.PROP, run.id)
                val accents = accentClips(role, i, list.size)
                // Accents sit on a layer above the base effects.
                if (accents.isNotEmpty()) tracks += Track(id(), "${run.name} · beats", target, accents, blend = BlendMode.NORMAL)
                tracks += Track(id(), run.name, target, baseClips(role, i))
            }
        }
        // A finale: the whole house shimmers in white for the last two seconds.
        if (runs.isNotEmpty() && dur > 6000) {
            tracks.add(0, Track(id(), "Finale", TrackTarget(TrackTarget.Kind.HOUSE), listOf(clip(dur - 2000, dur, SeqEffect.SHIMMER, listOf(0xFFFFFFFF.toInt()), speed = 0.8f, fade = 400)), blend = BlendMode.MAX))
        }

        val sectionTrack = TimingTrack(id(), "Sections", secs.map { TimedLabel(it.startMs, it.endMs, it.energy.label) })
        return seq.copy(
            tracks = if (options.replace) tracks else seq.tracks + tracks,
            marks = beatList,
            timingTracks = seq.timingTracks.filterNot { it.name == "Sections" } + sectionTrack,
        )
    }
}
