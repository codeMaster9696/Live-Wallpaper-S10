package com.hub.livewallpaper.data

/**
 * Sistema de origem da animação. Usado para o "chip" da galeria e para
 * tingir a prévia estática quando o vídeo ainda não foi decodificado.
 */
enum class Origin(val label: String, val accent: Int) {
    OXYGENOS("OxygenOS", 0xFFEB0028.toInt()),
    IOS("iOS", 0xFF5E5CE6.toInt()),
    COLOROS("ColorOS", 0xFFFF3DCB.toInt())
}

/**
 * VIDEO      -> MP4 em assets/wallpapers, decodificado por MediaPlayer.
 * PROCEDURAL -> fragment shader GLSL em assets/shaders, sem arquivo de vídeo.
 *
 * Os dois caminhos passam pelo MESMO renderizador EGL/GLES e pela MESMA
 * matemática de center-crop, então o comportamento de escala é idêntico.
 */
enum class SourceKind { VIDEO, PROCEDURAL }

data class Wallpaper(
    val id: String,
    val title: String,
    val origin: Origin,
    val kind: SourceKind,
    /** Caminho relativo dentro de assets/ (ex.: "wallpapers/oxygen_ribbon.mp4"). */
    val asset: String? = null,
    /** Nome do shader em assets/shaders/<shader>.frag (modo procedural). */
    val shader: String? = null,
    /** Paleta usada nas miniaturas, no fallback Canvas e nos detalhes da UI. */
    val palette: List<Int>,
    val description: String,
    val defaultSpeed: Float = 1f,
    /** Rotação intrínseca do mídia em graus (MediaPlayer expõe via METADATA_KEY_VIDEO_ROTATION). */
    val rotationHint: Int = 0
) {
    val originLabel: String get() = "${origin.label} · $title"
    val isVideo: Boolean get() = kind == SourceKind.VIDEO && asset != null
}
