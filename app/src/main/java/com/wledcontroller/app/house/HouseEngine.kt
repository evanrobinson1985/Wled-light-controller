package com.wledcontroller.app.house

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import com.wledcontroller.app.data.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Streams whole-house effects from the phone: renders every mapped LED each frame and sends one
 * DDP frame per controller. Runs in the app process; [HouseEffectService] keeps it alive.
 */
class HouseEngine(private val context: Context, private val store: AppStore, private val scope: CoroutineScope) {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    val params = MutableStateFlow(HouseEffectParams())

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        _running.value = true
        job = scope.launch(Dispatchers.Default) {
            try {
                streamLoop()
            } finally {
                _running.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _running.value = false
    }

    private suspend fun streamLoop() {
        val map = store.houseMap.first()
        val hosts = store.streamTargets()
        val leds = map.placedLeds().filter { it.deviceId in hosts }
        if (leds.isEmpty()) return
        // One buffer per device, sized to its highest mapped LED.
        val buffers = leds.groupBy { it.deviceId }.mapValues { (_, l) -> ByteArray((l.maxOf { it.index } + 1) * 3) }
        val start = SystemClock.elapsedRealtime()
        DdpSender().use { ddp ->
            while (currentCoroutineContextActive()) {
                val frameStart = SystemClock.elapsedRealtime()
                val t = (frameStart - start) / 1000f
                val p = params.value
                for ((i, led) in leds.withIndex()) {
                    val rgb = HouseRenderer.color(p, led.x, led.y, t, i, led.orderInRun)
                    val buf = buffers.getValue(led.deviceId)
                    val o = led.index * 3
                    buf[o] = (rgb shr 16).toByte(); buf[o + 1] = (rgb shr 8).toByte(); buf[o + 2] = rgb.toByte()
                }
                withContext(Dispatchers.IO) {
                    for ((id, buf) in buffers) runCatching { ddp.send(hosts.getValue(id), buf) }
                }
                val wait = FRAME_MS - (SystemClock.elapsedRealtime() - frameStart)
                delay(wait.coerceAtLeast(1))
            }
        }
    }

    private suspend fun currentCoroutineContextActive() = kotlinx.coroutines.currentCoroutineContext().isActive

    /** Lights one run white with its first LED red for a few seconds, so you can see where it starts. */
    fun identify(run: LightRun, host: String) {
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(run.endLed * 3)
            for (i in run.startLed until run.endLed) {
                val o = i * 3
                if (i == run.startLed) { buf[o] = 0xFF.toByte() } else { buf[o] = 0x60; buf[o + 1] = 0x60; buf[o + 2] = 0x60 }
            }
            DdpSender().use { ddp ->
                repeat(16) { runCatching { ddp.send(host, buf) }; delay(250) }
            }
        }
    }

    /** Lights a single LED (white) for a few seconds, everything else on that controller off. */
    fun identifyLed(host: String, index: Int) {
        identifyJob?.cancel()
        identifyJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray((index + 1) * 3)
            buf[index * 3] = 0xFF.toByte(); buf[index * 3 + 1] = 0xFF.toByte(); buf[index * 3 + 2] = 0xFF.toByte()
            DdpSender().use { ddp -> repeat(12) { runCatching { ddp.send(host, buf) }; delay(250) } }
        }
    }
    private var identifyJob: Job? = null

    /** Copies a picked photo into app storage, downscaled so it is cheap to draw. Returns (path, aspect). */
    suspend fun importPhoto(uri: Uri): Pair<String, Float> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bmp = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Couldn't read that image")
        savePhoto(bmp)
    }

    /** Saves a camera frame as the map background. Returns (path, aspect). */
    suspend fun importBitmap(bmp: Bitmap): Pair<String, Float> = withContext(Dispatchers.IO) { savePhoto(bmp) }

    private fun savePhoto(bmp: Bitmap): Pair<String, Float> {
        val file = File(context.filesDir, "house_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        context.filesDir.listFiles { f -> f.name.startsWith("house_") && f != file }?.forEach { it.delete() }
        return file.absolutePath to bmp.width.toFloat() / bmp.height
    }

    private companion object {
        const val FRAME_MS = 25L // 40 fps
    }
}
