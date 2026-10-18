package com.wledcontroller.app.show

import com.github.luben.zstd.ZstdInputStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.Inflater

/**
 * Reads FSEQ files (the xLights / Falcon Player sequence format): v1 uncompressed, and v2
 * uncompressed, zlib or zstd, including sparse channel ranges. Frames are decoded a
 * compression block at a time, so long, large shows don't need to fit in memory.
 */
class FseqReader private constructor(val path: String) : Closeable {

    /** Channels stored in each frame. */
    val channelCount: Int
    val frameCount: Int
    val stepMs: Int
    val majorVersion: Int
    /** 0 none, 1 zstd, 2 zlib. */
    val compression: Int
    /** Song file name the sequence was made for, if recorded. */
    val mediaFile: String?
    /** Highest channel number (exclusive, 0-based) the frame data covers. */
    val totalChannels: Int

    val durationMs: Long get() = frameCount.toLong() * stepMs
    val compressionName: String get() = when (compression) { 1 -> "zstd"; 2 -> "zlib"; else -> "uncompressed" }

    private class Block(val firstFrame: Int, val frames: Int, val fileOffset: Long, val size: Long)

    private val raf = RandomAccessFile(path, "r")
    private val dataOffset: Long
    private val blocks: List<Block>
    /** Sparse ranges as (startChannel, count, offsetInFrame); null when the frame holds every channel from 0. */
    private val sparse: List<IntArray>?

    private var cachedBlock = -1
    private var cachedData: ByteArray? = null

    init {
        val head = ByteArray(32)
        raf.seek(0)
        raf.readFully(head, 0, 28)
        val magic = String(head, 0, 4, Charsets.US_ASCII)
        if (magic != "PSEQ" && magic != "FSEQ") throw IOException("Not an FSEQ file")
        fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
        fun u16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
        fun u24(b: ByteArray, i: Int) = u16(b, i) or (u8(b, i + 2) shl 16)
        fun u32(b: ByteArray, i: Int) = u16(b, i).toLong() or (u16(b, i + 2).toLong() shl 16)

        dataOffset = u16(head, 4).toLong()
        majorVersion = u8(head, 7)
        val varHeaderOffset = u16(head, 8)
        channelCount = u32(head, 10).toInt()
        frameCount = u32(head, 14).toInt()
        stepMs = u8(head, 18).coerceAtLeast(1)
        if (channelCount <= 0 || frameCount <= 0) throw IOException("Empty sequence")

        // Everything up to the channel data: block index, sparse ranges, variable headers.
        val header = ByteArray(dataOffset.toInt())
        raf.seek(0)
        raf.readFully(header)

        val blockList = ArrayList<Block>()
        val sparseList = ArrayList<IntArray>()
        var varStart: Int
        if (majorVersion >= 2) {
            compression = u8(header, 20) and 0x0F
            val blockCount = u8(header, 21) or ((u8(header, 20) and 0xF0) shl 4)
            val sparseCount = u8(header, 22)
            var p = 32
            val raw = ArrayList<Pair<Int, Long>>()
            repeat(blockCount) {
                val first = u32(header, p).toInt(); val size = u32(header, p + 4)
                if (size > 0) raw += first to size
                p += 8
            }
            var offsetInFrame = 0
            repeat(sparseCount) {
                val start = u24(header, p); val count = u24(header, p + 3)
                sparseList += intArrayOf(start, count, offsetInFrame)
                offsetInFrame += count
                p += 6
            }
            varStart = if (varHeaderOffset >= p) varHeaderOffset else p
            if (compression != 0 && raw.isNotEmpty()) {
                var fileOff = dataOffset
                raw.forEachIndexed { i, (first, size) ->
                    val next = raw.getOrNull(i + 1)?.first ?: frameCount
                    blockList += Block(first, (next - first).coerceAtLeast(0), fileOff, size)
                    fileOff += size
                }
            }
        } else {
            compression = 0
            varStart = 28
        }
        compressionOrNone(blockList)
        blocks = blockList
        sparse = sparseList.takeIf { it.isNotEmpty() }
        totalChannels = sparse?.maxOf { it[0] + it[1] } ?: channelCount

        var media: String? = null
        var p = varStart
        while (p + 4 <= header.size) {
            val len = u16(header, p)
            if (len < 4 || p + len > header.size) break
            val code = String(header, p + 2, 2, Charsets.US_ASCII)
            if (code == "mf") media = String(header, p + 4, len - 4, Charsets.UTF_8).trimEnd('\u0000').substringAfterLast('/').substringAfterLast('\\')
            p += len
        }
        mediaFile = media?.takeIf { it.isNotBlank() }
    }

    private fun compressionOrNone(blocks: List<Block>) {
        if (compression != 0 && blocks.isEmpty()) throw IOException("Compressed FSEQ without a block index")
        if (compression !in 0..2) throw IOException("Unsupported FSEQ compression ($compression)")
    }

    /** Frame [n]'s channel data (as stored: sparse ranges packed together). */
    @Synchronized
    fun frame(n: Int): ByteArray {
        val idx = n.coerceIn(0, frameCount - 1)
        val out = ByteArray(channelCount)
        if (compression == 0) {
            raf.seek(dataOffset + idx.toLong() * channelCount)
            raf.readFully(out)
            return out
        }
        val b = blocks.indexOfLast { it.firstFrame <= idx }.coerceAtLeast(0)
        if (b != cachedBlock) {
            cachedData = decompress(blocks[b])
            cachedBlock = b
        }
        val data = cachedData!!
        val off = (idx - blocks[b].firstFrame).toLong() * channelCount
        if (off + channelCount <= data.size) System.arraycopy(data, off.toInt(), out, 0, channelCount)
        return out
    }

    private fun decompress(block: Block): ByteArray {
        val packed = ByteArray(block.size.toInt())
        raf.seek(block.fileOffset)
        raf.readFully(packed)
        val expected = block.frames * channelCount
        return when (compression) {
            1 -> ZstdInputStream(ByteArrayInputStream(packed)).use { readUpTo(it, expected) }
            else -> {
                val inf = Inflater()
                try {
                    inf.setInput(packed)
                    val out = ByteArray(expected)
                    var n = 0
                    while (n < expected && !inf.finished()) {
                        val r = inf.inflate(out, n, expected - n)
                        if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break
                        n += r
                    }
                    out
                } finally {
                    inf.end()
                }
            }
        }
    }

    private fun readUpTo(input: InputStream, size: Int): ByteArray {
        val out = ByteArray(size)
        var n = 0
        while (n < size) {
            val r = input.read(out, n, size - n)
            if (r < 0) break
            n += r
        }
        return out
    }

    /** Copies [count] channels starting at absolute channel [start] (0-based) into [dest]. */
    fun copyChannels(frame: ByteArray, start: Int, count: Int, dest: ByteArray, destOff: Int = 0) {
        val ranges = sparse
        if (ranges == null) {
            val n = minOf(count, frame.size - start).coerceAtLeast(0)
            if (start >= 0 && n > 0) System.arraycopy(frame, start, dest, destOff, n)
            return
        }
        for (r in ranges) {
            val lo = maxOf(start, r[0]); val hi = minOf(start + count, r[0] + r[1])
            if (lo < hi) System.arraycopy(frame, r[2] + (lo - r[0]), dest, destOff + (lo - start), hi - lo)
        }
    }

    /** One channel's value (0-based absolute channel), 0 if not stored. */
    fun channel(frame: ByteArray, ch: Int): Int {
        val ranges = sparse ?: return if (ch in frame.indices) frame[ch].toInt() and 0xFF else 0
        for (r in ranges) if (ch >= r[0] && ch < r[0] + r[1]) return frame[r[2] + ch - r[0]].toInt() and 0xFF
        return 0
    }

    override fun close() = raf.close()

    companion object {
        private val open = HashMap<String, FseqReader>()

        /** Shared reader per file (the player and the editor preview read the same show). */
        @Synchronized
        fun open(path: String): FseqReader = open.getOrPut(path) { FseqReader(path) }

        @Synchronized
        fun forget(path: String) { open.remove(path)?.close() }

        @Synchronized
        fun forgetAll() { open.values.forEach { runCatching { it.close() } }; open.clear() }
    }
}
