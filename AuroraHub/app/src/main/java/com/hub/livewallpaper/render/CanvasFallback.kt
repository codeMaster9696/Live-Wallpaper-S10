package com.hub.livewallpaper.render

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.Surface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Último recurso: se o EGL não inicializar (driver recusado, contexto
 * perdido, aparelho exótico), ainda assim desenhamos um wallpaper animado
 * com Canvas 2D. O usuário NUNCA vê tela preta.
 *
 * Os gradientes são criados uma única vez; por frame só atualizamos a
 * matriz local de cada shader (zero alocação no loop).
 */
class CanvasFallback(private val surface: Surface) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var gradients: List<RadialGradient> = emptyList()
    private var builtForW = 0
    private var builtForH = 0
    private var palette: List<Int> = emptyList()

    private fun build(w: Int, h: Int, colors: List<Int>) {
        val base = if (colors.size >= 4) colors else listOf(0xFF0B0A0F.toInt(), 0xFF5E5CE6.toInt(), 0xFFFF3DCB.toInt(), 0xFFFFB020.toInt())
        val r = maxOf(w, h) * 0.62f
        gradients = listOf(
            RadialGradient(0f, 0f, r, intArrayOf(base[1], base[1] and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP),
            RadialGradient(0f, 0f, r * 0.8f, intArrayOf(base[2], base[2] and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP),
            RadialGradient(0f, 0f, r * 0.55f, intArrayOf(base[3], base[3] and 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        )
        palette = base
        builtForW = w
        builtForH = h
    }

    fun draw(t: Float, w: Int, h: Int, colors: List<Int>) {
        if (!surface.isValid || w <= 0 || h <= 0) return
        if (gradients.isEmpty() || builtForW != w || builtForH != h || palette != colors) build(w, h, colors)

        var canvas: Canvas? = null
        try {
            canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                surface.lockHardwareCanvas()
            } else {
                @Suppress("DEPRECATION")
                surface.lockCanvas(null)
            }
            if (canvas == null) return

            canvas.drawColor(if (palette.isNotEmpty()) palette[0] else 0xFF0B0A0F.toInt())
            paint.isDither = true
            for (i in gradients.indices) {
                val fi = i.toFloat()
                val cx = w * (0.5f + 0.30f * sin(t * (0.18f + 0.05f * fi) + fi * 2.1f))
                val cy = h * (0.5f + 0.34f * cos(t * (0.14f + 0.04f * fi) + fi * 1.3f))
                matrix.reset()
                matrix.postTranslate(cx, cy)
                gradients[i].setLocalMatrix(matrix)
                paint.shader = gradients[i]
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            paint.shader = null
        } catch (t2: Throwable) {
            Log.w(TAG, "Canvas fallback falhou", t2)
        } finally {
            if (canvas != null) runCatching { surface.unlockCanvasAndPost(canvas) }
        }
    }

    private companion object { const val TAG = "CanvasFallback" }
}
