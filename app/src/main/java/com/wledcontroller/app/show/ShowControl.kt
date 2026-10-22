package com.wledcontroller.app.show

import android.content.Context
import com.wledcontroller.app.WledApp
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Starting and stopping shows from outside the app's screens: Home Assistant, the voice
 * shortcuts and the watch all come through here.
 */
object ShowControl {
    /**
     * Does [command]: "play" (the playlist named or with the id [arg]; the first one if none),
     * "schedule", "jukebox", "skip" or "stop". Returns why it couldn't, or null.
     */
    suspend fun perform(context: Context, command: String, arg: String? = null): String? {
        val app = context.applicationContext
        val c = (app as WledApp).container
        val runner = c.showRunner
        return when (command) {
            "play" -> {
                val lists = c.store.playlists.first()
                val p = arg?.let { a -> lists.firstOrNull { it.id == a } ?: lists.firstOrNull { it.name.equals(a.trim(), ignoreCase = true) } }
                    ?: lists.firstOrNull { it.sequenceIds.isNotEmpty() }
                    ?: return "Make a playlist in the app first."
                // The service keeps it going with the screen off; Android may not let it start from
                // the background, and then the show plays straight from the app.
                runCatching { ShowRunnerService.playNow(app, p.id) }.onFailure { runner.playNow(p.id) }
                null
            }
            "schedule" -> { runCatching { ShowRunnerService.startSchedule(app) }.onFailure { runner.runSchedule() }; null }
            "jukebox" -> {
                // Not set up yet: every show with music.
                if (c.jukebox.songs.isEmpty()) c.jukebox.songs = c.sequences.all.value
                    .map { Jukebox.Song(it.id, it.name, it.durationMs, it.audioFile != null) }
                if (c.jukebox.songs.isEmpty()) return "Make a show in the app first."
                runCatching { ShowRunnerService.startJukebox(app) }.onFailure { runner.runJukebox(c.jukebox) }
                null
            }
            "skip" -> { runner.skip(); null }
            "stop" -> { runCatching { ShowRunnerService.stop(app) }; runner.stop(); null }
            else -> "Unknown command $command"
        }
    }

    /** What's playing and what's coming, plus the playlists to choose from. */
    suspend fun status(context: Context): JsonObject {
        val c = ((context.applicationContext) as WledApp).container
        val s = c.showRunner.state.value
        val next = if (s.mode == "jukebox") c.jukebox.queued().map { it.title } else s.upNext
        return buildJsonObject {
            put("running", s.running)
            put("mode", s.mode)
            s.nowPlaying?.let { put("now", it) }
            s.playlistName?.let { put("playlist", it) }
            s.weatherHold?.let { put("hold", it) }
            s.nextStart?.let { put("nextStart", it.toLocalTime().withSecond(0).withNano(0).toString()) }
            putJsonArray("upNext") { next.take(10).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            putJsonArray("playlists") {
                c.store.playlists.first().filter { it.sequenceIds.isNotEmpty() }.forEach { p -> addJsonObject { put("id", p.id); put("name", p.name) } }
            }
        }
    }
}
