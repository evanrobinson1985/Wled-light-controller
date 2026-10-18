package com.wledcontroller.app.show

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

class FseqReaderTest {

    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        FseqReader.forgetAll()
        files.forEach { it.delete() }
    }

    /** Frame f, channel c holds (f * 16 + c) & 0xFF. */
    private fun frames(frames: Int, channels: Int) = ByteArray(frames * channels) { ((it / channels) * 16 + it % channels).toByte() }

    /**
     * Builds a v2 file: 32-byte header, [blocks] index entries, [sparse] (start, count) ranges,
     * optional variable headers, then [data].
     */
    private fun v2(
        channels: Int,
        frameCount: Int,
        data: ByteArray,
        compression: Int = 0,
        blocks: List<Pair<Int, Int>> = emptyList(),
        sparse: List<Pair<Int, Int>> = emptyList(),
        media: String? = null,
    ): File {
        val varHeaders = ByteArrayOutputStream()
        if (media != null) {
            val bytes = (media + "\u0000").toByteArray()
            val len = bytes.size + 4
            varHeaders.write(len and 0xFF); varHeaders.write(len shr 8)
            varHeaders.write("mf".toByteArray())
            varHeaders.write(bytes)
        }
        val varOffset = 32 + blocks.size * 8 + sparse.size * 6
        val dataOffset = varOffset + varHeaders.size()
        val b = ByteBuffer.allocate(dataOffset + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("PSEQ".toByteArray())
        b.putShort(dataOffset.toShort())
        b.put(0); b.put(2)
        b.putShort(varOffset.toShort())
        b.putInt(channels)
        b.putInt(frameCount)
        b.put(25); b.put(0)
        b.put(compression.toByte()); b.put(blocks.size.toByte()); b.put(sparse.size.toByte()); b.put(0)
        b.putLong(0)
        for ((first, size) in blocks) { b.putInt(first); b.putInt(size) }
        for ((start, count) in sparse) {
            b.put((start and 0xFF).toByte()); b.put((start shr 8 and 0xFF).toByte()); b.put((start shr 16).toByte())
            b.put((count and 0xFF).toByte()); b.put((count shr 8 and 0xFF).toByte()); b.put((count shr 16).toByte())
        }
        b.put(varHeaders.toByteArray())
        b.put(data)
        return File.createTempFile("test", ".fseq").also { it.writeBytes(b.array()); files += it }
    }

    @Test
    fun uncompressed() {
        val r = FseqReader.open(v2(channels = 6, frameCount = 4, data = frames(4, 6)).path)
        assertEquals(6, r.channelCount)
        assertEquals(4, r.frameCount)
        assertEquals(25, r.stepMs)
        assertEquals(100L, r.durationMs)
        assertEquals("uncompressed", r.compressionName)
        assertNull(r.mediaFile)
        val f2 = r.frame(2)
        assertEquals(32, r.channel(f2, 0))
        assertEquals(37, r.channel(f2, 5))
        val dest = ByteArray(3)
        r.copyChannels(f2, 3, 3, dest)
        assertArrayEquals(byteArrayOf(35, 36, 37), dest)
        // Past the end clamps to the last frame.
        assertEquals(48, r.channel(r.frame(99), 0))
    }

    @Test
    fun zlibBlocks() {
        val raw = frames(10, 6)
        fun deflate(bytes: ByteArray): ByteArray {
            val d = Deflater(); d.setInput(bytes); d.finish()
            val out = ByteArrayOutputStream(); val buf = ByteArray(256)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            return out.toByteArray()
        }
        // Two blocks: frames 0–5 and 6–9.
        val a = deflate(raw.copyOfRange(0, 36)); val b = deflate(raw.copyOfRange(36, 60))
        val file = v2(6, 10, a + b, compression = 2, blocks = listOf(0 to a.size, 6 to b.size), media = "music/Carol of the Bells.mp3")
        val r = FseqReader.open(file.path)
        assertEquals("zlib", r.compressionName)
        assertEquals("Carol of the Bells.mp3", r.mediaFile)
        for (f in 0 until 10) assertArrayEquals("frame $f", raw.copyOfRange(f * 6, f * 6 + 6), r.frame(f))
        // Going back to the first block after reading the second.
        assertEquals(16, r.channel(r.frame(1), 0))
    }

    @Test
    fun sparseRanges() {
        // Only absolute channels 6..8 are stored.
        val r = FseqReader.open(v2(channels = 3, frameCount = 2, data = byteArrayOf(1, 2, 3, 4, 5, 6), sparse = listOf(6 to 3)).path)
        assertEquals(9, r.totalChannels)
        val f = r.frame(1)
        assertEquals(0, r.channel(f, 0))
        assertEquals(4, r.channel(f, 6))
        assertEquals(6, r.channel(f, 8))
        val dest = ByteArray(6)
        r.copyChannels(f, 4, 6, dest)
        assertArrayEquals(byteArrayOf(0, 0, 4, 5, 6, 0), dest)
    }
}
