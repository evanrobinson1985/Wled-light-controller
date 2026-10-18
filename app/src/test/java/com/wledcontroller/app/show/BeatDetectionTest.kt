package com.wledcontroller.app.show

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatDetectionTest {

    /** Loudness with a sharp hit every [periodHops] hops (10 ms each), decaying between hits. */
    private fun pulses(periodHops: Int, offset: Int, length: Int = 3000) = AudioEnvelope(FloatArray(length) { i ->
        val since = (i - offset).mod(periodHops)
        0.1f + 0.9f * Math.exp(-since / 6.0).toFloat()
    })

    @Test
    fun findsTempoAndPhase() {
        val result = AudioAnalyzer.detectBeats(pulses(periodHops = 50, offset = 17))
        assertEquals(120f, result.bpm, 3f)
        assertTrue(result.beats.size > 50)
        // Beats land on the hits (hop 17 + n*50 → 170 ms + n*500 ms), within a hop.
        for (b in result.beats.drop(1).take(20)) {
            val off = (b - 170).mod(500L)
            assertTrue("beat at $b ms is off the grid", off <= 10 || off >= 490)
        }
    }

    @Test
    fun slowerSong() {
        assertEquals(90f, AudioAnalyzer.detectBeats(pulses(periodHops = 67, offset = 0)).bpm, 3f)
    }

    @Test
    fun tooShortGivesNothing() {
        val r = AudioAnalyzer.detectBeats(AudioEnvelope(FloatArray(100)))
        assertEquals(0f, r.bpm)
        assertTrue(r.beats.isEmpty())
    }
}
