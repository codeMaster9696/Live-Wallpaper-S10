package com.hub.livewallpaper.data

/**
 * Modo de escala do mídia sobre a superfície do wallpaper.
 *
 * FILL = CENTER_CROP  -> escala por max(), cobre 100% da tela, corta o excesso.
 * FIT  = CENTER_INSIDE-> escala por min(), mostra o quadro inteiro, cria bordas.
 *
 * FILL é o padrão e o que resolve o requisito "sem letterboxing" no S10.
 */
enum class ScaleMode { FILL, FIT }

/**
 * Snapshot imutável de tudo que afeta um frame. O Engine recebe um objeto
 * destes a cada mudança de preferência — nunca lê SharedPreferences dentro
 * do loop de renderização (isso causaria I/O por frame).
 */
data class RenderConfig(
    val wallpaperId: String = "oxygen_ribbon",
    val speed: Float = 1f,
    val scaleMode: ScaleMode = ScaleMode.FILL,
    val overscan: Float = 1.0f,
    val brightness: Float = 1.0f,
    val saturation: Float = 1.0f,
    val grain: Float = 0.06f,
    /** Escala do framebuffer interno: 1.0 = nativo, 0.75 = equilibrado, 0.5 = bateria. */
    val quality: Float = 0.75f,
    val fpsCap: Int = 60,
    val muteVideo: Boolean = true,
    val pauseOnBatterySaver: Boolean = true,
    /** Deslocamento normalizado das páginas da home (-1..1), vindo de onOffsetsChanged. */
    val parallax: Float = 0f
) {
    companion object {
        val DEFAULT = RenderConfig()
        const val MIN_SPEED = 0.15f
        const val MAX_SPEED = 2.5f
    }
}
