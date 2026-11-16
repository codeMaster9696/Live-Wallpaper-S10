package com.hub.livewallpaper.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log

object Catalog {

    private const val TAG = "Catalog"
    const val VIDEO_DIR = "wallpapers"
    // O nome da pasta que você vai criar dentro de "Movies" (Filmes) no celular
    const val CUSTOM_FOLDER = "AuroraHub" 

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

        // 1. Lê os vídeos de dentro do APK (CORRIGIDO: sem a palavra "assets/")
        try {
            val files = context.assets.list(VIDEO_DIR) ?: emptyArray()
            files.filter { it.endsWith(".mp4", ignoreCase = true) }.forEach { file ->
                extras += createWallpaperFromFile(file, "$VIDEO_DIR/$file")
            }
        } catch (t: Throwable) { Log.w(TAG, "Erro assets", t) }

        // 2. Lê APENAS os vídeos da pasta "Movies/AuroraHub" do celular
        try {
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
            
            // Filtro para pegar apenas a pasta que queremos
            val selection: String
            val selectionArgs: Array<String>
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                selectionArgs = arrayOf("Movies/$CUSTOM_FOLDER%")
            } else {
                selection = "${MediaStore.Video.Media.DATA} LIKE ?"
                selectionArgs = arrayOf("%Movies/$CUSTOM_FOLDER%")
            }

            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null
            )
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    if (name.endsWith(".mp4", ignoreCase = true)) {
                        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        extras += createWallpaperFromFile(name, contentUri.toString())
                    }
                }
            }
        } catch (t: Throwable) { Log.w(TAG, "Erro MediaStore", t) }

        return (builtin + extras).distinctBy { it.id }
    }

    private fun createWallpaperFromFile(fileName: String, path: String): Wallpaper {
        return Wallpaper(
            id = "local_" + fileName.hashCode(),
            title = fileName.substringBeforeLast('.').replace('_', ' '),
            origin = Origin.OXYGENOS,
            kind = SourceKind.VIDEO,
            asset = path,
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
