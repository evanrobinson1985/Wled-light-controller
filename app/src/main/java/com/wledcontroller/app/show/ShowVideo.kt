package com.wledcontroller.app.show

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.wledcontroller.app.house.HouseMap
import java.io.File
import java.nio.ByteBuffer

/**
 * Records a show as an MP4 video: every frame of the lights drawn on the house photo (as in the
 * preview), with the song, for sharing. Rendered as fast as the phone can, not in real time.
 */
object ShowVideo {
    private const val W = 1280
    private const val H = 720
    private const val FPS = 30
    private const val TIMEOUT_US = 10_000L

    private class Sample(val data: ByteArray, val timeUs: Long, val flags: Int)

    /** Records [seq] and saves it to Movies › WLED Controller. [onProgress] gets 0..1. Returns where it went. */
    fun record(context: Context, seq: Sequence, map: HouseMap, onProgress: (Float) -> Unit): String {
        val out = File(context.cacheDir, "show-video.mp4")
        out.delete()
        val audio = seq.audioFile?.takeIf { File(it).isFile }?.let { runCatching { encodeAudio(it, seq.durationMs) }.getOrNull() }
        val env = seq.audioFile?.let { runCatching { AudioAnalyzer.envelope(it) }.getOrNull() }
        val layout = SequenceRenderer.prepare(map, seq.tracks)
        val background = background(map)
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            encodeVideo(seq, map, layout, env, background, muxer, audio, onProgress)
        } finally {
            runCatching { muxer.release() }
        }
        return save(context, out, seq.name)
    }

    /** The house photo (or a dark canvas) fitted into the frame, and where the house sits in it. */
    private fun background(map: HouseMap): Pair<Bitmap, RectF> {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF07080C.toInt())
        val aspect = map.aspect.coerceAtLeast(0.1f)
        val (w, h) = if (aspect > W.toFloat() / H) W.toFloat() to W / aspect else H * aspect to H.toFloat()
        val box = RectF((W - w) / 2f, (H - h) / 2f, (W + w) / 2f, (H + h) / 2f)
        map.photoPath?.let { BitmapFactory.decodeFile(it) }?.let { photo ->
            // Darkened, so the lights stand out as they do at night.
            c.drawBitmap(photo, null, box, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 110 })
            photo.recycle()
        }
        return bmp to box
    }

    private fun drawFrame(target: Bitmap, background: Pair<Bitmap, RectF>, layout: PreparedLayout, colors: IntArray) {
        val c = Canvas(target)
        c.drawBitmap(background.first, 0f, 0f, null)
        val box = background.second
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = (box.width() / 260f).coerceIn(2f, 7f)
        for ((i, l) in layout.leds.withIndex()) {
            if (l.hidden) continue
            val rgb = colors[i]
            if (rgb == 0) continue
            val x = box.left + l.x * box.width(); val y = box.top + l.y * box.height()
            val col = 0xFF000000.toInt() or rgb
            glow.shader = RadialGradient(x, y, r * 3.2f, col and 0x66FFFFFF, 0, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r * 3.2f, glow)
            dot.color = col
            c.drawCircle(x, y, r, dot)
        }
    }

    private fun encodeVideo(
        seq: Sequence, map: HouseMap, layout: PreparedLayout, env: AudioEnvelope?, background: Pair<Bitmap, RectF>,
        muxer: MediaMuxer, audio: Pair<MediaFormat, List<Sample>>?, onProgress: (Float) -> Unit,
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        val frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val argb = IntArray(W * H)
        val colors = IntArray(layout.leds.size)
        val frames = (seq.durationMs * FPS / 1000).toInt().coerceAtLeast(1)
        var videoTrack = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        var frameIndex = 0
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val idx = enc.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        if (frameIndex >= frames) {
                            enc.queueInputBuffer(idx, 0, 0, frameIndex * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val ms = frameIndex * 1000L / FPS
                            SequenceRenderer.render(seq, layout, ms, colors, audio = env)
                            drawFrame(frame, background, layout, colors)
                            frame.getPixels(argb, 0, W, 0, 0, W, H)
                            val image = enc.getInputImage(idx)!!
                            writeYuv(image, argb)
                            enc.queueInputBuffer(idx, 0, W * H * 3 / 2, frameIndex * 1_000_000L / FPS, 0)
                            frameIndex++
                            onProgress(frameIndex / frames.toFloat())
                        }
                    }
                }
                val o = enc.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrack = muxer.addTrack(enc.outputFormat)
                        val audioTrack = audio?.let { muxer.addTrack(it.first) }
                        muxer.start(); started = true
                        if (audio != null && audioTrack != null) {
                            val ai = MediaCodec.BufferInfo()
                            for (s in audio.second) {
                                ai.set(0, s.data.size, s.timeUs, s.flags)
                                muxer.writeSampleData(audioTrack, ByteBuffer.wrap(s.data), ai)
                            }
                        }
                    }
                    o >= 0 -> {
                        val buf = enc.getOutputBuffer(o)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && started) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrack, buf, info)
                        }
                        enc.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            if (started) muxer.stop()
        } finally {
            runCatching { enc.stop() }
            enc.release()
            frame.recycle()
        }
    }

    /** Writes ARGB pixels into the encoder's YUV 4:2:0 image, whatever its layout. */
    private fun writeYuv(image: android.media.Image, argb: IntArray) {
        val planes = image.planes
        val y = planes[0]; val u = planes[1]; val v = planes[2]
        val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
        for (row in 0 until H) {
            for (col in 0 until W) {
                val p = argb[row * W + col]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                yb.put(row * y.rowStride + col * y.pixelStride, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
                if (row % 2 == 0 && col % 2 == 0) {
                    val cu = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val cv = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    ub.put((row / 2) * u.rowStride + (col / 2) * u.pixelStride, cu.coerceIn(0, 255).toByte())
                    vb.put((row / 2) * v.rowStride + (col / 2) * v.pixelStride, cv.coerceIn(0, 255).toByte())
                }
            }
        }
    }

    /** The song re-encoded as AAC (which MP4 always takes), up to [durationMs]. */
    private fun encodeAudio(path: String, durationMs: Long): Pair<MediaFormat, List<Sample>>? {
        val ex = MediaExtractor()
        ex.setDataSource(path)
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
        ex.selectTrack(track)
        val inFmt = ex.getTrackFormat(track)
        val dec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(inFmt, null, null, 0); dec.start()
        var enc: MediaCodec? = null
        var outFormat: MediaFormat? = null
        val samples = ArrayList<Sample>()
        val info = MediaCodec.BufferInfo()
        val endUs = durationMs * 1000
        var decInDone = false; var decOutDone = false; var encDone = false
        val pending = ArrayDeque<Pair<ByteArray, Long>>()
        var pendingEos = false
        fun startEncoder(rate: Int, channels: Int) {
            val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply { configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start() }
        }
        try {
            while (!encDone) {
                if (!decInDone) {
                    val i = dec.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                        if (n < 0 || ex.sampleTime > endUs) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); decInDone = true }
                        else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                if (!decOutDone) {
                    val o = dec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = dec.outputFormat
                        if (enc == null) startEncoder(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), f.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    } else if (o >= 0) {
                        if (enc == null) startEncoder(inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                        val b = dec.getOutputBuffer(o)!!
                        if (info.size > 0) {
                            val bytes = ByteArray(info.size); b.position(info.offset); b.get(bytes)
                            // Fed in pieces small enough for the encoder.
                            var off = 0
                            while (off < bytes.size) { val n = minOf(16 * 1024, bytes.size - off); pending += bytes.copyOfRange(off, off + n) to info.presentationTimeUs; off += n }
                        }
                        dec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { decOutDone = true; pendingEos = true }
                    }
                }
                val e = enc ?: continue
                if (pending.isNotEmpty() || pendingEos) {
                    val i = e.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        if (pending.isNotEmpty()) {
                            val (bytes, t) = pending.removeFirst()
                            e.getInputBuffer(i)!!.apply { clear(); put(bytes) }
                            e.queueInputBuffer(i, 0, bytes.size, t, 0)
                        } else {
                            e.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); pendingEos = false
                        }
                    }
                }
                val eo = e.dequeueOutputBuffer(info, TIMEOUT_US)
                if (eo == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outFormat = e.outputFormat
                else if (eo >= 0) {
                    val b = e.getOutputBuffer(eo)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val bytes = ByteArray(info.size); b.position(info.offset); b.get(bytes)
                        samples += Sample(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    }
                    e.releaseOutputBuffer(eo, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encDone = true
                }
            }
        } finally {
            runCatching { dec.stop() }; dec.release(); ex.release()
            enc?.let { runCatching { it.stop() }; it.release() }
        }
        return outFormat?.let { it to samples }
    }

    private fun save(context: Context, file: File, name: String): String {
        val title = name.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "Light show" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "").apply { mkdirs() }
            file.copyTo(File(dir, "$title.mp4"), overwrite = true)
            return dir.absolutePath
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/WLED Controller")
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Couldn't save the video")
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        file.delete()
        return "Movies › WLED Controller"
    }
}
