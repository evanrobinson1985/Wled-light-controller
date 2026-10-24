package com.wledcontroller.app.house

/**
 * Puts xLights props on the layout's background photo where they are in xLights. xLights
 * measures its layout from the bottom-left, y up; its background picture either fills the view
 * or (by default) is fitted inside it, at the bottom-left, keeping its shape.
 */
object XlightsPlacement {
    /** Where the photo sits in the layout: its width and height in layout units. */
    fun photoArea(preview: XlPreview, imageW: Int, imageH: Int): Pair<Float, Float> {
        if (preview.scaleImage || imageW <= 0 || imageH <= 0) return preview.width to preview.height
        val scale = maxOf(imageW / preview.width, imageH / preview.height)
        return imageW / scale to imageH / scale
    }

    /** A layout position on the photo (0..1 across and down). */
    fun onPhoto(x: Float, y: Float, area: Pair<Float, Float>) = MapPoint(x / area.first, 1f - y / area.second)

    /**
     * The traced points for [m] on the photo: the two corners of its box for props laid out
     * inside one (matrices, custom props), the outline of a window frame, or its line.
     */
    fun points(m: XlModel, area: Pair<Float, Float>): List<MapPoint>? {
        val p = m.place ?: return null
        fun at(x: Float, y: Float) = onPhoto(x, y, area)
        p.points?.let { pts -> return pts.map { (dx, dy) -> at(p.x + dx * p.scaleX, p.y + dy * p.scaleY) } }
        val halfW = p.gridW * p.scaleX / 2f; val halfH = p.gridH * p.scaleY / 2f
        if (m.model != null) return listOf(at(p.x - halfW, p.y + halfH), at(p.x + halfW, p.y - halfH))
        if (m.prop == PropType.WINDOW) return listOf(
            at(p.x - halfW, p.y - halfH), at(p.x - halfW, p.y + halfH), at(p.x + halfW, p.y + halfH), at(p.x + halfW, p.y - halfH), at(p.x - halfW, p.y - halfH),
        )
        if (p.x2 != null && p.y2 != null) {
            val end = at(p.x + p.x2, p.y + p.y2)
            val start = at(p.x, p.y)
            // A single light: just its spot.
            return if (m.ledCount == 1) listOf(start) else listOf(start, end)
        }
        // A box without a layout of its own: a line across its middle.
        return listOf(at(p.x - halfW, p.y), at(p.x + halfW, p.y))
    }
}
