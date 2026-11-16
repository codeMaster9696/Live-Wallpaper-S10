package com.hub.livewallpaper.render

import android.content.Context

/**
 * Carrega e monta os shaders a partir de assets/shaders/.
 *
 * Estrutura de montagem (idêntica à da prévia web):
 *   common.glsl  -> precision, uniforms, ruído/fbm, correção de cor
 *   <nome>.frag  -> apenas a função vec3 scene(vec2 p, vec2 uv, float t)
 *   main.glsl    -> main() que chama scene() e aplica o grading
 *
 * Vantagem: trocar um wallpaper procedural = soltar um .frag em assets.
 * Nenhum Kotlin precisa mudar.
 */
class ShaderLibrary(private val context: Context) {

    private val cache = HashMap<String, String>()

    private fun read(name: String): String = cache.getOrPut(name) {
        context.assets.open("shaders/$name").bufferedReader().use { it.readText() }
    }

    val vertex: String get() = read("vertex.vert")

    fun procedural(name: String): String =
        read("common.glsl") + "\n" + read("$name.frag") + "\n" + read("main.glsl")

    /** Fragment shader do caminho de vídeo (samplerExternalOES + crop UV). */
    fun video(): String = read("video.frag")

    /** Fragment shader do blit final (FBO -> tela). */
    fun blit(): String = read("blit.frag")

    companion object {
        /** Shader usado quando o vídeo falha e o wallpaper não tem shader próprio. */
        const val SAFE_FALLBACK = "ios_mesh"
    }
}
