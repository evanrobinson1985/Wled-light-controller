package com.wledcontroller.app.show

import com.wledcontroller.app.house.HouseMap
import com.wledcontroller.app.house.allSubmodels
import java.io.File

/** One thing found wrong (or worth a look) in the house map or a show. */
data class SetupIssue(val severity: Severity, val where: String, val message: String) {
    enum class Severity { PROBLEM, WARNING }
}

/**
 * Looks over the house map and the shows for mistakes, like xLights' Check Sequence: props on
 * controllers that aren't there, overlapping or too-long LED ranges, props not placed, shows
 * aimed at props that were deleted, missing pictures and videos, music effects without music.
 */
object SetupCheck {

    /**
     * [controllers]: every controller and light output the app has, by id, with its LED count
     * when known (null when not reached yet).
     */
    fun run(map: HouseMap, controllers: Map<String, Int?>, shows: List<Sequence>, fileExists: (String) -> Boolean = { File(it).exists() }): List<SetupIssue> {
        val out = ArrayList<SetupIssue>()
        fun problem(where: String, msg: String) { out += SetupIssue(SetupIssue.Severity.PROBLEM, where, msg) }
        fun warn(where: String, msg: String) { out += SetupIssue(SetupIssue.Severity.WARNING, where, msg) }

        // ---- The house map.
        for (run in map.runs) {
            val where = "Prop \"${run.name}\""
            if (run.deviceId !in controllers) problem(where, "Its controller isn't in the app any more. Edit the prop and choose one.")
            else controllers[run.deviceId]?.let { n ->
                if (n > 0 && run.endLed > n) problem(where, "Uses LEDs ${run.startLed}–${run.endLed - 1}, but its controller only has $n.")
            }
            if (run.count <= 0) problem(where, "Has no LEDs.")
            if (run.placedCount == 0) warn(where, "Isn't placed on the house photo yet, so effects can't reach it.")
        }
        // Two props on the same LEDs.
        for ((dev, runs) in map.runs.groupBy { it.deviceId }) {
            val sorted = runs.sortedBy { it.startLed }
            for ((a, b) in sorted.zipWithNext()) {
                if (b.startLed < a.endLed) {
                    warn("Props \"${a.name}\" and \"${b.name}\"", "Share LEDs ${b.startLed}–${minOf(a.endLed, b.endLed) - 1} on the same controller.")
                }
            }
        }
        val runIds = map.runs.map { it.id }.toSet()
        for (g in map.groups) {
            val missing = g.runIds.count { it !in runIds }
            if (missing > 0) warn("Group \"${g.name}\"", "Lists $missing prop${if (missing > 1) "s" else ""} that no longer exist.")
            if (g.runIds.none { it in runIds }) warn("Group \"${g.name}\"", "Has no props, so its tracks light nothing.")
        }

        // ---- Shows.
        val groupIds = map.groups.map { it.id }.toSet()
        for (s in shows) {
            if (s.fseqFile != null) {
                if (!fileExists(s.fseqFile)) problem("Show \"${s.name}\"", "Its xLights .fseq file is missing.")
                continue
            }
            if (s.audioFile != null && !fileExists(s.audioFile)) problem("Show \"${s.name}\"", "Its song file is missing.")
            for (t in s.tracks) {
                val where = "Show \"${s.name}\" · ${t.name}"
                val target = t.target
                val ok = when (target.kind) {
                    TrackTarget.Kind.HOUSE -> true
                    TrackTarget.Kind.PROP -> target.refId in runIds
                    TrackTarget.Kind.GROUP -> target.refId in groupIds
                    TrackTarget.Kind.SUBMODEL -> {
                        val run = map.runs.firstOrNull { it.id == target.refId.substringBefore('/') }
                        run != null && run.allSubmodels().any { it.name == target.refId.substringAfter('/') }
                    }
                }
                if (!ok) problem(where, "Aimed at a prop, group or part that no longer exists. Pick new lights for the track.")
                if (t.muted && t.clips.isNotEmpty()) warn(where, "Is muted, so its ${t.clips.size} effects don't play.")
                val clips = t.clips.sortedBy { it.startMs }
                for ((a, b) in clips.zipWithNext()) {
                    if (b.startMs < a.endMs) warn(where, "Effects at ${formatTime(b.startMs, true)} overlap; the later one covers the earlier.")
                }
                for (c in clips) {
                    val at = "${c.effect.label} at ${formatTime(c.startMs, true)}"
                    if (c.startMs >= s.durationMs) warn(where, "$at starts after the show ends.")
                    if (c.effect.musical && s.audioFile == null) warn(where, "$at follows music, but the show has no song.")
                    if (c.effect == SeqEffect.SING && (s.lyrics == null || s.lyrics.isEmpty) && s.audioFile != null) {
                        warn(where, "$at sings with the song's loudness. Add lyrics (♪ › Lyrics) for real mouth shapes.")
                    }
                    if (c.effect == SeqEffect.PICTURE && (c.imageFile == null || !fileExists(c.imageFile))) problem(where, "$at has no picture.")
                    if (c.effect == SeqEffect.VIDEO && (c.videoFile == null || !fileExists(c.videoFile))) problem(where, "$at has no video.")
                    if (c.effect == SeqEffect.TEXT && c.text.isNullOrBlank()) warn(where, "$at has no words.")
                    if (c.effect == SeqEffect.GLEDIATOR && (c.videoFile == null || !fileExists(c.videoFile))) problem(where, "$at has no Glediator recording.")
                    if (c.effect == SeqEffect.DUPLICATE && s.tracks.none { it.id == c.duplicateTrackId }) warn(where, "$at doesn't say which track to copy.")
                    if (c.effect == SeqEffect.STATE && s.timingTracks.none { it.id == c.timingTrackId }) warn(where, "$at has no timing track to follow.")
                    if (c.intensity <= 0f && c.intensityCurveOrRamp == null) warn(where, "$at is at zero brightness.")
                }
            }
            if (s.tracks.isEmpty()) warn("Show \"${s.name}\"", "Has no tracks yet.")
        }
        return out.sortedBy { it.severity }
    }
}
