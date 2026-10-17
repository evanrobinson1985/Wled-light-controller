package com.wledcontroller.app.show

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Loudness of a song over time (one value per [HOP_MS]), normalised to 0..1. */
class AudioEnvelope(val values: FloatArray) {
    val hopMs get() = HOP_MS
    fun at(ms: Long): Float = values.getOrElse((ms / HOP_MS).toInt()) { 0f }

    companion object { const val HOP_MS = 10 }
}

data class BeatResult(val bpm: Float, val beats: List<Long>)

/** Decodes songs to a loudness envelope and finds the beat grid in it. */
object AudioAnalyzer {

    /** Envelope for [path], cached next to it so reopening a show is instant. Blocking. */
    fun envelope(path: String): AudioEnvelope {
        val cache = File("$path.env")
        if (cache.exists()) runCatching {
            DataInputStream(cache.inputStream().buffered()).use { inp ->
                val n = inp.readInt()
                return AudioEnvelope(FloatArray(n) { inp.readFloat() })
            }
        }
        val env = decode(path)
        runCatching {
            DataOutputStream(cache.outputStream().buffered()).use { out ->
                out.writeInt(env.values.size); env.values.forEach { out.writeFloat(it) }
            }
        }
        return env
    }

    private fun decode(path: String): AudioEnvelope {
        val ex = MediaExtractor()
        ex.setDataSource(path)
        val track = (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("No audio in that file")
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()

        var sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false
        val rms = ArrayList<Float>(40_000)
        var acc = 0.0
        var accCount = 0
        var hopFrames = sampleRate * AudioEnvelope.HOP_MS / 1000

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        hopFrames = sampleRate * AudioEnvelope.HOP_MS / 1000
                    }
                    outIdx >= 0 -> {
                        val out = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.nativeOrder())
                        out.position(info.offset); out.limit(info.offset + info.size)
                        if (isFloat) {
                            val fb = out.asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var m = 0f
                                repeat(channels) { m += fb.get() }
                                m /= channels
                                acc += m * m; accCount++
                                if (accCount >= hopFrames) { rms += sqrt(acc / accCount).toFloat(); acc = 0.0; accCount = 0 }
                            }
                        } else {
                            val sb = out.asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var m = 0f
                                repeat(channels) { m += sb.get() / 32768f }
                                m /= channels
                                acc += m * m; accCount++
                                if (accCount >= hopFrames) { rms += sqrt(acc / accCount).toFloat(); acc = 0.0; accCount = 0 }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            ex.release()
        }
        val max = rms.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return AudioEnvelope(FloatArray(rms.size) { rms[it] / max })
    }

    /**
     * Finds a steady beat grid: onset strength from rises in loudness, tempo by autocorrelation
     * (60–180 BPM, preferring ~120), best phase, then each beat nudged to the nearest onset.
     */
    fun detectBeats(env: AudioEnvelope): BeatResult {
        val e = env.values
        if (e.size < 400) return BeatResult(0f, emptyList())
        val logE = FloatArray(e.size) { ln(e[it] * e[it] + 1e-6f) }
        val onset = FloatArray(e.size)
        for (i in 1 until e.size) onset[i] = (logE[i] - logE[i - 1]).coerceAtLeast(0f)
        // Remove the slowly varying part so only sharp hits stand out.
        val win = 30
        var run = 0f
        val novelty = FloatArray(e.size)
        for (i in onset.indices) {
            run += onset[i]
            if (i >= win) run -= onset[i - win]
            val avg = run / minOf(i + 1, win)
            novelty[i] = (onset[i] - avg).coerceAtLeast(0f)
        }

        val hop = AudioEnvelope.HOP_MS
        val minLag = 60_000 / 180 / hop
        val maxLag = 60_000 / 60 / hop
        var bestLag = minLag
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            var s = 0.0
            for (i in lag until novelty.size) s += novelty[i] * novelty[i - lag]
            val bpm = 60_000.0 / (lag * hop)
            val pref = exp(-0.5 * (ln(bpm / 120.0) / 0.45).let { it * it })
            val score = s / (novelty.size - lag) * pref
            if (score > bestScore) { bestScore = score; bestLag = lag }
        }
        // Parabolic refinement for a fractional period.
        fun ac(lag: Int): Double {
            var s = 0.0
            for (i in lag until novelty.size) s += novelty[i] * novelty[i - lag]
            return s / (novelty.size - lag)
        }
        val a = ac(bestLag - 1); val b = ac(bestLag); val c = ac(bestLag + 1)
        val shift = if (a - 2 * b + c != 0.0) (0.5 * (a - c) / (a - 2 * b + c)).coerceIn(-0.5, 0.5) else 0.0
        val period = bestLag + shift

        var bestPhase = 0
        var bestPhaseScore = -1.0
        for (p in 0 until bestLag) {
            var s = 0.0
            var t = p.toDouble()
            while (t < novelty.size) { s += novelty[t.toInt()]; t += period }
            if (s > bestPhaseScore) { bestPhaseScore = s; bestPhase = p }
        }

        val beats = ArrayList<Long>()
        val slack = (period / 8).toInt().coerceAtLeast(1)
        var t = bestPhase.toDouble()
        while (t < novelty.size) {
            val center = t.toInt()
            var best = center
            for (i in (center - slack).coerceAtLeast(0)..(center + slack).coerceAtMost(novelty.size - 1)) {
                if (novelty[i] > novelty[best]) best = i
            }
            beats += best.toLong() * hop
            t += period
        }
        return BeatResult((60_000.0 / (period * hop)).toFloat(), beats)
    }
}
