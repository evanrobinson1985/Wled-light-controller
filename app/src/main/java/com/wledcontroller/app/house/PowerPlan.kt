package com.wledcontroller.app.house

import kotlin.math.ceil

/**
 * Power planning for pixel lights: how much current runs draw, what power supply a controller
 * needs, and where long runs need power injected so the far end doesn't fade to pink.
 * Rules of thumb used by the hobby: full-white draw per pixel by voltage, about a third of that
 * on average during a show, 20% headroom on supplies, and an injection every so many pixels.
 */
object PowerPlan {
    enum class Volts(val v: Int, val maPerPixel: Float, val injectEvery: Int) {
        V5(5, 60f, 100), V12(12, 40f, 150), V24(24, 20f, 300);
        val label get() = "$v V"
    }

    /** Typical share of full white across a show. */
    const val SHOW_SHARE = 0.35f
    /** Common supply sizes, watts. */
    val SUPPLIES = listOf(60, 100, 150, 200, 350, 400, 600)

    data class RunPower(val name: String, val leds: Int, val fullAmps: Float, val showAmps: Float, val injections: Int)

    data class ControllerPower(
        val volts: Volts,
        val runs: List<RunPower>,
        val leds: Int,
        val fullAmps: Float,
        val showAmps: Float,
        /** Watts with headroom, and the supply (or supplies) to cover it. */
        val wattsNeeded: Int,
        val supplies: List<Int>,
    )

    fun amps(leds: Int, volts: Volts, share: Float = 1f) = leds * volts.maPerPixel * share / 1000f

    /** Power injections a run of [leds] needs along it (0 when it's short enough). */
    fun injections(leds: Int, volts: Volts): Int = (ceil(leds / volts.injectEvery.toFloat()).toInt() - 1).coerceAtLeast(0)

    /** Supplies to cover [watts]: the smallest one that does, or several of the biggest. */
    fun supplies(watts: Int): List<Int> {
        SUPPLIES.firstOrNull { it >= watts }?.let { return listOf(it) }
        val big = SUPPLIES.last()
        val n = ceil(watts / big.toFloat()).toInt()
        return List(n) { big }
    }

    fun plan(runs: List<LightRun>, extraLeds: Int, volts: Volts): ControllerPower {
        val rp = runs.map { RunPower(it.name, it.count, amps(it.count, volts), amps(it.count, volts, SHOW_SHARE), injections(it.count, volts)) }
        val leds = runs.sumOf { it.count } + extraLeds.coerceAtLeast(0)
        val full = amps(leds, volts)
        val watts = ceil(full * volts.v * 1.2f).toInt()
        return ControllerPower(volts, rp, leds, full, amps(leds, volts, SHOW_SHARE), watts, if (leds == 0) emptyList() else supplies(watts))
    }
}
