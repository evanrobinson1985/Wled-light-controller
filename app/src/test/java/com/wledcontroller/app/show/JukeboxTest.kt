package com.wledcontroller.app.show

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class JukeboxTest {
    private var now = 0L
    private fun box() = Jukebox { now }.apply {
        songs = listOf(Jukebox.Song("a", "Carol", 60_000, true), Jukebox.Song("b", "Jingle", 90_000, true), Jukebox.Song("c", "Silent", 120_000, false))
        playWhenIdle = false
    }

    @Test
    fun requestsPlayInOrderAndEachVisitorWaits() {
        val j = box()
        assertEquals(Jukebox.Outcome.Ok, j.request("1.1.1.1", "b"))
        assertTrue(j.request("1.1.1.1", "c") is Jukebox.Outcome.TooSoon)
        assertEquals(Jukebox.Outcome.AlreadyQueued, j.request("2.2.2.2", "b"))
        assertEquals(Jukebox.Outcome.Ok, j.request("2.2.2.2", "a"))
        assertEquals(Jukebox.Outcome.Unknown, j.request("3.3.3.3", "zzz"))
        now += 61_000
        assertEquals(Jukebox.Outcome.Ok, j.request("1.1.1.1", "c"))
        assertEquals(listOf("b", "a", "c"), listOf(j.next()!!.id, j.next()!!.id, j.next()!!.id))
        assertNull(j.next())
    }

    @Test
    fun votesPickTheMostPopularAndOneVoteEach() {
        val j = box().apply { mode = Jukebox.Mode.VOTES; cooldownMs = 0 }
        j.request("x", "a"); j.request("y", "b"); j.request("z", "b")
        j.request("x", "b") // x changes their mind
        assertEquals(mapOf("b" to 3), j.voteCounts())
        assertEquals("b", j.next()!!.id)
        assertTrue(j.voteCounts().isEmpty())
    }

    @Test
    fun keepsPlayingWhenNobodyAsks() {
        val j = box().apply { playWhenIdle = true }
        val first = j.next()!!.id
        assertTrue(j.next()!!.id != first)
    }

    private fun get(url: String, method: String = "GET", range: String? = null): Triple<Int, String, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        range?.let { c.setRequestProperty("Range", it) }
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        return Triple(code, c.contentType ?: "", body)
    }

    @Test
    fun servesThePageRequestsAndTheSong() {
        val j = box().apply { cooldownMs = 0 }
        val song = File.createTempFile("song", ".mp3").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
        j.nowPlaying = j.songs[0]; j.audioPath = song.absolutePath; j.position = { 12_345L }
        val server = JukeboxServer(j) { "Smith Family Lights" }
        val port = server.start(18_080)
        try {
            val base = "http://127.0.0.1:$port"
            val (code, type, page) = get("$base/")
            assertEquals(200, code); assertTrue(type.startsWith("text/html"))
            assertTrue(String(page).contains("Smith Family Lights"))
            val state = String(get("$base/api/state").third)
            assertTrue(state, state.contains("\"nowPlaying\":\"Carol\"") && state.contains("\"positionMs\":12345"))
            assertEquals(200, get("$base/api/request?id=b", "POST").first)
            assertEquals(409, get("$base/api/request?id=b", "POST").first) // already coming up
            assertEquals("b", j.next()!!.id)
            // The song, whole and in part.
            assertEquals(1000, get("$base/audio").third.size)
            val (pc, _, part) = get("$base/audio", range = "bytes=100-199")
            assertEquals(206, pc); assertEquals(100, part.size); assertEquals(100.toByte(), part[0])
            assertEquals(404, get("$base/nope").first)
        } finally {
            server.stop()
        }
    }
}
