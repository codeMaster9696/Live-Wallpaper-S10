package com.hub.livewallpaper.data

import android.content.Context
import android.util.Log

/**
 * Catálogo do hub.
 *
 * Além da lista embutida, o catálogo VARRE assets/wallpapers/ em tempo de
 * execução: qualquer MP4 que você soltar naquela pasta aparece na galeria
 * sem tocar em uma linha de Kotlin (nome do arquivo -> título).
 */
object Catalog {

    private const val TAG = "Catalog"
    const val VIDEO_DIR = "wallpapers"

    private val builtin: List<Wallpaper> = listOf(
        Wallpaper(
            id = "oxygen_ribbon",
            title = "Abstrato Vermelho",
            origin = Origin.OXYGENOS,
            kind = SourceKind.VIDEO,
            asset = "wallpapers/oxygen_ribbon.mp4",
            shader = "oxygen_ribbon",
            palette = listOf(0xFF120608, 0xFFEB0028, 0xFFFF6A3D, 0xFFFFB020),
            description = "Fitas de luz vermelha sobre grafite, no estilo do papel de parede padrão do OxygenOS 11.",
            defaultSpeed = 1f
        ),
        Wallpaper(
            id = "oxygen_aurora",
            title = "Aurora Grafite",
            origin = Origin.OXYGENOS,
            kind = SourceKind.PROCEDURAL,
            shader = "oxygen_aurora",
            palette = listOf(0xFF05070A, 0xFF0B3B34, 0xFF34E39B, 0xFFA3FF3C),
            description = "Cortinas de aurora verde-esmeralda com poeira de estrelas. Renderização procedural, zero MB de vídeo.",
            defaultSpeed = 0.8f
        ),
        Wallpaper(
            id = "ios_mesh",
            title = "Gradiente",
            origin = Origin.IOS,
            kind = SourceKind.VIDEO,
            asset = "wallpapers/ios_mesh.mp4",
            shader = "ios_mesh",
            palette = listOf(0xFF0A0714, 0xFF5E5CE6, 0xFFFF3DCB, 0xFF0A84FF),
            description = "Malha de gradientes em movimento lento, como os wallpapers dinâmicos do iOS.",
            defaultSpeed = 0.9f
        ),
        Wallpaper(
            id = "ios_bloom",
            title = "Bloom Noturno",
            origin = Origin.IOS,
            kind = SourceKind.PROCEDURAL,
            shader = "ios_bloom",
            palette = listOf(0xFF03060F, 0xFF0A84FF, 0xFF64D2FF, 0xFFFF375F),
            description = "Pétalas iridescentes abrindo em ciclo contínuo sobre azul profundo.",
            defaultSpeed = 0.7f
        ),
        Wallpaper(
            id = "coloros_silk",
            title = "Seda Fluida",
            origin = Origin.COLOROS,
            kind = SourceKind.PROCEDURAL,
            shader = "coloros_silk",
            palette = listOf(0xFF0B0512, 0xFFFF3DCB, 0xFF7C4DFF, 0xFF00E5FF),
            description = "Seda magenta e ciano dobrando-se com domain warping, assinatura visual do ColorOS 13.",
            defaultSpeed = 1f
        ),
        Wallpaper(
            id = "coloros_particles",
            title = "Partículas Douradas",
            origin = Origin.COLOROS,
            kind = SourceKind.PROCEDURAL,
            shader = "coloros_particles",
            palette = listOf(0xFF0A0705, 0xFF6B3F12, 0xFFFFB020, 0xFFFFE7A3),
            description = "Bokeh dourado em três planos de profundidade, com deriva paralaxe entre as páginas da home.",
            defaultSpeed = 1f
        )
    )

    /** Lista embutida (sempre disponível, mesmo sem nenhum MP4 no APK). */
    fun builtins(): List<Wallpaper> = builtin

    /**
     * Catálogo completo = embutidos + MP4 extras encontrados em assets/wallpapers.
     * Um arquivo já referenciado por um item embutido não é duplicado.
     */
    fun all(context: Context): List<Wallpaper> {
        val known = builtin.mapNotNull { it.asset?.substringAfterLast('/') }.toMutableSet()
        val extras = mutableListOf<Wallpaper>()
        try {
            val files = context.assets.list(VIDEO_DIR) ?: emptyArray()
            files.filter { it.endsWith(".mp4", ignoreCase = true) }.sorted().forEach { file ->
                if (known.add(file)) {
                    extras += Wallpaper(
                        id = "asset_" + file.substringBeforeLast('.'),
                        title = file.substringBeforeLast('.').replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                        origin = Origin.OXYGENOS,
                        kind = SourceKind.VIDEO,
                        asset = "$VIDEO_DIR/$file",
                        shader = null,
                        palette = listOf(0xFF0B0A0F, 0xFF5E5CE6, 0xFFFF3DCB, 0xFFFFB020),
                        description = "Vídeo local adicionado em assets/$VIDEO_DIR/$file."
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível listar assets/$VIDEO_DIR", t)
        }
        return builtin + extras
    }

    fun byId(context: Context, id: String?): Wallpaper {
        val list = all(context)
        return list.firstOrNull { it.id == id } ?: list.first()
    }
}
