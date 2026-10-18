package com.wledcontroller.app.data

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.util.UUID

@Serializable
enum class OutputProtocol(val label: String, val scheme: String) {
    E131("E1.31 (sACN)", "e131"),
    ARTNET("Art-Net", "artnet"),
    DDP("DDP", "ddp"),
}

/**
 * A pixel controller that isn't WLED — Falcon, Kulp, ESPixelStick, Falcon Player … — which
 * the app streams shows and whole-house effects to. It has no settings or effects of its own
 * in the app; it only receives lights.
 */
@Serializable
data class PixelOutput(
    val id: String = "out-" + UUID.randomUUID().toString().take(8),
    val name: String,
    val host: String,
    val protocol: OutputProtocol = OutputProtocol.E131,
    val startUniverse: Int = 1,
    /** Channels used in each universe (510 = 170 RGB pixels, the usual setting). */
    val channelsPerUniverse: Int = 510,
    val ledCount: Int = 100,
) {
    /** What the senders are given in place of a WLED controller's host. */
    val address get() = "${protocol.scheme}://${host.trim()}?u=$startUniverse&c=$channelsPerUniverse"
    val universes get() = (ledCount * 3 + channelsPerUniverse - 1) / channelsPerUniverse.coerceAtLeast(3)
}

/** Builds E1.31 (sACN) and Art-Net packets. */
object PixelPackets {
    const val E131_PORT = 5568
    const val ARTNET_PORT = 6454

    private val cid = UUID.randomUUID().let { u ->
        ByteBuffer.allocate(16).putLong(u.mostSignificantBits).putLong(u.leastSignificantBits).array()
    }

    /** An E1.31 data packet for [universe] carrying [data] (up to 512 DMX channels). */
    fun e131(universe: Int, sequence: Int, data: ByteArray, sourceName: String = "WLED Controller"): ByteArray {
        val n = data.size.coerceAtMost(512)
        val b = ByteBuffer.allocate(126 + n) // big-endian
        // Root layer.
        b.putShort(0x0010); b.putShort(0)
        b.put("ASC-E1.17".toByteArray()); b.put(ByteArray(3))
        b.putShort((0x7000 or (110 + n)).toShort())
        b.putInt(0x00000004)
        b.put(cid)
        // Framing layer.
        b.putShort((0x7000 or (88 + n)).toShort())
        b.putInt(0x00000002)
        b.put(sourceName.toByteArray().copyOf(64))
        b.put(100.toByte())             // priority
        b.putShort(0)                   // sync address
        b.put(sequence.toByte())
        b.put(0)                        // options
        b.putShort(universe.toShort())
        // DMP layer.
        b.putShort((0x7000 or (11 + n)).toShort())
        b.put(0x02); b.put(0xA1.toByte())
        b.putShort(0); b.putShort(1)
        b.putShort((n + 1).toShort())
        b.put(0)                        // DMX start code
        b.put(data, 0, n)
        return b.array()
    }

    /** An Art-Net ArtDmx packet for [universe] (15-bit port address) carrying [data]. */
    fun artnet(universe: Int, sequence: Int, data: ByteArray): ByteArray {
        // Art-Net wants an even length of 2..512 channels.
        val len = ((data.size.coerceAtMost(512) + 1) / 2 * 2).coerceIn(2, 512)
        val b = ByteBuffer.allocate(18 + len)
        b.put("Art-Net".toByteArray()); b.put(0)
        b.put(0x00); b.put(0x50)        // OpDmx, little-endian
        b.put(0); b.put(14)             // protocol version 14
        b.put(sequence.toByte())
        b.put(0)                        // physical
        b.put((universe and 0xFF).toByte())        // SubUni
        b.put(((universe shr 8) and 0x7F).toByte()) // Net
        b.putShort(len.toShort())
        b.put(data, 0, minOf(len, data.size))
        return b.array()
    }

    /**
     * Splits RGB [data] across universes as the controller expects: [perUniverse] channels in
     * each, starting at [startUniverse]. Returns (universe, channels) pairs.
     */
    fun split(data: ByteArray, startUniverse: Int, perUniverse: Int): List<Pair<Int, ByteArray>> {
        val per = perUniverse.coerceIn(3, 512)
        val out = ArrayList<Pair<Int, ByteArray>>()
        var off = 0; var u = startUniverse
        while (off < data.size) {
            val n = minOf(per, data.size - off)
            out += u to data.copyOfRange(off, off + n)
            off += n; u++
        }
        return out
    }
}
