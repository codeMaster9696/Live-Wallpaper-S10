package com.hub.livewallpaper

import com.hub.livewallpaper.data.ScaleMode
import com.hub.livewallpaper.render.ScaleMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes JVM (rodam em segundos, sem aparelho):
 *   ./gradlew :app:testDebugUnitTest
 *
 * Eles travam o requisito principal do projeto: no Galaxy S10 o modo
 * Preencher NÃO pode produzir bordas, em nenhuma orientação.
 */
class ScaleMathTest {

    private val S10_W = 1440
    private val S10_H = 3040

    @Test
    fun `video 16x9 em tela 19x9 preenche tudo e corta só nas laterais`() {
        val r = ScaleMath.compute(S10_W, S10_H, 1920, 1080, ScaleMode.FILL)
        assertEquals(2.8148f, r.scale, 0.001f)
        assertEquals(5404.44f, r.drawW, 0.5f)
        assertEquals(3040f, r.drawH, 0.5f)
        assertEquals(1982.2f, r.cropLeftPx, 0.5f)
        assertEquals(0f, r.cropTopPx, 0.01f)
        assertEquals(0f, r.letterboxXpx, 0.01f)
        assertEquals(0f, r.letterboxYpx, 0.01f)
        assertEquals(1f, r.coverage, 0.0001f)
        assertFalse(r.hasLetterbox)
        assertEquals(0.26645f, r.uvScaleX, 0.0005f)
        assertEquals(1f, r.uvScaleY, 0.0001f)
    }

    @Test
    fun `modo ajustar produz letterbox previsivel`() {
        val r = ScaleMath.compute(S10_W, S10_H, 1920, 1080, ScaleMode.FIT)
        assertEquals(0.75f, r.scale, 0.0001f)
        assertEquals(1115f, r.letterboxYpx, 0.5f)
        assertTrue(r.hasLetterbox)
        assertTrue(r.coverage < 0.3f)
    }

    @Test
    fun `video vertical 9x19 tambem preenche sem bordas`() {
        val r = ScaleMath.compute(S10_W, S10_H, 1080, 2280, ScaleMode.FILL)
        assertEquals(0f, r.letterboxXpx, 0.01f)
        assertEquals(0f, r.letterboxYpx, 0.01f)
        assertEquals(1f, r.coverage, 0.0001f)
    }

    @Test
    fun `paisagem nao quebra a cobertura`() {
        val r = ScaleMath.compute(S10_H, S10_W, 1920, 1080, ScaleMode.FILL)
        assertEquals(1f, r.coverage, 0.0001f)
        assertFalse(r.hasLetterbox)
    }

    @Test
    fun `overscan aumenta o zoom e nunca cria borda`() {
        val base = ScaleMath.compute(S10_W, S10_H, 1920, 1080, ScaleMode.FILL, 1f)
        val over = ScaleMath.compute(S10_W, S10_H, 1920, 1080, ScaleMode.FILL, 1.15f)
        assertTrue(over.scale > base.scale)
        assertTrue(over.uvScaleX < base.uvScaleX)
        assertEquals(1f, over.coverage, 0.0001f)
    }

    @Test
    fun `paralaxe usa apenas a folga do crop`() {
        val r = ScaleMath.compute(S10_W, S10_H, 1920, 1080, ScaleMode.FILL)
        val left = ScaleMath.parallaxUvOffset(r, 0f)
        val right = ScaleMath.parallaxUvOffset(r, 1f)
        assertEquals(r.uvSlackX, Math.abs(left), 0.0001f)
        assertEquals(r.uvSlackX, Math.abs(right), 0.0001f)
        // uv' mínimo continua >= 0 : nenhuma borda exposta durante o swipe
        assertTrue(0.5f - r.uvScaleX * 0.5f + left >= -0.0001f)
        assertTrue(0.5f + r.uvScaleX * 0.5f + right <= 1.0001f)
    }

    @Test
    fun `entradas degeneradas nao geram NaN`() {
        val r = ScaleMath.compute(0, 0, 0, 0, ScaleMode.FILL)
        assertFalse(r.scale.isNaN())
        assertFalse(r.uvScaleX.isNaN())
    }
}
