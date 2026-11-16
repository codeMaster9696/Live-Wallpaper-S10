package com.hub.livewallpaper.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Fonte única de verdade das preferências.
 *
 * O WallpaperService roda em OUTRO momento do ciclo de vida da Activity
 * (a Activity pode estar morta). Por isso a comunicação é feita por
 * SharedPreferences + OnSharedPreferenceChangeListener: ao mover um slider
 * na tela de ajustes, o Engine ativo recebe o novo RenderConfig no mesmo
 * instante, sem reiniciar o serviço e sem piscar a tela.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var wallpaperId: String
        get() = sp.getString(K_ID, RenderConfig.DEFAULT.wallpaperId)!!
        set(v) = sp.edit().putString(K_ID, v).apply()

    var speed: Float
        get() = sp.getFloat(K_SPEED, RenderConfig.DEFAULT.speed)
        set(v) = sp.edit().putFloat(K_SPEED, v.coerceIn(RenderConfig.MIN_SPEED, RenderConfig.MAX_SPEED)).apply()

    var scaleMode: ScaleMode
        get() = runCatching { ScaleMode.valueOf(sp.getString(K_SCALE, ScaleMode.FILL.name)!!) }.getOrDefault(ScaleMode.FILL)
        set(v) = sp.edit().putString(K_SCALE, v.name).apply()

    var overscan: Float
        get() = sp.getFloat(K_OVERSCAN, 1f)
        set(v) = sp.edit().putFloat(K_OVERSCAN, v.coerceIn(1f, 1.3f)).apply()

    var brightness: Float
        get() = sp.getFloat(K_BRIGHT, 1f)
        set(v) = sp.edit().putFloat(K_BRIGHT, v.coerceIn(0.5f, 1.5f)).apply()

    var saturation: Float
        get() = sp.getFloat(K_SAT, 1f)
        set(v) = sp.edit().putFloat(K_SAT, v.coerceIn(0f, 1.8f)).apply()

    var grain: Float
        get() = sp.getFloat(K_GRAIN, 0.06f)
        set(v) = sp.edit().putFloat(K_GRAIN, v.coerceIn(0f, 0.25f)).apply()

    var quality: Float
        get() = sp.getFloat(K_QUALITY, 0.75f)
        set(v) = sp.edit().putFloat(K_QUALITY, v.coerceIn(0.4f, 1f)).apply()

    var fpsCap: Int
        get() = sp.getInt(K_FPS, 60)
        set(v) = sp.edit().putInt(K_FPS, v.coerceIn(15, 120)).apply()

    var muteVideo: Boolean
        get() = sp.getBoolean(K_MUTE, true)
        set(v) = sp.edit().putBoolean(K_MUTE, v).apply()

    var pauseOnBatterySaver: Boolean
        get() = sp.getBoolean(K_BATTERY, true)
        set(v) = sp.edit().putBoolean(K_BATTERY, v).apply()

    /** Lê tudo de uma vez — chamado fora do loop de render. */
    fun snapshot(parallax: Float = 0f): RenderConfig = RenderConfig(
        wallpaperId = wallpaperId,
        speed = speed,
        scaleMode = scaleMode,
        overscan = overscan,
        brightness = brightness,
        saturation = saturation,
        grain = grain,
        quality = quality,
        fpsCap = fpsCap,
        muteVideo = muteVideo,
        pauseOnBatterySaver = pauseOnBatterySaver,
        parallax = parallax
    )

    fun reset() {
        val keep = wallpaperId
        sp.edit().clear().apply()
        wallpaperId = keep
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val FILE = "aurora_hub_prefs"
        private const val K_ID = "wallpaper_id"
        private const val K_SPEED = "speed"
        private const val K_SCALE = "scale_mode"
        private const val K_OVERSCAN = "overscan"
        private const val K_BRIGHT = "brightness"
        private const val K_SAT = "saturation"
        private const val K_GRAIN = "grain"
        private const val K_QUALITY = "quality"
        private const val K_FPS = "fps_cap"
        private const val K_MUTE = "mute_video"
        private const val K_BATTERY = "pause_battery_saver"
    }
}
