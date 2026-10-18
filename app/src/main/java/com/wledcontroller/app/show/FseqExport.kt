package com.wledcontroller.app.show

import com.wledcontroller.app.data.SavedDevice
import java.io.OutputStream

/** Where a controller's LEDs sit in an exported sequence (channels are 1-based, as xLights shows them). */
data class ChannelRange(val deviceId: String, val name: String, val startChannel: Int, val leds: Int) {
    val endChannel get() = startChannel + leds * 3 - 1
}

/** Renders a show to an .fseq so Falcon Player, xLights or another app can play it. */
object FseqExport {
    const val STEP_MS = 25

    /** Each controller with lights on the map gets a block of channels, in the app's controller order. */
    fun channelMap(layout: PreparedLayout, devices: List<SavedDevice>): List<ChannelRange> {
        var next = 1
        return devices.mapNotNull { d ->
            val leds = (layout.leds.filter { it.deviceId == d.id }.maxOfOrNull { it.index } ?: return@mapNotNull null) + 1
            ChannelRange(d.id, d.name, next, leds).also { next += leds * 3 }
        }
    }

    fun export(
        seq: Sequence,
        layout: PreparedLayout,
        devices: List<SavedDevice>,
        audio: AudioEnvelope?,
        out: OutputStream,
    ): List<ChannelRange> {
        val ranges = channelMap(layout, devices)
        require(ranges.isNotEmpty()) { "None of this show's lights are on the house map." }
        val channels = ranges.last().endChannel
        val startOf = ranges.associate { it.deviceId to it.startChannel - 1 }
        val frames = ((seq.durationMs + STEP_MS - 1) / STEP_MS).toInt().coerceAtLeast(1)
        val colors = IntArray(layout.leds.size)
        val media = seq.audioTitle ?: seq.audioFile?.substringAfterLast('/')
        FseqWriter.write(out, channels, frames, STEP_MS, media) { n, buf ->
            SequenceRenderer.render(seq, layout, n.toLong() * STEP_MS, colors, audio)
            layout.leds.forEachIndexed { i, l ->
                val o = (startOf[l.deviceId] ?: return@forEachIndexed) + l.index * 3
                val c = colors[i]
                buf[o] = (c shr 16).toByte(); buf[o + 1] = (c shr 8).toByte(); buf[o + 2] = c.toByte()
            }
        }
        return ranges
    }
}
