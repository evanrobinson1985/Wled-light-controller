package com.wledcontroller.app.show

import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.data.WledApi
import com.wledcontroller.app.house.HouseEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime

data class RunnerState(
    val running: Boolean = false,
    /** "schedule" or "playlist" (play now). */
    val mode: String = "",
    val nowPlaying: String? = null,
    val playlistName: String? = null,
    val nextStart: LocalDateTime? = null,
)

/**
 * Plays playlists on the lights: either right now, or whenever a schedule window is open.
 * Uses its own player so editing a show in the app doesn't disturb it. Driven by
 * [ShowRunnerService], which keeps the process alive with the screen off.
 */
class ShowRunner(
    private val store: AppStore,
    private val api: WledApi,
    private val sequences: SequenceRepository,
    house: HouseEngine,
    private val scope: CoroutineScope,
) {
    private val player = SequencePlayer(store, house, scope)
    private val _state = MutableStateFlow(RunnerState())
    val state: StateFlow<RunnerState> = _state.asStateFlow()
    private var job: Job? = null

    fun runSchedule() = startJob("schedule") { scheduleLoop() }

    fun playNow(playlistId: String) = startJob("playlist") {
        val p = store.playlists.first().firstOrNull { it.id == playlistId } ?: return@startJob
        do {
            if (!playPlaylistOnce(p) { true }) break
        } while (p.loop && currentCoroutineContext().isActive)
    }

    fun stop() {
        job?.cancel()
        job = null
        player.stopAll()
        player.loadAudio(null)
        _state.value = RunnerState()
    }

    private fun startJob(mode: String, block: suspend () -> Unit) {
        job?.cancel()
        _state.value = RunnerState(running = true, mode = mode)
        job = scope.launch {
            try {
                block()
            } finally {
                player.stopAll()
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { player.loadAudio(null) }
                _state.value = RunnerState()
            }
        }
    }

    private suspend fun scheduleLoop() {
        while (currentCoroutineContext().isActive) {
            val now = LocalDateTime.now()
            val entries = store.schedules.first()
            val active = entries.firstOrNull { it.isActive(now) }
            if (active == null) {
                val next = entries.mapNotNull { it.nextStart(now) }.minOrNull()
                _state.value = RunnerState(running = true, mode = "schedule", nextStart = next)
                delay(15_000)
                continue
            }
            val playlist = store.playlists.first().firstOrNull { it.id == active.playlistId }
            if (playlist == null || playlist.sequenceIds.isEmpty()) { delay(15_000); continue }
            val end = active.endTime(now)
            // Keep cycling the playlist until the window closes; the current song always finishes.
            var anyPlayed = false
            while (currentCoroutineContext().isActive && LocalDateTime.now().isBefore(end)) {
                val played = playPlaylistOnce(playlist) { LocalDateTime.now().isBefore(end) }
                anyPlayed = anyPlayed || played
                if (!playlist.loop || !played) break
            }
            if (!playlist.loop || !anyPlayed) {
                // Played once: wait out the rest of the window so it doesn't restart.
                while (currentCoroutineContext().isActive && LocalDateTime.now().isBefore(end)) delay(15_000)
            }
            player.stopAll()
            if (active.lightsOffAtEnd) turnAllOff()
        }
    }

    /** Plays each show once while [keepGoing]; returns whether anything played. */
    private suspend fun playPlaylistOnce(p: Playlist, keepGoing: () -> Boolean): Boolean {
        var played = false
        for ((i, id) in p.sequenceIds.withIndex()) {
            if (!keepGoing() || !currentCoroutineContext().isActive) break
            val seq = sequences.get(id) ?: continue
            val map = store.houseMap.first()
            player.sequence = seq
            player.layout = SequenceRenderer.prepare(map, seq.tracks)
            withContext(Dispatchers.IO) { player.loadAudio(seq.audioFile) }
            _state.value = _state.value.copy(nowPlaying = seq.name, playlistName = p.name, nextStart = null)
            player.seek(0)
            player.setLive(true)
            player.play()
            played = true
            while (player.playing.value && currentCoroutineContext().isActive) delay(200)
            if (i < p.sequenceIds.lastIndex || p.loop) {
                player.pause()
                delay(p.gapSec * 1000L)
            }
        }
        player.stopAll()
        _state.value = _state.value.copy(nowPlaying = null)
        return played
    }

    private suspend fun turnAllOff() {
        store.devices.first().forEach { d ->
            try {
                api.postState(d.host, buildJsonObject { put("on", false) })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
