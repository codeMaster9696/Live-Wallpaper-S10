package com.hub.livewallpaper.service

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import com.hub.livewallpaper.data.Catalog
import com.hub.livewallpaper.data.Prefs
import com.hub.livewallpaper.data.Wallpaper
import com.hub.livewallpaper.render.RenderSession

/**
 * =====================================================================
 *  HubWallpaperService — o live wallpaper do sistema.
 * =====================================================================
 *
 *  Ciclo de vida real observado no Galaxy S10 / One UI 4.1:
 *
 *    onCreate → onSurfaceCreated → onSurfaceChanged(1440x3040)
 *             → onVisibilityChanged(true) → onOffsetsChanged(...)
 *    [tela apaga]        onVisibilityChanged(false)   -> paramos o loop
 *    [tela acende]       onVisibilityChanged(true)    -> retomamos
 *    [troca de wallpaper] onSurfaceDestroyed → onSurfaceCreated
 *    [rotação]            onSurfaceChanged com W/H trocados
 *
 *  Regras que este Engine respeita à risca:
 *   1. NUNCA segurar o Surface depois de onSurfaceDestroyed (o sistema
 *      mata o processo se você desenhar em superfície abandonada).
 *   2. NUNCA renderizar com visibilidade false (drena bateria e a One UI
 *      pode marcar o app como "consumo alto em segundo plano").
 *   3. Recriar TODO o estado GL a cada Surface novo — não existe
 *      "contexto EGL sobrevivente" garantido entre superfícies.
 *   4. Ler preferências fora do loop; o listener empurra RenderConfig.
 */
class HubWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HubEngine()

    inner class HubEngine : Engine() {

        private val prefs: Prefs by lazy { Prefs(baseContext) }
        private val mainHandler = Handler(Looper.getMainLooper())
        private var session: RenderSession? = null
        private var current: Wallpaper? = null

        private var visible = false
        private var xOffset = 0.5f
        private var surfaceW = 0
        private var surfaceH = 0
        private var powerSave = false

        private val powerManager by lazy { getSystemService(PowerManager::class.java) }

        private val powerListener = PowerManager.OnPowerSaveModeChangedListener { mode ->
            powerSave = mode
            applyRunningState()
        }

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            Log.d(TAG, "preferência alterada: $key")
            pushPreferences()
        }

        // ------------------------------------------------------------------
        //  Ciclo de vida do Engine
        // ------------------------------------------------------------------

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Não precisamos de toque: o launcher consome os gestos.
            setTouchEventsEnabled(false)
            powerSave = powerManager?.isPowerSaveMode == true
            prefs.register(prefListener)
            powerManager?.addOnPowerSaveModeChangedListener(powerListener)
            Log.i(TAG, "Engine criado")
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.i(TAG, "onSurfaceCreated")
            val s = session ?: RenderSession(baseContext, "engine").also { session = it }
            s.onVideoFallback = { msg -> Log.w(TAG, "fallback de vídeo: $msg") }
            s.onStats = { st ->
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "${st.backend} · ${st.fps.toInt()}fps · cobertura ${(st.coverage * 100).toInt()}%")
                }
            }
            pushPreferences()
            // O Surface já é válido aqui; usamos o tamanho conhecido (ou o
            // desejado) e deixamos onSurfaceChanged corrigir em seguida.
            val frame = holder.surfaceFrame
            val w = if (frame.width() > 0) frame.width() else desiredMinimumWidth
            val h = if (frame.height() > 0) frame.height() else desiredMinimumHeight
            surfaceW = w; surfaceH = h
            s.attach(holder.surface, w, h)
            applyRunningState()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.i(TAG, "onSurfaceChanged ${width}x${height} format=$format")
            surfaceW = width
            surfaceH = height
            val s = session ?: return
            // Se o tamanho mudou de verdade (rotação / mudança de resolução
            // WQHD+ -> FHD+ na One UI) recriamos a janela EGL: é mais seguro
            // do que reaproveitar um buffer com dimensões antigas.
            s.attach(holder.surface, width, height)
            applyRunningState()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            // O sistema pede um redesenho síncrono (ex.: voltou do AOD).
            session?.setRunning(visible && !isPausedByBattery())
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            Log.i(TAG, "onSurfaceDestroyed")
            session?.setRunning(false)
            session?.detach()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            applyRunningState()
        }

        /**
         * Swipe entre páginas da tela inicial.
         * xOffset: 0..1 (posição), xStep: quanto cada página anda.
         * Alimentamos o paralaxe do renderizador — que só usa a folga
         * criada pelo center-crop, então nunca aparece borda no swipe.
         */
        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            if (kotlin.math.abs(xOffset - this.xOffset) < 0.001f) return
            this.xOffset = xOffset
            pushPreferences()
        }

        override fun onDesiredSizeChanged(desiredWidth: Int, desiredHeight: Int) {
            super.onDesiredSizeChanged(desiredWidth, desiredHeight)
            Log.d(TAG, "desired ${desiredWidth}x${desiredHeight}")
        }

        /**
         * One UI 4.1 usa estas cores para tingir a UI do sistema a partir
         * do wallpaper. Devolver a paleta real deixa o resultado coerente.
         */
        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors? {
            val p = current?.palette ?: return null
            if (p.size < 3) return null
            return WallpaperColors(Color.valueOf(p[1]), Color.valueOf(p[2]), Color.valueOf(p[3]))
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregister(prefListener)
            powerManager?.removeOnPowerSaveModeChangedListener(powerListener)
            session?.release()
            session = null
            mainHandler.removeCallbacksAndMessages(null)
            Log.i(TAG, "Engine destruído")
        }

        // ------------------------------------------------------------------
        //  Estado
        // ------------------------------------------------------------------

        private fun isPausedByBattery(): Boolean =
            prefs.pauseOnBatterySaver && powerSave

        private fun applyRunningState() {
            val run = visible && !isPausedByBattery()
            session?.setRunning(run)
        }

        /** Lê preferências (fora do loop) e empurra para a sessão ativa. */
        private fun pushPreferences() {
            val wp = Catalog.byId(baseContext, prefs.wallpaperId)
            if (current?.id != wp.id) {
                current = wp
                session?.setWallpaper(wp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching { notifyColorsChanged() }
                }
            }
            if (current == null) current = wp
            session?.setConfig(prefs.snapshot(parallax = (xOffset - 0.5f) * 2f))
            applyRunningState()
        }
    }

    private companion object { const val TAG = "HubWallpaperService" }
}
