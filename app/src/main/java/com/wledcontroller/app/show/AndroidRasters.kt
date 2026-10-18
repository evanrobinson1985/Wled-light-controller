package com.wledcontroller.app.show

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil

/** Draws text and loads pictures for the Text and Picture effects. */
object AndroidRasters {
    fun install() {
        Rasters.textMaker = ::text
        Rasters.imageLoader = ::image
    }

    private fun text(s: String): Raster? = runCatching {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
        val fm = paint.fontMetrics
        val w = ceil(paint.measureText(s)).toInt().coerceIn(1, 4000)
        val h = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(s, 0f, -fm.ascent, paint)
        pixels(bmp)
    }.getOrNull()

    private fun image(path: String): Raster? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 160) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        pixels(bmp)
    }.getOrNull()

    private fun pixels(bmp: Bitmap): Raster {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val r = Raster(bmp.width, bmp.height, px)
        bmp.recycle()
        return r
    }
}
