package com.wledcontroller.app.show

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How loud each part of the sound is, from bass to treble, over a sliding window of samples
 * (Goertzel filters on [BANDS] frequencies spaced evenly in pitch from 60 Hz to 8 kHz). Feeds
 * the Music (spectrum) effect and the piano, like xLights' audio analysis.
 */
class BandAnalyzer(sampleRate: Int, private val window: Int = 1024) {
    private val buf = FloatArray(window)
    private var pos = 0
    private var filled = 0
    private val coeff = FloatArray(BANDS) { b ->
        val f = frequency(b).coerceAtMost(sampleRate / 2f - 1f)
        (2.0 * cos(2.0 * PI * f / sampleRate)).toFloat()
    }

    fun push(sample: Float) {
        buf[pos] = sample; pos = (pos + 1) % window
        if (filled < window) filled++
    }

    /** The current strength of each band (not normalised). */
    fun bands(): FloatArray = FloatArray(BANDS) { b ->
        if (filled < window / 4) return@FloatArray 0f
        var s1 = 0f; var s2 = 0f
        val c = coeff[b]
        for (i in 0 until window) {
            // Oldest sample first, with a Hann window to keep neighbouring bands apart.
            val x = buf[(pos + i) % window] * HANN[i % HANN.size]
            val s = x + c * s1 - s2
            s2 = s1; s1 = s
        }
        sqrt((s1 * s1 + s2 * s2 - c * s1 * s2).coerceAtLeast(0f)) / window
    }

    companion object {
        const val BANDS = 12
        /** Centre frequency of band [b], in Hz. */
        fun frequency(b: Int): Float = 60f * (8000f / 60f).pow(b / (BANDS - 1f))
        private val HANN = FloatArray(1024) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / 1023)).toFloat() }

        /** Each band scaled to 0..1 by its loudest moment, softened so quieter parts still show. */
        fun normalise(rows: List<FloatArray>): Array<FloatArray> {
            val out = Array(BANDS) { FloatArray(rows.size) }
            for (b in 0 until BANDS) {
                val max = rows.maxOfOrNull { it[b] }?.takeIf { it > 0f } ?: 1f
                for (i in rows.indices) out[b][i] = sqrt(rows[i][b] / max)
            }
            return out
        }
    }
}
