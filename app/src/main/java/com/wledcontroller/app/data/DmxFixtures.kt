package com.wledcontroller.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** What one DMX channel of a fixture does. */
@Serializable
enum class DmxRole(val label: String) {
    NONE("Unused (0)"),
    DIMMER("Dimmer"),
    RED("Red"),
    GREEN("Green"),
    BLUE("Blue"),
    WHITE("White"),
    AMBER("Amber"),
    UV("UV"),
    PAN("Pan"),
    PAN_FINE("Pan fine"),
    TILT("Tilt"),
    TILT_FINE("Tilt fine"),
    SHUTTER("Shutter open"),
    FIXED("Fixed value"),
}

/** A channel: its role, and the value sent for [DmxRole.SHUTTER] / [DmxRole.FIXED]. */
@Serializable
data class DmxChannel(val role: DmxRole, val value: Int = 0, val name: String = "")

/**
 * A DMX light — par, wash, flood, strobe, moving head — on an E1.31 / Art-Net output, starting
 * at [address] (1–512) in [universe]. On the house map it's a light run with one light.
 */
@Serializable
data class DmxFixture(
    val id: String = "dmx-" + UUID.randomUUID().toString().take(8),
    val name: String,
    val outputId: String,
    val universe: Int = 1,
    val address: Int = 1,
    val channels: List<DmxChannel>,
    val type: String = "Flood light",
) {
    val movingHead get() = channels.any { it.role == DmxRole.PAN || it.role == DmxRole.TILT }

    /**
     * Channel values for a colour (0xRRGGBB) and an aim ([pan], [tilt] 0..1, 0.5 = straight ahead).
     * With a dimmer channel the colour goes out at full strength and the dimmer sets brightness.
     */
    fun values(rgb: Int, pan: Float = 0.5f, tilt: Float = 0.5f): ByteArray {
        var r = (rgb shr 16) and 0xFF; var g = (rgb shr 8) and 0xFF; var b = rgb and 0xFF
        val level = maxOf(r, g, b)
        val hasDimmer = channels.any { it.role == DmxRole.DIMMER }
        if (hasDimmer && level > 0) { r = r * 255 / level; g = g * 255 / level; b = b * 255 / level }
        val white = minOf(r, g, b)
        val p = (pan.coerceIn(0f, 1f) * 65535).toInt(); val t = (tilt.coerceIn(0f, 1f) * 65535).toInt()
        return ByteArray(channels.size) { i ->
            val c = channels[i]
            when (c.role) {
                DmxRole.NONE -> 0
                DmxRole.DIMMER -> level
                DmxRole.RED -> r
                DmxRole.GREEN -> g
                DmxRole.BLUE -> b
                DmxRole.WHITE -> white
                DmxRole.AMBER, DmxRole.UV -> 0
                DmxRole.PAN -> p shr 8
                DmxRole.PAN_FINE -> p and 0xFF
                DmxRole.TILT -> t shr 8
                DmxRole.TILT_FINE -> t and 0xFF
                DmxRole.SHUTTER, DmxRole.FIXED -> c.value.coerceIn(0, 255)
            }.toByte()
        }
    }

    companion object {
        private fun ch(vararg roles: DmxRole) = roles.map { DmxChannel(it, if (it == DmxRole.SHUTTER) 255 else 0) }

        /** Common channel layouts to start from. */
        val PRESETS: List<Pair<String, List<DmxChannel>>> = listOf(
            "RGB par (3 ch)" to ch(DmxRole.RED, DmxRole.GREEN, DmxRole.BLUE),
            "RGBW par (4 ch)" to ch(DmxRole.RED, DmxRole.GREEN, DmxRole.BLUE, DmxRole.WHITE),
            "Dimmer + RGB (4 ch)" to ch(DmxRole.DIMMER, DmxRole.RED, DmxRole.GREEN, DmxRole.BLUE),
            "RGB + dimmer + strobe (5 ch)" to ch(DmxRole.RED, DmxRole.GREEN, DmxRole.BLUE, DmxRole.DIMMER, DmxRole.SHUTTER),
            "Dimmer + RGBW + strobe (6 ch)" to ch(DmxRole.DIMMER, DmxRole.RED, DmxRole.GREEN, DmxRole.BLUE, DmxRole.WHITE, DmxRole.SHUTTER),
            "Single dimmer (1 ch)" to ch(DmxRole.DIMMER),
            "Moving head, basic (9 ch)" to ch(
                DmxRole.PAN, DmxRole.PAN_FINE, DmxRole.TILT, DmxRole.TILT_FINE, DmxRole.NONE,
                DmxRole.DIMMER, DmxRole.RED, DmxRole.GREEN, DmxRole.BLUE,
            ),
            "Moving head + shutter (11 ch)" to ch(
                DmxRole.PAN, DmxRole.PAN_FINE, DmxRole.TILT, DmxRole.TILT_FINE, DmxRole.NONE, DmxRole.DIMMER,
                DmxRole.SHUTTER, DmxRole.RED, DmxRole.GREEN, DmxRole.BLUE, DmxRole.WHITE,
            ),
        )
    }
}

/**
 * Keeps every DMX universe's current values and sends a whole universe whenever one of its
 * fixtures changes, so fixtures sharing a universe don't wipe each other out.
 */
object DmxRegistry {
    @Volatile private var fixtures: Map<String, DmxFixture> = emptyMap()
    @Volatile private var outputs: Map<String, PixelOutput> = emptyMap()
    private val universes = HashMap<String, ByteArray>()

    fun update(fixtures: List<DmxFixture>, outputs: List<PixelOutput>) {
        this.fixtures = fixtures.associateBy { it.id }
        this.outputs = outputs.associateBy { it.id }
    }

    fun isFixture(id: String) = fixtures.containsKey(id)

    /** Address the senders use for a fixture. */
    fun address(id: String) = "dmx://$id"

    /** A universe to send: where, how, which and its 512 channels. */
    class Universe(val host: String, val protocol: OutputProtocol, val number: Int, val data: ByteArray)

    /**
     * Sets a fixture from [frame]: 3 bytes RGB, then optionally pan and tilt as 16-bit values.
     * Returns its universe, ready to send, or null if the fixture or its output is unknown.
     */
    @Synchronized
    fun stage(fixtureId: String, frame: ByteArray): Universe? {
        val f = fixtures[fixtureId] ?: return null
        val o = outputs[f.outputId] ?: return null
        val rgb = if (frame.size >= 3) ((frame[0].toInt() and 0xFF) shl 16) or ((frame[1].toInt() and 0xFF) shl 8) or (frame[2].toInt() and 0xFF) else 0
        fun u16(i: Int) = if (frame.size >= i + 2) (((frame[i].toInt() and 0xFF) shl 8) or (frame[i + 1].toInt() and 0xFF)) / 65535f else 0.5f
        val values = f.values(rgb, u16(3), u16(5))
        val key = "${o.id}/${f.universe}"
        val data = universes.getOrPut(key) { ByteArray(512) }
        val start = (f.address - 1).coerceIn(0, 511)
        System.arraycopy(values, 0, data, start, minOf(values.size, 512 - start))
        return Universe(o.host, o.protocol, f.universe, data.copyOf())
    }

    /** RGB plus pan and tilt (0..1) as the frame [stage] reads. */
    fun frame(rgb: Int, pan: Float, tilt: Float): ByteArray {
        val p = (pan.coerceIn(0f, 1f) * 65535).toInt(); val t = (tilt.coerceIn(0f, 1f) * 65535).toInt()
        return byteArrayOf(
            (rgb shr 16).toByte(), (rgb shr 8).toByte(), rgb.toByte(),
            (p shr 8).toByte(), p.toByte(), (t shr 8).toByte(), t.toByte(),
        )
    }
}
