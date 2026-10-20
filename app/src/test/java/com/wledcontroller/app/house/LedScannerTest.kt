package com.wledcontroller.app.house

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class LedScannerTest {

    /**
     * A pretend camera over a dark house: a lamp that flickers on its own, a neighbour's
     * controller that would run an effect unless held dark, and five LEDs that light up a
     * network delay after they're sent (longer than the scan's expected delay, as on slow Wi-Fi).
     */
    @Test
    fun findsEachLedOnceAndIgnoresLampsAndOtherLights() = runBlocking {
        val w = 80; val h = 60
        val leds = listOf(10 to 40, 25 to 42, 40 to 44, 55 to 42, 70 to 40)
        val lamp = 20 to 10
        val neighbourSpot = 60 to 12
        val delayMs = 160L
        // What each host was last sent, and when.
        val sent = ArrayList<Triple<Long, String, ByteArray>>()
        val scanner = LedScanner(sendFrame = { host, rgb -> synchronized(sent) { sent += Triple(LedScanner.now(), host, rgb.copyOf()) } })

        fun lastSent(host: String, before: Long) = synchronized(sent) { sent.lastOrNull { it.second == host && it.first <= before }?.third }

        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val rnd = Random(7)
        val camera = Thread {
            while (running.get()) {
                val t = LedScanner.now()
                val luma = IntArray(w * h) { 20 + rnd.nextInt(3) }
                fun glow(x: Int, y: Int, v: Int) {
                    for (dy in -1..1) for (dx in -1..1) luma[(y + dy) * w + x + dx] = (luma[(y + dy) * w + x + dx] + v).coerceAtMost(255)
                }
                // The lamp flickers no matter what.
                if (rnd.nextBoolean()) glow(lamp.first, lamp.second, 120)
                // The neighbour's controller twinkles unless it's been told to go dark.
                val n = lastSent("neighbour", t - delayMs)
                if (n == null && rnd.nextBoolean()) glow(neighbourSpot.first, neighbourSpot.second, 150)
                val buf = lastSent("roof", t - delayMs)
                if (buf != null) leds.forEachIndexed { i, (x, y) -> if (buf.getOrElse(i * 3) { 0 } != 0.toByte()) glow(x, y, 160) }
                scanner.onLuma(LumaFrame(luma, w, h, t))
                Thread.sleep(33)
            }
        }
        camera.start()
        try {
            val found = scanner.scan(
                listOf(ScanTarget("roof", "roof", "Roof", 0, leds.size + 1)),
                settleMs = 80,
                darkHosts = mapOf("neighbour" to 50),
            ) { }
            val roof = found.single()
            leds.forEachIndexed { i, (x, y) ->
                assertNotNull("LED $i", roof[i])
                val p = roof[i]!!
                assertEquals("LED $i x", (x + 0.5f) / w, p.x, 1.5f / w)
                assertEquals("LED $i y", (y + 0.5f) / h, p.y, 1.5f / h)
            }
            // There's no sixth LED: nothing (not the lamp) is taken for it.
            assertNull(roof[leds.size])
        } finally {
            running.set(false)
            camera.join()
        }
    }
}
