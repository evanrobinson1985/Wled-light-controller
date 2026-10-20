package com.wledcontroller.app.house

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/** A downsampled luminance image from the camera, upright, with the time it arrived. */
class LumaFrame(val luma: IntArray, val width: Int, val height: Int, val time: Long)

/** One controller (or LED range on it) to locate. */
data class ScanTarget(val deviceId: String, val host: String, val name: String, val startLed: Int, val count: Int)

data class ScanProgress(
    val targetIndex: Int,
    val targetCount: Int,
    val ledIndex: Int,
    val ledCount: Int,
    /** Found positions so far, per target (index = order in target). */
    val found: List<List<MapPoint?>>,
)

/**
 * Locates LEDs with the camera: lights one LED at a time and finds the bright spot that appears
 * compared with an all-off frame. The camera analyser feeds frames in through [onFrame].
 */
class LedScanner(
    /** Where LED frames go; DDP to the controllers unless a test swaps it. */
    private val sendFrame: ((host: String, rgb: ByteArray) -> Unit)? = null,
) {

    @Volatile private var latest: LumaFrame? = null
    @Volatile private var latestBitmap: Bitmap? = null
    /** The analyser only does the (costly) conversion while a scan wants frames. */
    @Volatile var wantsFrames = false
    @Volatile var wantsPhoto = false
    /** Width / height of the upright camera frame, for drawing results over the preview. */
    @Volatile var frameAspect = 0.75f

    /** Called on the camera thread with an upright frame. */
    fun onFrame(bitmap: Bitmap) {
        if (wantsPhoto) {
            latestBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        if (!wantsFrames) return
        val w = WORK_WIDTH
        val h = (bitmap.height.toFloat() / bitmap.width * w).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        for (i in px.indices) {
            val c = px[i]
            px[i] = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
        }
        onLuma(LumaFrame(px, w, h, now()))
    }

    /** Takes a frame that's already luminance (what [onFrame] makes; tests feed these directly). */
    fun onLuma(frame: LumaFrame) { latest = frame }

    /** Grabs a full-resolution colour frame for the map background. */
    suspend fun capturePhoto(): Bitmap {
        latestBitmap = null
        wantsPhoto = true
        try {
            while (true) {
                latestBitmap?.let { return it }
                delay(30)
            }
        } finally {
            wantsPhoto = false
        }
    }

    private suspend fun frameAfter(time: Long): LumaFrame {
        while (true) {
            currentCoroutineContext().ensureActive()
            val f = latest
            if (f != null && f.time > time) return f
            delay(10)
        }
    }

    /**
     * Runs the scan: every light on the [targets] and on [darkHosts] (other controllers, with their
     * LED counts, so their effects can't be mistaken for LEDs) goes off, then each LED flashes
     * white on its own. An LED
     * counts as found only once the camera sees it light up in two frames in a row at the same spot
     * and then sees that spot go dark again when it's switched off, so lamps, TVs and the previous
     * LED (on a slow network) aren't taken for it. [settleMs] is the expected network + camera
     * delay; each LED is given several times that before it's marked as not seen.
     */
    suspend fun scan(
        targets: List<ScanTarget>,
        settleMs: Long,
        darkHosts: Map<String, Int> = emptyMap(),
        onProgress: (ScanProgress) -> Unit,
    ): List<List<MapPoint?>> = withContext(Dispatchers.Default) {
        wantsFrames = true
        val found = targets.map { MutableList<MapPoint?>(it.count) { null } }
        val buffers = targets.associate { it.deviceId to ByteArray((it.startLed + it.count).coerceAtLeast(1) * 3) }
        // Other controllers (host to LED count) are held dark for the whole scan.
        val others = darkHosts.filterKeys { h -> targets.none { it.host == h } }.mapValues { (_, n) -> ByteArray(n.coerceAtLeast(1) * 3) }
        try {
            DdpSender().use { ddp ->
                val send = sendFrame ?: ddp::send
                var lastSent = 0L
                suspend fun sendAll() = withContext(Dispatchers.IO) {
                    // UDP can drop a packet, which would leave an LED lit: send each frame twice.
                    repeat(2) {
                        for (t in targets) runCatching { send(t.host, buffers.getValue(t.deviceId)) }
                        for ((h, black) in others) runCatching { send(h, black) }
                    }
                    lastSent = now()
                }
                // WLED goes back to its own effects ~2.5 s after realtime frames stop: keep them coming.
                suspend fun keepAlive() { if (now() - lastSent > 500) sendAll() }
                suspend fun allOff() { buffers.values.forEach { it.fill(0) }; sendAll() }

                // Everything off, then learn the background, and which spots flicker by themselves
                // (lamps, screens, reflections), so those are never taken for an LED.
                allOff()
                delay(settleMs * 3)
                val calm = ArrayList<LumaFrame>()
                var t0 = now()
                repeat(CALIBRATION_FRAMES) { val f = frameAfter(t0); calm += f; t0 = f.time; keepAlive() }
                val mask = flickerMask(calm)
                var dark = average(calm.takeLast(3))

                val timeout = (settleMs * 6).coerceIn(900L, 4000L)
                targets.forEachIndexed { ti, target ->
                    val buf = buffers.getValue(target.deviceId)
                    for (i in 0 until target.count) {
                        // Light this LED only.
                        buffers.values.forEach { it.fill(0) }
                        val o = (target.startLed + i) * 3
                        buf[o] = 0xFF.toByte(); buf[o + 1] = 0xFF.toByte(); buf[o + 2] = 0xFF.toByte()
                        sendAll()
                        val litAt = lastSent

                        // Wait for a new spot to show up in two frames running.
                        var spot: Spot? = null
                        var prev: Spot? = null
                        var t = litAt
                        while (now() - litAt < timeout) {
                            val f = frameAfter(t); t = f.time
                            keepAlive()
                            val d = detect(dark, f, mask)
                            if (d != null && prev != null && d.near(prev)) { spot = d.mergedWith(prev); break }
                            prev = d
                        }

                        // Off again: the spot must go dark, or it wasn't this LED.
                        allOff()
                        val offAt = lastSent
                        var confirmed = spot == null
                        var last: LumaFrame? = null
                        t = offAt
                        while (now() - offAt < timeout) {
                            val f = frameAfter(t); t = f.time
                            keepAlive()
                            last = f
                            if (spot == null) { if (f.time - offAt >= settleMs) break else continue }
                            if (brightness(dark, f, spot) < spot.strength * 0.35) { confirmed = true; break }
                        }
                        if (spot != null && confirmed) found[ti][i] = spot.point
                        // A fresh "all off" picture for the next LED: lamps and exposure drift.
                        last?.let { l -> dark = average(listOf(l, frameAfter(l.time))) }
                        onProgress(ScanProgress(ti, targets.size, i + 1, target.count, found.map { it.toList() }))
                    }
                }
                allOff()
            }
        } finally {
            wantsFrames = false
        }
        found.map { it.toList() }
    }

    /** A bright spot: where it is (on the picture), in frame pixels, and how bright. */
    private class Spot(val point: MapPoint, val px: Int, val py: Int, val strength: Double) {
        fun near(o: Spot) = abs(px - o.px) <= 3 && abs(py - o.py) <= 3
        fun mergedWith(o: Spot) = Spot(
            MapPoint((point.x + o.point.x) / 2f, (point.y + o.point.y) / 2f), px, py, minOf(strength, o.strength),
        )
    }

    private fun average(frames: List<LumaFrame>): LumaFrame {
        val f0 = frames.last()
        val sum = IntArray(f0.luma.size)
        for (f in frames) if (f.luma.size == sum.size) for (i in sum.indices) sum[i] += f.luma[i]
        for (i in sum.indices) sum[i] /= frames.size
        return LumaFrame(sum, f0.width, f0.height, f0.time)
    }

    /** Pixels (and their neighbours) that change by themselves while every LED is off. */
    private fun flickerMask(frames: List<LumaFrame>): BooleanArray {
        val f0 = frames.first()
        val w = f0.width; val h = f0.height
        val lo = f0.luma.copyOf(); val hi = f0.luma.copyOf()
        for (f in frames) if (f.luma.size == lo.size) for (i in lo.indices) {
            if (f.luma[i] < lo[i]) lo[i] = f.luma[i]
            if (f.luma[i] > hi[i]) hi[i] = f.luma[i]
        }
        val mask = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (hi[y * w + x] - lo[y * w + x] < FLICKER) continue
            for (dy in -2..2) for (dx in -2..2) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) mask[yy * w + xx] = true
            }
        }
        return mask
    }

    /** How much brighter [frame] is than [base] around [spot]. */
    private fun brightness(base: LumaFrame, frame: LumaFrame, spot: Spot): Double {
        if (base.width != frame.width || base.height != frame.height) return 0.0
        val w = frame.width; val h = frame.height
        var s = 0; var n = 0
        for (y in (spot.py - 1).coerceAtLeast(0)..(spot.py + 1).coerceAtMost(h - 1)) {
            for (x in (spot.px - 1).coerceAtLeast(0)..(spot.px + 1).coerceAtMost(w - 1)) {
                s += (frame.luma[y * w + x] - base.luma[y * w + x]).coerceAtLeast(0); n++
            }
        }
        return if (n == 0) 0.0 else s.toDouble() / n
    }

    /**
     * Finds the single new, small bright spot in [frame] versus [base], ignoring [mask]ed
     * (flickering) pixels; null if nothing stands out, or the whole picture changed (the camera
     * re-adjusting or a room light switched on).
     */
    private fun detect(base: LumaFrame, frame: LumaFrame, mask: BooleanArray): Spot? {
        if (base.width != frame.width || base.height != frame.height) return null
        val w = frame.width; val h = frame.height
        val diff = IntArray(w * h) { if (mask.getOrElse(it) { false }) 0 else (frame.luma[it] - base.luma[it]).coerceAtLeast(0) }
        var mean = 0.0
        for (d in diff) mean += d
        mean /= diff.size
        if (mean > MAX_MEAN_SHIFT) return null
        var variance = 0.0
        for (d in diff) variance += (d - mean) * (d - mean)
        val std = sqrt(variance / diff.size)

        // Strongest 3×3 neighbourhood, so single noisy pixels don't win.
        var best = -1; var bestSum = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            var s = 0
            for (dy in -1..1) for (dx in -1..1) s += diff[(y + dy) * w + x + dx]
            if (s > bestSum) { bestSum = s; best = y * w + x }
        }
        if (best < 0) return null
        val peak = bestSum / 9.0
        if (peak < MIN_PEAK || peak < mean + 6 * std) return null

        // An LED is a small spot: a big bright area is something else changing.
        val cut = peak * 0.4
        var bigArea = 0
        for (d in diff) if (d > cut) bigArea++
        if (bigArea > MAX_SPOT_PIXELS) return null

        // Brightness-weighted centre of the spot for sub-pixel accuracy.
        val bx = best % w; val by = best / w
        var sx = 0.0; var sy = 0.0; var sw = 0.0
        for (y in (by - 5).coerceAtLeast(0)..(by + 5).coerceAtMost(h - 1)) {
            for (x in (bx - 5).coerceAtLeast(0)..(bx + 5).coerceAtMost(w - 1)) {
                val d = diff[y * w + x]
                if (d > cut) { sx += x * d.toDouble(); sy += y * d.toDouble(); sw += d.toDouble() }
            }
        }
        if (sw <= 0) return null
        return Spot(MapPoint(((sx / sw + 0.5) / w).toFloat(), ((sy / sw + 0.5) / h).toFloat()), bx, by, peak)
    }

    companion object {
        /** Milliseconds on a steady clock (frames and the scan share it). */
        fun now() = System.nanoTime() / 1_000_000

        private const val WORK_WIDTH = 320
        private const val MIN_PEAK = 18.0
        /** Frames of "all off" used to learn what flickers by itself. */
        private const val CALIBRATION_FRAMES = 12
        /** Brightness change (0–255) that marks a pixel as flickering while the LEDs are off. */
        private const val FLICKER = 28
        /** Average change across the picture beyond which the frame is the camera re-adjusting. */
        private const val MAX_MEAN_SHIFT = 6.0
        /** Largest bright area (in 320-wide frame pixels) still taken for one LED. */
        private const val MAX_SPOT_PIXELS = 220
    }
}
