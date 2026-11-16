package com.hub.livewallpaper.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hub.livewallpaper.service.HubWallpaperService

/**
 * Aplicação do wallpaper no sistema — com verificação, não com "fé".
 *
 * Estratégia em duas etapas (tentativa + verificação):
 *
 *  1) tenta WallpaperManager.setWallpaperComponent() — troca sem UI.
 *     ATENÇÃO: esse método exige android.permission.SET_WALLPAPER_COMPONENT,
 *     que é signature|privileged. Em um APK instalado normalmente (nosso
 *     caso) o sistema lança SecurityException. Não declaramos a permissão
 *     no manifesto justamente porque ela nunca seria concedida.
 *
 *  2) com a exceção capturada, abrimos o fluxo OFICIAL:
 *     ACTION_CHANGE_LIVE_WALLPAPER + EXTRA_LIVE_WALLPAPER_COMPONENT.
 *     É o mesmo caminho que o Settings da One UI usa: abre a prévia do
 *     sistema já com o nosso serviço selecionado e o botão
 *     "Definir papel de parede" (o usuário escolhe inicial/bloqueio/ambas).
 *
 *  Ou seja: um toque no app + um toque de confirmação no sistema.
 *  Nenhum app de terceiros consegue pular essa confirmação sem root.
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
        val wm = WallpaperManager.getInstance(activity)
        val target = component(activity)

        // ---- Etapa 1: caminho direto (só vinga em app de sistema/root) ----
        try {
            @Suppress("MissingPermission")
            wm.setWallpaperComponent(target)
            val info = wm.wallpaperInfo
            if (info != null && info.component == target) {
                Log.i(TAG, "aplicado diretamente")
                return Result.Direct
            }
            Log.w(TAG, "setWallpaperComponent não pegou (info=${info?.component})")
        } catch (t: Throwable) {
            // Esperado em APK sideloaded: SecurityException.
            Log.i(TAG, "caminho direto indisponível (${t.javaClass.simpleName}); usando o seletor do sistema")
        }

        // ---- Etapa 2: seletor do sistema ---------------------------------
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
