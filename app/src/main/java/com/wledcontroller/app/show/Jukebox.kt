package com.wledcontroller.app.show

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The viewer jukebox (like Remote Falcon): visitors pick what plays next from their phones,
 * either by requesting shows (played in the order asked) or by voting (the most votes plays
 * next). Each visitor can ask once every [cooldownMs]. Between requests it can keep shows
 * going on its own.
 */
class Jukebox(private val clock: () -> Long = System::currentTimeMillis) {

    enum class Mode(val label: String) { REQUESTS("Requests"), VOTES("Votes") }

    data class Song(val id: String, val title: String, val durationMs: Long, val hasAudio: Boolean)

    sealed interface Outcome {
        data object Ok : Outcome
        data object AlreadyQueued : Outcome
        data object Unknown : Outcome
        data object QueueFull : Outcome
        data class TooSoon(val secondsLeft: Int) : Outcome
        val message: String get() = when (this) {
            Ok -> "Got it!"
            AlreadyQueued -> "That one's already coming up."
            Unknown -> "That show isn't available."
            QueueFull -> "The list is full right now — try again in a bit."
            is TooSoon -> "One pick at a time — try again in $secondsLeft s."
        }
    }

    @Volatile var songs: List<Song> = emptyList()
    @Volatile var mode: Mode = Mode.REQUESTS
    /** Play shows on its own when nobody has asked for one. */
    @Volatile var playWhenIdle: Boolean = true
    @Volatile var cooldownMs: Long = 60_000
    @Volatile var maxQueue: Int = 20
    /** Pause between shows, ms. */
    @Volatile var gapMs: Long = 3_000

    /** What's playing and where it is; set by the player. */
    @Volatile var nowPlaying: Song? = null
    @Volatile var audioPath: String? = null
    @Volatile var position: () -> Long = { 0L }

    private val queue = ArrayList<String>()
    private val votes = LinkedHashMap<String, MutableSet<String>>()
    private val lastAsk = HashMap<String, Long>()
    private var lastPlayed: String? = null

    @Synchronized
    fun request(client: String, songId: String): Outcome {
        if (songs.none { it.id == songId }) return Outcome.Unknown
        cooldown(client)?.let { return it }
        return when (mode) {
            Mode.REQUESTS -> {
                if (songId in queue || nowPlaying?.id == songId) return Outcome.AlreadyQueued
                if (queue.size >= maxQueue) return Outcome.QueueFull
                queue += songId
                lastAsk[client] = clock()
                Outcome.Ok
            }
            Mode.VOTES -> {
                // One vote per visitor per round: a new vote moves theirs.
                votes.values.forEach { it.remove(client) }
                votes.getOrPut(songId) { LinkedHashSet() } += client
                lastAsk[client] = clock()
                Outcome.Ok
            }
        }
    }

    private fun cooldown(client: String): Outcome.TooSoon? {
        val last = lastAsk[client] ?: return null
        val left = cooldownMs - (clock() - last)
        return if (left > 0) Outcome.TooSoon(((left + 999) / 1000).toInt()) else null
    }

    /** The next show to play, taken off the list (votes are counted and cleared). */
    @Synchronized
    fun next(): Song? {
        val byId = songs.associateBy { it.id }
        val chosen: Song? = when (mode) {
            Mode.REQUESTS -> {
                var s: Song? = null
                while (s == null && queue.isNotEmpty()) s = byId[queue.removeAt(0)]
                s
            }
            Mode.VOTES -> {
                val best = votes.entries.filter { it.value.isNotEmpty() && it.key in byId }.maxByOrNull { it.value.size }
                votes.clear()
                best?.let { byId[it.key] }
            }
        } ?: if (playWhenIdle && songs.isNotEmpty()) {
            // Nobody asked: something different from last time.
            val choices = songs.filter { it.id != lastPlayed }.ifEmpty { songs }
            choices[(clock() / 1000 % choices.size).toInt()]
        } else null
        if (chosen != null) lastPlayed = chosen.id
        return chosen
    }

    @Synchronized
    fun clear() { queue.clear(); votes.clear(); lastAsk.clear() }

    @Synchronized
    fun queued(): List<Song> = queue.mapNotNull { id -> songs.firstOrNull { it.id == id } }

    @Synchronized
    fun voteCounts(): Map<String, Int> = votes.mapValues { it.value.size }.filterValues { it > 0 }

    /** Everything the visitors' page shows, as JSON. */
    @Synchronized
    fun stateJson(client: String?): String = buildJsonObject {
        put("mode", mode.name.lowercase())
        nowPlaying?.let { s ->
            put("nowPlaying", s.title)
            put("nowId", s.id)
            put("positionMs", position())
            put("durationMs", s.durationMs)
            put("hasAudio", s.hasAudio && audioPath != null)
        }
        val counts = votes.mapValues { it.value.size }
        putJsonArray("songs") {
            songs.forEach { s ->
                addJsonObject {
                    put("id", s.id); put("title", s.title); put("durationMs", s.durationMs)
                    put("votes", counts[s.id] ?: 0)
                    put("queued", s.id in queue)
                }
            }
        }
        putJsonArray("queue") { queue.forEach { id -> songs.firstOrNull { it.id == id }?.let { add(kotlinx.serialization.json.JsonPrimitive(it.title)) } } }
        client?.let { c -> cooldown(c)?.let { put("waitSeconds", it.secondsLeft) } }
    }.toString()
}
