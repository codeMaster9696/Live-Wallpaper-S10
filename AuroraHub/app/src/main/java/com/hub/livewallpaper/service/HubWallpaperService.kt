package com.hub.livewallpaper.service

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

        private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as? PowerManager }

        // CORREÇÃO: Substituído OnPowerSaveModeChangedListener (API 33+) por BroadcastReceiver
        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager?.isPowerSaveMode == true
                applyRunningState()
            }
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
            setTouchEventsEnabled(false)
            powerSave = powerManager?.isPowerSaveMode == true
            
            prefs.register(prefListener)
            
            // CORREÇÃO: Registrar o BroadcastReceiver em vez da API nova
            val filter = IntentFilter(Intent.ACTION_POWER_SAVE_MODE_CHANGED)
            baseContext.registerReceiver(powerReceiver, filter)
            
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
            s.attach(holder.surface, width, height)
            applyRunningState()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
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

        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors? {
            val p = current?.palette ?: return null
            if (p.size < 3) return null
            return WallpaperColors(Color.valueOf(p[1]), Color.valueOf(p[2]), Color.valueOf(p[3]))
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregister(prefListener)
            
            // CORREÇÃO: Desregistrar o BroadcastReceiver com segurança
            runCatching { baseContext.unregisterReceiver(powerReceiver) }
            
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
