package com.hub.livewallpaper.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hub.livewallpaper.service.HubWallpaperService

/**
 * Aplicação do wallpaper no sistema.
 * 
 * A tentativa de usar setWallpaperComponent() foi removida porque exige 
 * permissões de sistema que um APK comum não possui (causando erro de compilação).
 * Agora usamos o fluxo oficial via Intent, que abre a tela de prévia do Android.
 */
object WallpaperApply {

    sealed class Result {
        object Direct : Result()
        object Chooser : Result()
        object Failed : Result()
    }

    fun component(context: Context): ComponentName =
        ComponentName(context.packageName, HubWallpaperService::class.java.name)

    fun isAlreadyActive(context: Context): Boolean {
        val info = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
        return info.component == component(context)
    }

    fun apply(activity: Activity): Result {
        val target = component(activity)

        // ---- Caminho Oficial: Abre o seletor do sistema -------------------
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, target)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            activity.startActivity(intent)
            Result.Chooser
        } catch (t: Throwable) {
            Log.e(TAG, "ACTION_CHANGE_LIVE_WALLPAPER indisponível", t)
            try {
                activity.startActivity(
                    Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                Result.Chooser
            } catch (t2: Throwable) {
                Log.e(TAG, "nenhum seletor disponível", t2)
                Result.Failed
            }
        }
    }

    /** Atalho para os ajustes do sistema (bateria, telas, etc.). */
    fun openSystemWallpaperSettings(activity: Activity) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private const val TAG = "WallpaperApply"
}
