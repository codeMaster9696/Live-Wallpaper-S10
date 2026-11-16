package com.hub.livewallpaper.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.hub.livewallpaper.data.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Miniaturas da galeria.
 *
 * · VÍDEO      -> MediaMetadataRetriever.getFrameAtTime() + CENTER_CROP
 *                (o mesmo enquadramento do wallpaper, em 9:16)
 * · PROCEDURAL -> composição Canvas com a paleta do shader (barato e
 *                instantâneo, não precisa de GPU nem de arquivo)
 *
 * Cache em memória (LruCache) para o scroll da grade não redecodificar.
 */
object Thumbnails {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(context: Context, wp: Wallpaper, w: Int, h: Int): Bitmap =
        withContext(Dispatchers.IO) {
            val key = "${wp.id}_${w}x$h"
            cache.get(key)?.let { return@withContext it }
            val bmp = if (wp.isVideo) videoFrame(context, wp, w, h) else procedural(wp, w, h)
            cache.put(key, bmp)
            bmp
        }

    private fun videoFrame(context: Context, wp: Wallpaper, w: Int, h: Int): Bitmap {
        val mmr = MediaMetadataRetriever()
        return try {
            context.assets.openFd(wp.asset!!).use { afd ->
                mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            val frame = mmr.getFrameAtTime(1_200_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame == null) procedural(wp, w, h) else centerCrop(frame, w, h)
        } catch (t: Throwable) {
            procedural(wp, w, h)
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** CENTER_CROP em Bitmap: escala por max() e corta o excesso centralizado. */
    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, null, android.graphics.RectF(left, top, left + dw, top + dh), paint)
        if (src != out) src.recycle()
        return out
    }

    private fun procedural(wp: Wallpaper, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(max(2, w), max(2, h), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val p = wp.palette
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        canvas.drawColor(p[0])

        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(p[0], p[1], p[0]), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        for (i in 1 until p.size) {
            val cx = w * (0.5f + 0.32f * sin(i * 2.1f))
            val cy = h * (0.42f + 0.34f * cos(i * 1.7f))
            val r = max(w, h) * (0.62f - 0.10f * i)
            paint.shader = RadialGradient(
                cx, cy, max(8f, r),
                intArrayOf(p[i], p[i] and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        paint.shader = null
        return out
    }
}
