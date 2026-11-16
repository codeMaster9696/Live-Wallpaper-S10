package com.hub.livewallpaper.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import com.hub.livewallpaper.data.RenderConfig
import com.hub.livewallpaper.data.ScaleMode
import com.hub.livewallpaper.data.Wallpaper

/**
 * =====================================================================
 *  Renderizador GLES 2.0 do wallpaper.
 * =====================================================================
 *  Dois caminhos de entrada, UM pipeline de saída:
 *
 *   VÍDEO       MediaPlayer -> SurfaceTexture -> GL_TEXTURE_EXTERNAL_OES
 *               -> video.frag (crop UV) -> [FBO] -> tela
 *
 *   PROCEDURAL  <nome>.frag (scene()) -> [FBO] -> tela
 *
 *  [FBO] só existe quando a preferência "Qualidade" é < 100%: renderizamos
 *  em resolução reduzida e fazemos um blit com filtro linear para a
 *  superfície nativa. Em um S10 isso corta ~44% do trabalho de fragmento
 *  sem mudar o enquadramento — o crop continua sendo calculado em pixels
 *  REAIS da superfície.
 *
 *  Nada aqui aloca objeto por frame: programas, texturas, buffers e o
 *  FloatArray da matriz são criados uma vez e reutilizados.
 */
class WallpaperRenderer(context: Context) {

    private val shaders = ShaderLibrary(context)
    private val quad = FullscreenQuad()
    private val stMatrix = FloatArray(16)
    private val target = RenderTarget(0, 0)

    private var sceneProgram: GLProgram? = null
    private var sceneName: String? = null
    private var videoProgram: GLProgram? = null
    private var blitProgram: GLProgram? = null

    private var externalTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var hasFrame = false
    private var videoFade = 0f

    private var surfaceW = 0
    private var surfaceH = 0
    private var mediaW = 0
    private var mediaH = 0

    private var wallpaper: Wallpaper? = null
    private var config: RenderConfig = RenderConfig.DEFAULT

    /** Último cálculo de escala — usado pela tela de ajustes e pelo logcat. */
    var lastScale: ScaleMath.Result? = null
        private set

    val videoActive: Boolean get() = hasFrame && surfaceTexture != null

    // ------------------------------------------------------------------
    //  Ciclo de vida
    // ------------------------------------------------------------------

    fun resize(w: Int, h: Int) {
        surfaceW = w.coerceAtLeast(1)
        surfaceH = h.coerceAtLeast(1)
    }

    fun setConfig(cfg: RenderConfig) { config = cfg }

    /** Troca o wallpaper atual. Compila o shader apenas se mudou. */
    fun setWallpaper(wp: Wallpaper) {
        if (wallpaper?.id == wp.id && sceneProgram != null) return
        wallpaper = wp
        val name = wp.shader ?: ShaderLibrary.SAFE_FALLBACK
        if (sceneName != name || sceneProgram == null) {
            sceneProgram?.delete()
            sceneProgram = try {
                GLProgram(shaders.vertex, shaders.procedural(name))
            } catch (t: Throwable) {
                Log.e(TAG, "Shader '$name' inválido, usando fallback", t)
                if (name != ShaderLibrary.SAFE_FALLBACK) {
                    runCatching { GLProgram(shaders.vertex, shaders.procedural(ShaderLibrary.SAFE_FALLBACK)) }.getOrNull()
                } else null
            }
            sceneName = name
        }
        videoFade = 0f
        hasFrame = false
    }

    /**
     * Cria a textura externa + SurfaceTexture que receberá os frames do
     * decoder. Precisa ser chamado com o contexto EGL corrente.
     */
    fun createVideoSurfaceTexture(): SurfaceTexture {
        releaseVideo()
        externalTex = ExternalTexture.create()
        val st = SurfaceTexture(externalTex)
        surfaceTexture = st
        if (videoProgram == null) {
            videoProgram = runCatching { GLProgram(shaders.vertex, shaders.video()) }.getOrNull()
        }
        if (blitProgram == null) {
            blitProgram = runCatching { GLProgram(shaders.vertex, shaders.blit()) }.getOrNull()
        }
        return st
    }

    /** Chamado quando o MediaPlayer conhece o tamanho real do vídeo. */
    fun setMediaSize(w: Int, h: Int) {
        mediaW = w.coerceAtLeast(1)
        mediaH = h.coerceAtLeast(1)
        surfaceTexture?.setDefaultBufferSize(mediaW, mediaH)
        Log.i(TAG, "mídia ${mediaW}x${mediaH}")
    }

    fun releaseVideo() {
        surfaceTexture?.let { runCatching { it.release() } }
        surfaceTexture = null
        ExternalTexture.delete(externalTex)
        externalTex = 0
        hasFrame = false
        videoFade = 0f
    }

    /** Libera TODOS os recursos GL. Chamar com o contexto corrente. */
    fun releaseGL() {
        releaseVideo()
        sceneProgram?.delete(); sceneProgram = null; sceneName = null
        videoProgram?.delete(); videoProgram = null
        blitProgram?.delete(); blitProgram = null
        target.destroy()
    }

    // ------------------------------------------------------------------
    //  Frame
    // ------------------------------------------------------------------

    fun render(time: Float, frameAvailable: Boolean) {
        if (surfaceW <= 0 || surfaceH <= 0) return

        if (frameAvailable && surfaceTexture != null) {
            runCatching {
                surfaceTexture!!.updateTexImage()
                surfaceTexture!!.getTransformMatrix(stMatrix)
                hasFrame = true
            }.onFailure { Log.w(TAG, "updateTexImage falhou", it) }
        }

        // Resolução interna conforme a preferência de qualidade.
        val q = config.quality.coerceIn(0.4f, 1f)
        val useFbo = q < 0.99f
        var rw = surfaceW
        var rh = surfaceH
        if (useFbo) {
            target.ensure((surfaceW * q).toInt(), (surfaceH * q).toInt())
            if (target.isReady) {
                rw = target.width; rh = target.height
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target.framebuffer)
            }
        }
        if (!useFbo || !target.isReady) GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        GLES20.glViewport(0, 0, rw, rh)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glClearColor(0.02f, 0.02f, 0.03f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val wp = wallpaper
        val showVideo = hasFrame && videoProgram != null && wp?.isVideo == true && externalTex != 0

        // 1) Cena procedural: sempre no modo PROCEDURAL; no modo VÍDEO ela
        //    serve de fundo até o primeiro frame chegar (nunca tela preta).
        if (!showVideo || videoFade < 1f) {
            sceneProgram?.let { p ->
                p.use()
                p.f2("uResolution", rw.toFloat(), rh.toFloat())
                p.f("uTime", time)
                p.f("uBrightness", config.brightness)
                p.f("uSaturation", config.saturation)
                p.f("uGrain", config.grain)
                p.f("uParallax", config.parallax)
                quad.draw(p)
            }
        }

        // 2) Vídeo por cima, com crossfade de ~12 frames.
        if (showVideo) {
            videoFade = (videoFade + 0.08f).coerceAtMost(1f)

            val mw = if (mediaW > 0) mediaW else surfaceW
            val mh = if (mediaH > 0) mediaH else surfaceH
            val s = ScaleMath.compute(surfaceW, surfaceH, mw, mh, config.scaleMode, config.overscan)
            lastScale = s
            val offsetX = ScaleMath.parallaxUvOffset(s, (config.parallax + 1f) * 0.5f)

            val p = videoProgram!!
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            p.use()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTex)
            p.i("uTex", 0)
            p.mat4("uSTMatrix", stMatrix)
            p.f2("uUvScale", s.uvScaleX, s.uvScaleY)
            p.f2("uUvOffset", offsetX, 0f)
            p.f2("uResolution", rw.toFloat(), rh.toFloat())
            p.f("uTime", time)
            p.f("uBrightness", config.brightness)
            p.f("uSaturation", config.saturation)
            p.f("uGrain", config.grain)
            p.f("uFade", videoFade)
            quad.draw(p)
            GLES20.glDisable(GLES20.GL_BLEND)
        } else {
            val s = ScaleMath.compute(surfaceW, surfaceH, surfaceW, surfaceH, ScaleMode.FILL, 1f)
            lastScale = s
        }

        // 3) Blit do FBO para a superfície real.
        if (useFbo && target.isReady) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, surfaceW, surfaceH)
            val b = blitProgram ?: runCatching {
                GLProgram(shaders.vertex, shaders.blit()).also { blitProgram = it }
            }.getOrNull()
            b?.let {
                it.use()
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, target.texture)
                it.i("uTex", 0)
                quad.draw(it)
            }
        }
    }

    private companion object { const val TAG = "WallpaperRenderer" }
}
