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

    fun play() {
        val seq = sequence ?: return
        if (_position.value >= seq.durationMs) _position.value = 0
        _playing.value = true
        ensureLoop()
    }

    fun pause() { _playing.value = false }

    fun seek(ms: Long) {
        _position.value = ms.coerceIn(0, sequence?.durationMs ?: ms)
    }

    fun setLive(on: Boolean) {
        _live.value = on
        if (on) { house.stop(); ensureLoop() }
    }

    /** Stops playback and streaming; the controllers return to their own effects shortly after. */
    fun stopAll() {
        _playing.value = false
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
                        val next = _position.value + (now - last)
                        if (next >= seq.durationMs) { _position.value = seq.durationMs; _playing.value = false } else _position.value = next
                    }
                    last = now
                    val lay = layout
                    if (_live.value && seq != null && lay != null && lay.leds.isNotEmpty()) {
                        if (colors.size != lay.leds.size) colors = IntArray(lay.leds.size)
                        SequenceRenderer.render(seq, lay, _position.value, colors)
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
