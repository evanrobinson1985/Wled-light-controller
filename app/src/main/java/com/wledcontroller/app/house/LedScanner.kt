package com.wledcontroller.app.house

import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
class LedScanner {

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
        latest = LumaFrame(px, w, h, SystemClock.elapsedRealtime())
    }

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

    private suspend fun averagedFrame(after: Long, frames: Int): LumaFrame {
        var f = frameAfter(after)
        val sum = f.luma.copyOf()
        repeat(frames - 1) {
            f = frameAfter(f.time)
            for (i in sum.indices) sum[i] += f.luma[i]
        }
        for (i in sum.indices) sum[i] /= frames
        return LumaFrame(sum, f.width, f.height, f.time)
    }

    /**
     * Runs the scan. [settleMs] is how long to wait after lighting an LED before trusting a frame
     * (network + camera latency). Unlit LEDs on all targets stay off for the whole scan.
     */
    suspend fun scan(
        targets: List<ScanTarget>,
        settleMs: Long,
        onProgress: (ScanProgress) -> Unit,
    ): List<List<MapPoint?>> = withContext(Dispatchers.Default) {
        wantsFrames = true
        val found = targets.map { MutableList<MapPoint?>(it.count) { null } }
        val buffers = targets.associate { it.deviceId to ByteArray((it.startLed + it.count).coerceAtLeast(1) * 3) }
        try {
            DdpSender().use { ddp ->
                suspend fun sendAll() = withContext(Dispatchers.IO) {
                    for (t in targets) runCatching { ddp.send(t.host, buffers.getValue(t.deviceId)) }
                }
                suspend fun baseline(): LumaFrame {
                    buffers.values.forEach { it.fill(0) }
                    sendAll()
                    val sent = SystemClock.elapsedRealtime()
                    delay(settleMs * 2)
                    return averagedFrame(sent + settleMs, 3)
                }

                var base = baseline()
                targets.forEachIndexed { ti, target ->
                    val buf = buffers.getValue(target.deviceId)
                    for (i in 0 until target.count) {
                        // Refresh the reference now and then: auto-exposure and ambient light drift.
                        if (i > 0 && i % 25 == 0) base = baseline()
                        buffers.values.forEach { it.fill(0) }
                        val o = (target.startLed + i) * 3
                        buf[o] = 0xFF.toByte(); buf[o + 1] = 0xFF.toByte(); buf[o + 2] = 0xFF.toByte()
                        sendAll()
                        val sent = SystemClock.elapsedRealtime()
                        delay(settleMs)
                        val frame = frameAfter(sent + settleMs)
                        found[ti][i] = detect(base, frame)
                        onProgress(ScanProgress(ti, targets.size, i + 1, target.count, found.map { it.toList() }))
                    }
                }
                buffers.values.forEach { it.fill(0) }
                sendAll()
            }
        } finally {
            wantsFrames = false
        }
        found.map { it.toList() }
    }

    /** Finds the single new bright spot in [frame] versus [base]; null if nothing stands out. */
    private fun detect(base: LumaFrame, frame: LumaFrame): MapPoint? {
        if (base.width != frame.width || base.height != frame.height) return null
        val w = frame.width; val h = frame.height
        val diff = IntArray(w * h) { (frame.luma[it] - base.luma[it]).coerceAtLeast(0) }
        var mean = 0.0
        for (d in diff) mean += d
        mean /= diff.size
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

        // Brightness-weighted centre of the spot for sub-pixel accuracy.
        val bx = best % w; val by = best / w
        val cut = peak * 0.4
        var sx = 0.0; var sy = 0.0; var sw = 0.0
        for (y in (by - 5).coerceAtLeast(0)..(by + 5).coerceAtMost(h - 1)) {
            for (x in (bx - 5).coerceAtLeast(0)..(bx + 5).coerceAtMost(w - 1)) {
                val d = diff[y * w + x]
                if (d > cut) { sx += x * d.toDouble(); sy += y * d.toDouble(); sw += d.toDouble() }
            }
        }
        if (sw <= 0) return null
        return MapPoint(((sx / sw + 0.5) / w).toFloat(), ((sy / sw + 0.5) / h).toFloat())
    }

    private companion object {
        const val WORK_WIDTH = 320
        const val MIN_PEAK = 18.0
    }
}
