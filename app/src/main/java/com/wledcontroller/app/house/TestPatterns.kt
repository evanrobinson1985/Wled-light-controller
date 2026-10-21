package com.wledcontroller.app.house

/**
 * Test patterns for checking wiring and every LED, like xLights' test mode: each one is a
 * colour per LED for a frame number (frames run at [FPS]).
 */
enum class TestPattern(val label: String, val help: String) {
    CHASE("Chase", "One lit LED in every ten, stepping along: shows the direction and any dead LEDs."),
    ALTERNATE("Alternate", "Odd and even LEDs swap: shows LEDs out of order or two lit as one."),
    COLORS("Red, green, blue, white", "The whole string in each colour in turn: shows colour-order and power problems."),
    WHITE("All white", "Every LED white at the brightness below: shows voltage drop along the run."),
    COUNT("Count", "Every 10th LED red, 50th green, 100th blue: count along to find an LED number."),
    TWINKLE("Twinkle", "Random LEDs flashing: shows flicker and data problems."),
    ;

    /** Colour (0xRRGGBB) of LED [i] at [frame]. */
    fun color(i: Int, frame: Int): Int = when (this) {
        CHASE -> if ((i - frame / 3).mod(10) == 0) 0xFFFFFF else 0
        ALTERNATE -> if ((i + frame / 20) % 2 == 0) 0xFFFFFF else 0
        COLORS -> intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF)[(frame / 40) % 4]
        WHITE -> 0xFFFFFF
        COUNT -> when {
            i % 100 == 99 -> 0x0000FF
            i % 50 == 49 -> 0x00FF00
            i % 10 == 9 -> 0xFF0000
            else -> 0x101010
        }
        TWINKLE -> if (HouseRenderer.hash(i, frame / 2) > 0.9f) 0xFFFFFF else 0
    }

    /** A whole frame for [count] LEDs as RGB bytes, at [brightness] 0..1. */
    fun frame(count: Int, frame: Int, brightness: Float): ByteArray {
        val out = ByteArray(count * 3)
        for (i in 0 until count) {
            val c = HouseRenderer.scale(color(i, frame), brightness)
            out[i * 3] = (c shr 16).toByte(); out[i * 3 + 1] = (c shr 8).toByte(); out[i * 3 + 2] = c.toByte()
        }
        return out
    }

    companion object { const val FPS = 20 }
}
