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
