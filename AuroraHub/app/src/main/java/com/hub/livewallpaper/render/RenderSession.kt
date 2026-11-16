package com.hub.livewallpaper.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import com.hub.livewallpaper.data.Catalog
import com.hub.livewallpaper.data.RenderConfig
import com.hub.livewallpaper.data.Wallpaper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * =====================================================================
 *  Sessão de renderização — o coração reutilizável do app.
 * =====================================================================
 *  Usada em DOIS lugares:
 *    · HubWallpaperService.HubEngine  (wallpaper real do sistema)
 *    · PreviewSurfaceView             (prévia dentro da galeria/ajustes)
 *
 *  Responsabilidades:
 *    · uma única thread GL com Looper + Choreographer (vsync);
 *    · ciclo de vida EGL à prova de recriação de Surface (troca de
 *      wallpaper, rotação, tela apagada, retorno do launcher);
 *    · limite de FPS e pausa total quando invisível (bateria);
 *    · MediaPlayer ligado/desligado junto com a superfície;
 *    · fallback automático vídeo -> procedural -> Canvas;
 *    · telemetria (fps, escala, corte, cobertura) para a UI.
 *
 *  Regras de thread: métodos públicos podem ser chamados de qualquer
 *  thread; tudo que toca GL é postado para a RenderThread.
 */
class RenderSession(private val context: Context, private val label: String) {

    data class Stats(
        val fps: Float,
        val surfaceW: Int,
        val surfaceH: Int,
        val mediaW: Int,
        val mediaH: Int,
        val scale: Float,
        val cropX: Float,
        val cropY: Float,
        val letterboxX: Float,
        val letterboxY: Float,
        val coverage: Float,
        val backend: String,
        val videoActive: Boolean
    )

    var onStats: ((Stats) -> Unit)? = null
    var onVideoFallback: ((String) -> Unit)? = null

    @Volatile private var config: RenderConfig = RenderConfig.DEFAULT
    @Volatile private var wallpaper: Wallpaper = Catalog.builtins().first()
    @Volatile private var running: Boolean = true

    private var thread: RenderThread? = null
    private val main = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------
    //  API pública
    // ------------------------------------------------------------------

    fun setWallpaper(wp: Wallpaper) {
        if (wallpaper.id == wp.id) return
        wallpaper = wp
        post { it.changeWallpaper(wp) }
    }

    fun setConfig(cfg: RenderConfig) {
        val old = config
        config = cfg
        post { t ->
            if (old.speed != cfg.speed) t.video?.applySpeed(cfg.speed)
            if (old.muteVideo != cfg.muteVideo) t.video?.applyMute(cfg.muteVideo)
            if (!running) t.lastRenderNanos = 0L
        }
    }

    fun setRunning(value: Boolean) {
        if (running == value) return
        running = value
        post { t ->
            t.video?.setPlaying(value)
            t.lastRenderNanos = 0L
            if (value) t.schedule() else t.unschedule()
        }
    }

    fun attach(surface: Surface, width: Int, height: Int) {
        val t = ensureThread()
        t.handler?.post { t.bind(surface, width, height) }
    }

    fun resize(width: Int, height: Int) {
        post { it.resizeSurface(width, height) }
    }

    /** Surface morreu: solta GL/decoder, mas mantém a thread pronta para reuso. */
    fun detach() {
        post { it.unbind() }
    }

    /** Encerra de vez (Engine.onDestroy / View.onDetachedFromWindow). */
    fun release() {
        val t = thread ?: return
        thread = null
        t.handler?.post {
            t.unbind()
            Looper.myLooper()?.quitSafely()
        } ?: run { t.quitSafelyLooper() }
    }

    private fun post(action: (RenderThread) -> Unit) {
        val t = thread ?: return
        t.handler?.post { action(t) }
    }

    private fun ensureThread(): RenderThread {
        thread?.let { if (it.isAlive) return it }
        val t = RenderThread()
        thread = t
        t.start()
        // Espera curta e limitada: o Looper precisa existir antes do 1º post.
        t.ready.await(2500, TimeUnit.MILLISECONDS)
        return t
    }

    // ------------------------------------------------------------------
    //  Thread de renderização
    // ------------------------------------------------------------------

    private inner class RenderThread : Thread("AuroraGL-$label") {

        val ready = CountDownLatch(1)
        var handler: Handler? = null

        private var egl: EglCore? = null
        private var eglSurface: EGLSurface? = null
        private var renderer: WallpaperRenderer? = null
        private var fallback: CanvasFallback? = null
        private var choreographer: Choreographer? = null

        var video: VideoSource? = null
        private var surface: Surface? = null
        private var width = 0
        private var height = 0
        private var videoFailed = false
        private var frameAvailable = false
        private var scheduled = false
        private var mediaW = 0
        private var mediaH = 0

        private var timeSec = 0.0
        var lastRenderNanos = 0L
        private var frames = 0
        private var windowStart = 0L
        private var fps = 0f
        private var lastStats = 0L

        override fun run() {
            Looper.prepare()
            handler = Handler(Looper.myLooper()!!)
            choreographer = Choreographer.getInstance()
            try {
                egl = EglCore()
                renderer = WallpaperRenderer(context)
            } catch (t: Throwable) {
                Log.e(TAG, "EGL indisponível — fallback Canvas 2D", t)
                egl?.release()
                egl = null
                renderer = null
            }
            ready.countDown()
            Looper.loop()
            // Looper encerrado: limpa o que sobrou.
            unbind()
            egl?.release()
            egl = null
        }

        fun quitSafelyLooper() {
            handler?.post { Looper.myLooper()?.quitSafely() }
        }

        // ---------------- superfície ----------------

        fun bind(s: Surface, w: Int, h: Int) {
            unbind()
            surface = s
            width = w.coerceAtLeast(1)
            height = h.coerceAtLeast(1)
            videoFailed = false
            timeSec = 0.0
            lastRenderNanos = 0L

            val core = egl
            val r = renderer
            if (core != null && r != null) {
                try {
                    eglSurface = core.createWindowSurface(s)
                    core.makeCurrent(eglSurface!!)
                    r.resize(width, height)
                    r.setConfig(config)
                    r.setWallpaper(wallpaper)
                    startVideoIfNeeded()
                } catch (t: Throwable) {
                    Log.e(TAG, "Falha ao criar janela EGL", t)
                    core.destroySurface(eglSurface)
                    eglSurface = null
                    fallback = CanvasFallback(s)
                }
            } else {
                fallback = CanvasFallback(s)
            }
            if (running) schedule()
        }

        fun resizeSurface(w: Int, h: Int) {
            if (surface == null) return
            width = w.coerceAtLeast(1)
            height = h.coerceAtLeast(1)
            renderer?.resize(width, height)
            lastRenderNanos = 0L
        }

        fun unbind() {
            unschedule()
            video?.release()
            video = null
            val core = egl
            if (core != null && eglSurface != null) {
                runCatching {
                    core.makeCurrent(eglSurface!!)
                    renderer?.releaseGL()
                }
                core.destroySurface(eglSurface)
            }
            eglSurface = null
            fallback = null
            surface = null
            frameAvailable = false
            mediaW = 0
            mediaH = 0
        }

        fun changeWallpaper(wp: Wallpaper) {
            val r = renderer ?: return
            if (surface == null) return
            video?.release()
            video = null
            videoFailed = false
            frameAvailable = false
            mediaW = 0; mediaH = 0
            timeSec = 0.0
            r.setWallpaper(wp)
            startVideoIfNeeded()
        }

        // ---------------- vídeo ----------------

        private fun startVideoIfNeeded() {
            val wp = wallpaper
            val r = renderer ?: return
            if (!wp.isVideo || videoFailed || eglSurface == null) return
            val asset = wp.asset ?: return
            try {
                val st = r.createVideoSurfaceTexture()
                st.setOnFrameAvailableListener({ frameAvailable = true }, handler)
                val src = VideoSource(context)
                video = src
                src.open(asset, st, config.muteVideo, config.speed, object : VideoSource.Callback {
                    override fun onVideoReady(width: Int, height: Int) {
                        mediaW = width
                        mediaH = height
                        r.setMediaSize(width, height)
                    }

                    override fun onVideoFatal(message: String) {
                        videoFailed = true
                        src.release()
                        video = null
                        r.releaseVideo()
                        Log.w(TAG, "Vídeo falhou ($message) — procedural assume")
                        main.post { onVideoFallback?.invoke(message) }
                    }
                })
            } catch (t: Throwable) {
                videoFailed = true
                Log.e(TAG, "Sem SurfaceTexture — procedural assume", t)
            }
        }

        // ---------------- loop ----------------

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                scheduled = false
                if (surface == null || !running) return

                val cap = config.fpsCap.coerceIn(15, 120)
                val interval = 1_000_000_000L / cap
                // 2 ms de tolerância: o vsync de 60 Hz tem jitter e não
                // queremos cair para 30 fps por causa de 0,1 ms.
                if (lastRenderNanos == 0L || frameTimeNanos - lastRenderNanos >= interval - 2_000_000L) {
                    val dt = if (lastRenderNanos == 0L) 0.0
                    else ((frameTimeNanos - lastRenderNanos) / 1_000_000_000.0).coerceAtMost(0.1)
                    lastRenderNanos = frameTimeNanos
                    timeSec += dt * config.speed
                    draw(frameTimeNanos)

                    frames++
                    if (windowStart == 0L) windowStart = frameTimeNanos
                    val elapsed = frameTimeNanos - windowStart
                    if (elapsed >= 500_000_000L) {
                        fps = frames * 1_000_000_000f / elapsed
                        frames = 0
                        windowStart = frameTimeNanos
                    }
                    if (frameTimeNanos - lastStats >= 500_000_000L) {
                        lastStats = frameTimeNanos
                        publishStats()
                    }
                }
                schedule()
            }
        }

        fun schedule() {
            if (scheduled || surface == null || !running) return
            scheduled = true
            choreographer?.postFrameCallback(frameCallback)
        }

        fun unschedule() {
            choreographer?.removeFrameCallback(frameCallback)
            scheduled = false
        }

        private fun draw(nowNanos: Long) {
            val core = egl
            val es = eglSurface
            val r = renderer
            if (core != null && es != null && r != null) {
                try {
                    core.makeCurrent(es)
                    r.setConfig(config)
                    r.render(timeSec.toFloat(), frameAvailable)
                    frameAvailable = false
                    core.setPresentationTime(es, nowNanos)
                    if (!core.swap(es)) Log.w(TAG, "eglSwapBuffers=false (superfície abandonada?)")
                } catch (t: Throwable) {
                    Log.e(TAG, "Erro no frame", t)
                    // Superfície provavelmente morreu: solta e espera o
                    // próximo onSurfaceCreated do sistema.
                    unbind()
                }
            } else {
                fallback?.draw(timeSec.toFloat(), width, height, wallpaper.palette)
                frameAvailable = false
            }
        }

        private fun publishStats() {
            val cb = onStats ?: return
            val s = renderer?.lastScale
            val backend = when {
                renderer == null -> "Canvas 2D (fallback)"
                renderer?.videoActive == true -> "MediaPlayer + GLES (OES)"
                else -> "GLES procedural"
            }
            val stats = Stats(
                fps = fps,
                surfaceW = width,
                surfaceH = height,
                mediaW = mediaW,
                mediaH = mediaH,
                scale = s?.scale ?: 1f,
                cropX = s?.cropLeftPx ?: 0f,
                cropY = s?.cropTopPx ?: 0f,
                letterboxX = s?.letterboxXpx ?: 0f,
                letterboxY = s?.letterboxYpx ?: 0f,
                coverage = s?.coverage ?: 1f,
                backend = backend,
                videoActive = renderer?.videoActive == true
            )
            main.post { cb(stats) }
        }
    }

    private companion object { const val TAG = "RenderSession" }
}
