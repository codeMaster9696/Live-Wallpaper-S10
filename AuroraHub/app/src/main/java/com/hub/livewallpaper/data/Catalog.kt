package com.hub.livewallpaper.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Catálogo do hub.
 * Lê os vídeos embutidos + vídeos locais das pastas Movies (Filmes) e Downloads.
 */
object Catalog {

    private const val TAG = "Catalog"
    const val VIDEO_DIR = "wallpapers"

    private val builtin: List<Wallpaper> = listOf(
        Wallpaper(
            id = "oxygen_ribbon", title = "Abstrato Vermelho", origin = Origin.OXYGENOS,
            kind = SourceKind.VIDEO, asset = "wallpapers/oxygen_ribbon.mp4", shader = "oxygen_ribbon",
            palette = listOf(0xFF120608.toInt(), 0xFFEB0028.toInt(), 0xFFFF6A3D.toInt(), 0xFFFFB020.toInt()),
            description = "Fitas de luz vermelha sobre grafite.", defaultSpeed = 1f
        ),
        Wallpaper(
            id = "oxygen_aurora", title = "Aurora Grafite", origin = Origin.OXYGENOS,
            kind = SourceKind.PROCEDURAL, shader = "oxygen_aurora",
            palette = listOf(0xFF05070A.toInt(), 0xFF0B3B34.toInt(), 0xFF34E39B.toInt(), 0xFFA3FF3C.toInt()),
            description = "Cortinas de aurora verde-esmeralda procedural.", defaultSpeed = 0.8f
        )
    )

    fun builtins(): List<Wallpaper> = builtin

    fun all(context: Context): List<Wallpaper> {
        val extras = mutableListOf<Wallpaper>()

        // 1. Lê os vídeos que estão dentro do APK (assets)
        try {
            val files = context.assets.list(VIDEO_DIR) ?: emptyArray()
            files.filter { it.endsWith(".mp4", ignoreCase = true) }.forEach { file ->
                extras += createWallpaperFromFile(file, "assets/$VIDEO_DIR/$file")
            }
        } catch (t: Throwable) { Log.w(TAG, "Erro assets", t) }

        // 2. Lê a pasta Movies (Filmes) do celular
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (moviesDir.exists()) {
                moviesDir.listFiles()?.filter { it.name.endsWith(".mp4", ignoreCase = true) }?.forEach { file ->
                    extras += createWallpaperFromFile(file.name, file.absolutePath)
                }
            }
        } catch (t: Throwable) { Log.w(TAG, "Erro Movies", t) }

        // 3. Lê a pasta Downloads do celular
        try {
            val downDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downDir.exists()) {
                downDir.listFiles()?.filter { it.name.endsWith(".mp4", ignoreCase = true) }?.forEach { file ->
                    extras += createWallpaperFromFile(file.name, file.absolutePath)
                }
            }
        } catch (t: Throwable) { Log.w(TAG, "Erro Downloads", t) }

        // Remove duplicados (se o mesmo vídeo estiver em pastas diferentes)
        return (builtin + extras).distinctBy { it.id }
    }

    private fun createWallpaperFromFile(fileName: String, path: String): Wallpaper {
        return Wallpaper(
            id = "local_" + fileName.hashCode(),
            title = fileName.substringBeforeLast('.').replace('_', ' '),
            origin = Origin.OXYGENOS, // Pode ser qualquer um, só para a tag colorida
            kind = SourceKind.VIDEO,
            asset = path, // Aqui passamos o caminho do arquivo local
            shader = null,
            palette = listOf(0xFF0B0A0F.toInt(), 0xFF5E5CE6.toInt(), 0xFFFF3DCB.toInt(), 0xFFFFB020.toInt()),
            description = "Vídeo local: $fileName",
            defaultSpeed = 1f
        )
    }

    fun byId(context: Context, id: String?): Wallpaper {
        val list = all(context)
        return list.firstOrNull { it.id == id } ?: list.first()
    }
}
