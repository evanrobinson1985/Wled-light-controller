package com.wledcontroller.app.house

import com.wledcontroller.app.data.WledApi
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends raw LED frames with the DDP protocol (UDP port 4048), which WLED accepts as realtime
 * data on any build. A device falls back to its own effects ~2.5 s after frames stop.
 */
class DdpSender : AutoCloseable {
    private val socket = DatagramSocket()
    private val addresses = HashMap<String, InetAddress>()
    private var sequence = 1

    /** [rgb] holds 3 bytes per LED starting at LED 0. Blocking: call off the main thread. */
    fun send(host: String, rgb: ByteArray) {
        if (com.wledcontroller.app.sim.SimHub.deliverDdp(host, rgb)) return
        // Non-WLED controllers come as "e131://", "artnet://" or "ddp://" addresses.
        if (host.contains("://")) { sendOther(host, rgb); return }
        val addr = addresses.getOrPut(host) {
            InetAddress.getByName(WledApi.normalizeHost(host).substringBefore(':'))
        }
        val seq = sequence
        sequence = if (sequence >= 15) 1 else sequence + 1
        var offset = 0
        while (offset < rgb.size) {
            val len = minOf(MAX_DATA, rgb.size - offset)
            val last = offset + len >= rgb.size
            val packet = ByteArray(HEADER + len)
            packet[0] = (if (last) VERSION_1 or PUSH else VERSION_1).toByte()
            packet[1] = seq.toByte()
            packet[2] = TYPE_RGB24
            packet[3] = DEST_DISPLAY
            packet[4] = (offset ushr 24).toByte()
            packet[5] = (offset ushr 16).toByte()
            packet[6] = (offset ushr 8).toByte()
            packet[7] = offset.toByte()
            packet[8] = (len ushr 8).toByte()
            packet[9] = len.toByte()
            System.arraycopy(rgb, offset, packet, HEADER, len)
            socket.send(DatagramPacket(packet, packet.size, addr, PORT))
            offset += len
        }
    }

    private val universeSeq = HashMap<String, Int>()

    private fun sendOther(address: String, rgb: ByteArray) {
        val scheme = address.substringBefore("://")
        val rest = address.substringAfter("://")
        val host = rest.substringBefore('?')
        if (scheme == "ddp") { send(host, rgb); return }
        val params = rest.substringAfter('?', "").split('&').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val start = params["u"]?.toIntOrNull() ?: 1
        val per = params["c"]?.toIntOrNull() ?: 510
        val addr = addresses.getOrPut(host) { InetAddress.getByName(host) }
        val seq = (universeSeq[address] ?: 0).let { (it + 1) and 0xFF }.also { universeSeq[address] = it }
        for ((universe, data) in com.wledcontroller.app.data.PixelPackets.split(rgb, start, per)) {
            val (packet, port) = when (scheme) {
                "artnet" -> com.wledcontroller.app.data.PixelPackets.artnet(universe, seq, data) to com.wledcontroller.app.data.PixelPackets.ARTNET_PORT
                else -> com.wledcontroller.app.data.PixelPackets.e131(universe, seq, data) to com.wledcontroller.app.data.PixelPackets.E131_PORT
            }
            socket.send(DatagramPacket(packet, packet.size, addr, port))
        }
    }

    override fun close() = socket.close()

    private companion object {
        const val PORT = 4048
        const val HEADER = 10
        const val MAX_DATA = 1440 // 480 RGB LEDs per packet
        const val VERSION_1 = 0x40
        const val PUSH = 0x01
        const val TYPE_RGB24: Byte = 0x0B
        const val DEST_DISPLAY: Byte = 1
    }
}
