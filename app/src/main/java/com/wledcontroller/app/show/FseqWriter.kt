package com.wledcontroller.app.show

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/**
 * Writes FSEQ v2 files (what xLights renders and Falcon Player plays): zlib-compressed blocks of
 * frames, with the song's file name and the app as variable headers.
 */
object FseqWriter {

    /**
     * Writes [frameCount] frames of [channels] channels, [stepMs] apart. [frame] fills frame n's
     * channel bytes (the buffer starts zeroed).
     */
    fun write(
        out: OutputStream,
        channels: Int,
        frameCount: Int,
        stepMs: Int,
        mediaFile: String? = null,
        frame: (n: Int, buf: ByteArray) -> Unit,
    ) {
        require(channels > 0 && frameCount > 0 && stepMs in 1..255)
        // Up to 255 blocks so the block count fits its byte; at least 10 frames per block.
        val perBlock = maxOf(10, (frameCount + 254) / 255)
        val blocks = ArrayList<Pair<Int, ByteArray>>()
        val buf = ByteArray(channels)
        val deflater = Deflater(6)
        try {
            var first = 0
            while (first < frameCount) {
                val n = minOf(perBlock, frameCount - first)
                val raw = ByteArrayOutputStream(n * channels)
                for (f in first until first + n) {
                    buf.fill(0)
                    frame(f, buf)
                    raw.write(buf)
                }
                deflater.reset()
                deflater.setInput(raw.toByteArray())
                deflater.finish()
                val packed = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (!deflater.finished()) packed.write(chunk, 0, deflater.deflate(chunk))
                blocks += first to packed.toByteArray()
                first += n
            }
        } finally {
            deflater.end()
        }

        val vars = ByteArrayOutputStream()
        fun varHeader(code: String, value: String) {
            val bytes = (value + "\u0000").toByteArray()
            val len = bytes.size + 4
            vars.write(len and 0xFF); vars.write(len shr 8)
            vars.write(code.toByteArray()); vars.write(bytes)
        }
        mediaFile?.takeIf { it.isNotBlank() }?.let { varHeader("mf", it) }
        varHeader("sp", "WLED Controller")

        val varOffset = 32 + blocks.size * 8
        val dataOffset = varOffset + vars.size()
        val head = ByteBuffer.allocate(dataOffset).order(ByteOrder.LITTLE_ENDIAN)
        head.put("PSEQ".toByteArray())
        head.putShort(dataOffset.toShort())
        head.put(0); head.put(2)                      // version 2.0
        head.putShort(varOffset.toShort())
        head.putInt(channels)
        head.putInt(frameCount)
        head.put(stepMs.toByte())
        head.put(0)                                   // flags
        head.put(((blocks.size shr 8) shl 4 or 2).toByte()) // zlib, high bits of the block count
        head.put((blocks.size and 0xFF).toByte())
        head.put(0)                                   // no sparse ranges
        head.put(0)
        head.putLong(System.currentTimeMillis() * 1000)
        for ((firstFrame, data) in blocks) { head.putInt(firstFrame); head.putInt(data.size) }
        head.put(vars.toByteArray())
        out.write(head.array())
        for ((_, data) in blocks) out.write(data)
        out.flush()
    }
}
