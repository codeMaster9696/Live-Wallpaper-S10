package com.hub.livewallpaper.ui

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.hub.livewallpaper.data.RenderConfig
import com.hub.livewallpaper.data.Wallpaper
import com.hub.livewallpaper.render.RenderSession

/**
 * Prévia WYSIWYG dentro do app.
 *
 * Usa EXATAMENTE a mesma RenderSession do WallpaperService: o que você vê
 * aqui é o mesmo pipeline (EGL + crop + shader) que vai para a tela inicial.
 * Se o vídeo falhar aqui, ele também falharia lá — melhor descobrir agora.
 */
class PreviewSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var session: RenderSession? = null
    private var wallpaper: Wallpaper? = null
    private var config: RenderConfig = RenderConfig.DEFAULT
    private var paused = false
    private var lastW = 0
    private var lastH = 0

    var onStats: ((RenderSession.Stats) -> Unit)? = null
    var onVideoFallback: ((String) -> Unit)? = null

    init {
        holder.addCallback(this)
    }

    fun setWallpaper(wp: Wallpaper) {
        wallpaper = wp
        session?.setWallpaper(wp)
    }

    fun setConfig(cfg: RenderConfig) {
        config = cfg
        session?.setConfig(cfg)
    }

    /** Chamado por onStart/onStop da Activity: prévia não roda em segundo plano. */
    fun setPaused(value: Boolean) {
        paused = value
        session?.setRunning(!value)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val s = session ?: RenderSession(context, "preview").also { session = it }
        s.onStats = { st -> onStats?.invoke(st) }
        s.onVideoFallback = { msg -> onVideoFallback?.invoke(msg) }
        wallpaper?.let { s.setWallpaper(it) }
        s.setConfig(config)
        val w = if (width > 0) width else 1
        val h = if (height > 0) height else 1
        lastW = w; lastH = h
        s.attach(holder.surface, w, h)
        s.setRunning(!paused)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width == lastW && height == lastH) return
        lastW = width; lastH = height
        // Recria a janela EGL com o tamanho novo (evita esticar o buffer antigo).
        session?.attach(holder.surface, width, height)
        session?.setRunning(!paused)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        session?.setRunning(false)
        session?.detach()
    }

    override fun onDetachedFromWindow() {
        session?.release()
        session = null
        super.onDetachedFromWindow()
    }
}
