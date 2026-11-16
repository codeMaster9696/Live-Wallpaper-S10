package com.hub.livewallpaper.render

import com.hub.livewallpaper.data.ScaleMode
import kotlin.math.max
import kotlin.math.min

/**
 * =====================================================================
 *  A MATEMÁTICA DO "SEM BORDAS PRETAS"  (CENTER_CROP)
 * =====================================================================
 *
 * Problema real no Galaxy S10:
 *   superfície do wallpaper .... 1440 x 3040 px  (19:9 -> 0,4737)
 *   vídeo típico ............... 1920 x 1080 px  (16:9 -> 1,7778)
 *
 * Se você simplesmente entregar o Surface para o MediaPlayer, o compositor
 * faz CENTER_INSIDE: escala por min(1440/1920, 3040/1080) = 0,75 e sobram
 * (3040 - 810) / 2 = 1115 px de PRETO em cima e embaixo. É o letterboxing.
 *
 * Solução: escalar por max() e cortar o que passa da tela.
 *   escala = max(1440/1920, 3040/1080) = max(0,75 ; 2,8148) = 2,8148
 *   quadro projetado = 5404,4 x 3040 px
 *   corte horizontal = (5404,4 - 1440) / 2 = 1982,2 px de cada lado
 *   corte vertical   = 0 px            <- nenhuma borda, em nenhum eixo
 *
 * Em espaço de textura (UV, 0..1) isso vira um simples zoom no amostrador:
 *   uvScaleX = 1440 / 5404,4 = 0,26645
 *   uvScaleY = 3040 / 3040   = 1,0
 *   uv' = (uv - 0,5) * uvScale + 0,5
 *
 * Este arquivo é Kotlin PURO (sem imports de android.*) de propósito:
 * roda em teste JVM comum (src/test) e dá para conferir os números acima
 * sem precisar de um aparelho.
 */
object ScaleMath {

    data class Result(
        /** Fator aplicado ao mídia. */
        val scale: Float,
        /** Tamanho do quadro do mídia depois de escalado, em pixels de tela. */
        val drawW: Float,
        val drawH: Float,
        /** Pixels de tela cortados por lado (0 quando não há corte). */
        val cropLeftPx: Float,
        val cropTopPx: Float,
        /** Pixels de tela vazios por lado (0 no modo FILL). */
        val letterboxXpx: Float,
        val letterboxYpx: Float,
        /** Zoom no amostrador: < 1 corta, > 1 revela área fora do quadro. */
        val uvScaleX: Float,
        val uvScaleY: Float,
        /** Região do mídia original que continua visível. */
        val visibleSourceW: Float,
        val visibleSourceH: Float,
        /** Fração da tela efetivamente coberta pelo mídia (1.0 = sem bordas). */
        val coverage: Float
    ) {
        val hasLetterbox: Boolean get() = letterboxXpx > 0.5f || letterboxYpx > 0.5f

        /** Deslocamento UV máximo disponível antes de expor a borda do mídia. */
        val uvSlackX: Float get() = max(0f, (1f - uvScaleX) * 0.5f)
        val uvSlackY: Float get() = max(0f, (1f - uvScaleY) * 0.5f)
    }

    /**
     * @param surfaceW largura real da superfície entregue por onSurfaceChanged
     * @param surfaceH altura real da superfície
     * @param mediaW   largura do vídeo (ou 1 no modo procedural)
     * @param mediaH   altura do vídeo
     * @param mode     FILL (center-crop) ou FIT (center-inside)
     * @param overscan zoom extra de segurança (1.0 = nenhum). Só age no FILL:
     *                 útil porque a One UI aplica um leve zoom ao trocar de
     *                 página e ao abrir a gaveta de apps.
     */
    fun compute(
        surfaceW: Int,
        surfaceH: Int,
        mediaW: Int,
        mediaH: Int,
        mode: ScaleMode = ScaleMode.FILL,
        overscan: Float = 1f
    ): Result {
        val sw = max(1, surfaceW).toFloat()
        val sh = max(1, surfaceH).toFloat()
        val mw = max(1, mediaW).toFloat()
        val mh = max(1, mediaH).toFloat()
        val zoom = if (mode == ScaleMode.FILL) max(1f, overscan) else 1f

        val fitScale = min(sw / mw, sh / mh)
        val fillScale = max(sw / mw, sh / mh)
        val scale = (if (mode == ScaleMode.FILL) fillScale else fitScale) * zoom

        val drawW = mw * scale
        val drawH = mh * scale

        val cropX = max(0f, (drawW - sw) * 0.5f)
        val cropY = max(0f, (drawH - sh) * 0.5f)
        val boxX = max(0f, (sw - drawW) * 0.5f)
        val boxY = max(0f, (sh - drawH) * 0.5f)

        val uvX = sw / drawW
        val uvY = sh / drawH

        val coveredW = min(drawW, sw)
        val coveredH = min(drawH, sh)

        return Result(
            scale = scale,
            drawW = drawW,
            drawH = drawH,
            cropLeftPx = cropX,
            cropTopPx = cropY,
            letterboxXpx = boxX,
            letterboxYpx = boxY,
            uvScaleX = uvX,
            uvScaleY = uvY,
            visibleSourceW = coveredW / scale,
            visibleSourceH = coveredH / scale,
            coverage = (coveredW * coveredH) / (sw * sh)
        )
    }

    /**
     * Paralaxe entre páginas da tela inicial.
     *
     * onOffsetsChanged() devolve xOffset em 0..1. Convertemos para -1..1 e
     * usamos APENAS a folga que o center-crop criou — assim o wallpaper
     * "anda" com o swipe sem nunca revelar a borda do vídeo.
     */
    fun parallaxUvOffset(result: Result, xOffset: Float): Float {
        val normalized = ((xOffset - 0.5f) * 2f).coerceIn(-1f, 1f)
        return -normalized * result.uvSlackX
    }

    /** String de diagnóstico exibida na tela de ajustes e no logcat. */
    fun describe(surfaceW: Int, surfaceH: Int, mediaW: Int, mediaH: Int, r: Result): String =
        "superfície ${surfaceW}x${surfaceH} · mídia ${mediaW}x${mediaH} · " +
            "escala ${"%.4f".format(r.scale)} · corte ${"%.0f".format(r.cropLeftPx)}px/" +
            "${"%.0f".format(r.cropTopPx)}px · bordas ${"%.0f".format(r.letterboxXpx)}/" +
            "${"%.0f".format(r.letterboxYpx)}px · cobertura ${"%.1f".format(r.coverage * 100)}%"
}
