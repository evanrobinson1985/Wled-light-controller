package com.wledcontroller.app.show

import android.os.SystemClock
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.house.DdpSender
import com.wledcontroller.app.house.HouseEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays a sequence: advances the playhead and, while [live], streams each frame to the
 * controllers. While paused and live it keeps sending the frame at the playhead, so scrubbing
 * shows on the house.
 */
class SequencePlayer(private val store: AppStore, private val house: HouseEngine, private val scope: CoroutineScope) {

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    @Volatile var sequence: Sequence? = null
    @Volatile var layout: PreparedLayout? = null

    private var loop: Job? = null

    // The song, when the show has one, is the master clock.
    private var media: android.media.MediaPlayer? = null
    private var mediaPath: String? = null
    @Volatile private var lastMediaPos = -1L
    @Volatile private var mediaAnchor = 0L

    /** Loads (or with null, releases) the show's song. */
    fun loadAudio(path: String?) {
        if (path == mediaPath) return
        media?.release()
        media = null
        mediaPath = path
        envelope = null
        if (path != null) {
            media = runCatching { android.media.MediaPlayer().apply { setDataSource(path); prepare() } }.getOrNull()
            // Loudness over time, for the music effects (cached after the first time).
            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                val env = runCatching { AudioAnalyzer.envelope(path) }.getOrNull()
                if (mediaPath == path) envelope = env
            }
        }
    }

    /** The current song's loudness, once analysed. */
    @Volatile var envelope: AudioEnvelope? = null
        private set

    val hasAudio get() = media != null

    fun play() {
        val seq = sequence ?: return
        if (_position.value >= seq.durationMs) _position.value = 0
        media?.let { mp ->
            if (_position.value < mp.duration) {
                seekMedia(mp, _position.value)
                lastMediaPos = -1L
                mp.start()
            }
        }
        _playing.value = true
        ensureLoop()
    }

    fun pause() {
        _playing.value = false
        media?.let { if (it.isPlaying) it.pause() }
    }

    fun seek(ms: Long) {
        _position.value = ms.coerceIn(0, sequence?.durationMs ?: ms)
        media?.let { mp ->
            if (_position.value < mp.duration) seekMedia(mp, _position.value) else if (mp.isPlaying) mp.pause()
            lastMediaPos = -1L
        }
    }

    private fun seekMedia(mp: android.media.MediaPlayer, ms: Long) {
        if (android.os.Build.VERSION.SDK_INT >= 26) mp.seekTo(ms, android.media.MediaPlayer.SEEK_CLOSEST) else mp.seekTo(ms.toInt())
    }

    fun setLive(on: Boolean) {
        _live.value = on
        if (on) { house.stop(); ensureLoop() }
    }

    /** Stops playback and streaming; the controllers return to their own effects shortly after. */
    fun stopAll() {
        pause()
        _live.value = false
    }

    private fun ensureLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch(Dispatchers.Default) {
            val hosts = store.devices.first().associate { it.id to it.host }
            DdpSender().use { ddp ->
                var last = SystemClock.elapsedRealtime()
                var colors = IntArray(0)
                while (_playing.value || _live.value) {
                    val now = SystemClock.elapsedRealtime()
                    val seq = sequence
                    if (_playing.value && seq != null) {
                        val mp = media
                        val audioPlaying = mp != null && runCatching { mp.isPlaying }.getOrDefault(false)
                        val next = if (audioPlaying) {
                            // MediaPlayer reports position in coarse steps: extrapolate between them.
                            val p = mp!!.currentPosition.toLong()
                            if (p != lastMediaPos) { lastMediaPos = p; mediaAnchor = now }
                            lastMediaPos + (now - mediaAnchor).coerceAtMost(250)
                        } else {
                            _position.value + (now - last)
                        }
                        if (next >= seq.durationMs) { _position.value = seq.durationMs; _playing.value = false; mp?.let { if (it.isPlaying) it.pause() } }
                        else _position.value = next
                    }
                    last = now
                    val lay = layout
                    val fseqPath = seq?.fseqFile
                    // With a song, shift the lights to line up with what's actually heard.
                    val renderPos = if (media != null) (_position.value - ShowSettings.lightsDelayMs).coerceAtLeast(0) else _position.value
                    if (_live.value && seq != null && fseqPath != null) {
                        val reader = runCatching { FseqReader.open(fseqPath) }.getOrNull()
                        if (reader != null) {
                            val frame = reader.frame((renderPos / reader.stepMs).toInt())
                            withContext(Dispatchers.IO) {
                                for (m in seq.fseqMap) {
                                    if (!m.enabled || m.ledCount <= 0) continue
                                    val host = hosts[m.deviceId] ?: continue
                                    val buf = ByteArray(m.ledCount * 3)
                                    reader.copyChannels(frame, m.startChannel - 1, buf.size, buf)
                                    runCatching { ddp.send(host, buf) }
                                }
                            }
                        }
                    } else if (_live.value && seq != null && lay != null && lay.leds.isNotEmpty()) {
                        if (colors.size != lay.leds.size) colors = IntArray(lay.leds.size)
                        SequenceRenderer.render(seq, lay, renderPos, colors, envelope)
                        val buffers = HashMap<String, ByteArray>()
                        lay.leds.forEach { l ->
                            val needed = (l.index + 1) * 3
                            val cur = buffers[l.deviceId]
                            if (cur == null || cur.size < needed) buffers[l.deviceId] = (cur ?: ByteArray(0)).copyOf(needed)
                        }
                        lay.leds.forEachIndexed { i, l ->
                            val b = buffers.getValue(l.deviceId); val o = l.index * 3; val c = colors[i]
                            b[o] = (c shr 16).toByte(); b[o + 1] = (c shr 8).toByte(); b[o + 2] = c.toByte()
                        }
                        withContext(Dispatchers.IO) {
                            buffers.forEach { (id, b) -> hosts[id]?.let { h -> runCatching { ddp.send(h, b) } } }
                        }
                    }
                    delay(25)
                }
            }
        }
    }
}
