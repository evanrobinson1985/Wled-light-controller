package com.wledcontroller.app.show

/** A small picture for effects to sample: [argb] row by row. */
class Raster(val width: Int, val height: Int, val argb: IntArray) {
    /** Pixel at (u, v) in 0..1 (v down), or 0 outside. */
    fun sample(u: Float, v: Float): Int {
        if (u < 0f || v < 0f || u >= 1f || v >= 1f) return 0
        return argb[(v * height).toInt().coerceIn(0, height - 1) * width + (u * width).toInt().coerceIn(0, width - 1)]
    }
}

/**
 * Text and pictures as rasters for the Text and Picture effects. The app plugs in Android
 * drawing at start-up; without it (e.g. in tests) those effects stay dark.
 */
object Rasters {
    @Volatile var textMaker: ((String) -> Raster?)? = null
    @Volatile var imageLoader: ((String) -> Raster?)? = null

    private val cache = object : LinkedHashMap<String, Raster>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Raster>?) = size > 24
    }

    @Synchronized
    fun text(s: String): Raster? = if (s.isBlank()) null else cache["t:$s"] ?: textMaker?.invoke(s)?.also { cache["t:$s"] = it }

    @Synchronized
    fun image(path: String): Raster? = cache["i:$path"] ?: imageLoader?.invoke(path)?.also { cache["i:$path"] = it }

    private val videos = object : LinkedHashMap<String, VideoFrames>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoFrames>?) = size > 3
    }

    /** A video's frames for the Video effect (read once, then kept while it's in use). */
    @Synchronized
    fun video(path: String): VideoFrames? = videos[path] ?: VideoFrames.read(java.io.File(path))?.also { videos[path] = it }
}
