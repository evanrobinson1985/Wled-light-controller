package com.wledcontroller.app.show

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream

/**
 * A video shrunk to a few frames a second at lights resolution, for the Video effect: every
 * frame's RGB bytes, one after another, after a small header ("WLVF", width, height, frames
 * per second, frame count).
 */
class VideoFrames(val width: Int, val height: Int, val fps: Float, val count: Int, private val rgb: ByteArray) {

    private var cachedIndex = -1
    private var cached: Raster? = null

    /** Frame [index] (wrapping round) as a raster. */
    @Synchronized
    fun frame(index: Int): Raster {
        val i = index.mod(count.coerceAtLeast(1))
        if (i == cachedIndex) cached?.let { return it }
        val n = width * height
        val base = i * n * 3
        val argb = IntArray(n) { p ->
            val o = base + p * 3
            if (o + 2 >= rgb.size) 0
            else (0xFF shl 24) or ((rgb[o].toInt() and 0xFF) shl 16) or ((rgb[o + 1].toInt() and 0xFF) shl 8) or (rgb[o + 2].toInt() and 0xFF)
        }
        return Raster(width, height, argb).also { cached = it; cachedIndex = i }
    }

    /** The frame showing [seconds] into the video, looping. */
    fun at(seconds: Float): Raster = frame((seconds * fps).toInt())

    val durationSec get() = count / fps

    companion object {
        private const val MAGIC = 0x574C5646 // "WLVF"

        fun read(file: File): VideoFrames? = runCatching {
            DataInputStream(file.inputStream().buffered()).use { inp ->
                require(inp.readInt() == MAGIC)
                val w = inp.readInt(); val h = inp.readInt(); val fps = inp.readFloat(); val n = inp.readInt()
                require(w in 1..512 && h in 1..512 && n in 1..20_000)
                val data = ByteArray(w * h * 3 * n)
                inp.readFully(data)
                VideoFrames(w, h, fps, n, data)
            }
        }.getOrNull()

        /** Writes the header for [count] frames of [width] × [height]; the frames' RGB bytes follow. */
        fun writeHeader(out: OutputStream, width: Int, height: Int, fps: Float, count: Int) {
            val d = DataOutputStream(out)
            d.writeInt(MAGIC); d.writeInt(width); d.writeInt(height); d.writeFloat(fps); d.writeInt(count)
            d.flush()
        }

        /**
         * A Glediator recording (raw RGB frames of a [width] × [height] matrix, row by row) as
         * frames for the lights, written to [out]. Returns how many frames it held.
         */
        fun fromGlediator(raw: ByteArray, width: Int, height: Int, fps: Float, out: OutputStream): Int {
            require(width in 1..512 && height in 1..512) { "Width and height must be 1–512." }
            val frameBytes = width * height * 3
            val count = raw.size / frameBytes
            require(count >= 1) { "That file is smaller than one ${width}×$height frame — check the size." }
            writeHeader(out, width, height, fps, count)
            out.write(raw, 0, count * frameBytes)
            out.flush()
            return count
        }

        /** The frame size for a video [w] × [h] with its longer side at most [maxSide]. */
        fun fit(w: Int, h: Int, maxSide: Int = 96): Pair<Int, Int> {
            val s = maxSide.toFloat() / maxOf(w, h).coerceAtLeast(1)
            return if (s >= 1f) w to h else (w * s).toInt().coerceAtLeast(1) to (h * s).toInt().coerceAtLeast(1)
        }
    }
}
