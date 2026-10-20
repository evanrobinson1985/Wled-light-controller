package com.wledcontroller.app.sim

import com.wledcontroller.app.data.WledState

/**
 * What a controller's LEDs look like for a given state, worked out in the app (no data from the
 * controller): each segment's effect, colours, palette, speed and settings, drawn with the same
 * WLED look-alike effects as the simulator. Effects and palettes are matched by name, since a
 * controller's numbering can differ from the simulator's.
 */
object StatePreview {

    /** One colour (0xRRGGBB) per LED of a [ledCount]-LED strip at time [now] (ms). */
    fun render(state: WledState, effectNames: List<String>, paletteNames: List<String>, ledCount: Int, now: Long): IntArray {
        val out = IntArray(ledCount.coerceAtLeast(0))
        if (!state.on || out.isEmpty()) return out
        for (s in state.segments) {
            if (!s.on) continue
            val start = s.start.coerceIn(0, out.size)
            val stop = s.stop.coerceIn(start, out.size)
            val len = stop - start
            if (len <= 0) continue
            val grp = s.grp.coerceAtLeast(1); val spc = s.spc.coerceAtLeast(0)
            val step = grp + spc
            val full = (len + step - 1) / step
            val vlen = if (s.mi) (full + 1) / 2 else full
            val colors = IntArray(3) { i ->
                val c = s.colors.getOrNull(i) ?: return@IntArray 0
                val rgb = c.rgb and 0xFFFFFF
                // A white channel shows as the colour washed towards white.
                if (c.w > 0) SimEffects.maxRgb(SimEffects.blend(rgb, 0xFFFFFF, c.w / 255f), rgb) else rgb
            }
            val params = SegParams(
                len = vlen,
                fx = indexByName(SimCatalog.effects, effectNames.getOrNull(s.fx)) ?: s.fx.takeIf { effectNames.isEmpty() && it in SimCatalog.effects.indices } ?: 0,
                sx = s.sx, ix = s.ix, c1 = s.c1, c2 = s.c2, c3 = s.c3,
                colors = colors,
                pal = indexByName(SimCatalog.palettes.map { it.first }, paletteNames.getOrNull(s.pal))
                    ?: s.pal.takeIf { paletteNames.isEmpty() && it in SimCatalog.palettes.indices } ?: 0,
                seed = s.id * 7919 + 13,
            )
            val buf = IntArray(vlen)
            SimEffects.render(params, now, buf)
            val segBri = s.bri / 255f
            fun put(v: Int, c: Int) {
                for (g in 0 until grp) {
                    var idx = v * step + g
                    if (idx >= len) break
                    idx = (idx + s.of) % len
                    if (s.rev) idx = len - 1 - idx
                    out[start + idx] = c
                }
            }
            for (v in 0 until vlen) {
                val c = if (segBri < 1f) SimEffects.scale(buf[v], segBri) else buf[v]
                put(v, c)
                if (s.mi) put(full - 1 - v, c)
            }
        }
        val bri = state.bri / 255f
        if (bri < 1f) for (i in out.indices) out[i] = SimEffects.scale(out[i], bri)
        return out
    }

    private fun indexByName(names: List<String>, name: String?): Int? {
        if (name.isNullOrBlank()) return null
        return names.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
    }
}
