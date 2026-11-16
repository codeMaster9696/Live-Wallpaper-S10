package com.hub.livewallpaper.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Programa GLES 2.0 compilado + cache de localizações de uniforms/attribs. */
class GLProgram(vertexSrc: String, fragmentSrc: String) {

    val handle: Int
    private val uniforms = HashMap<String, Int>()
    private val attribs = HashMap<String, Int>()

    init {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        handle = GLES20.glCreateProgram()
        GLES20.glAttachShader(handle, vs)
        GLES20.glAttachShader(handle, fs)
        GLES20.glLinkProgram(handle)
        val status = IntArray(1)
        GLES20.glGetProgramiv(handle, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(handle)
            GLES20.glDeleteProgram(handle)
            throw IllegalStateException("Link do programa falhou: $log")
        }
        // Shaders já linkados podem ser liberados: economiza memória do driver.
        GLES20.glDetachShader(handle, vs)
        GLES20.glDetachShader(handle, fs)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
    }

    fun use() = GLES20.glUseProgram(handle)

    fun loc(name: String): Int = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(handle, name) }
    fun attr(name: String): Int = attribs.getOrPut(name) { GLES20.glGetAttribLocation(handle, name) }

    fun f(name: String, v: Float) { val l = loc(name); if (l >= 0) GLES20.glUniform1f(l, v) }
    fun f2(name: String, a: Float, b: Float) { val l = loc(name); if (l >= 0) GLES20.glUniform2f(l, a, b) }
    fun i(name: String, v: Int) { val l = loc(name); if (l >= 0) GLES20.glUniform1i(l, v) }
    fun mat4(name: String, m: FloatArray) { val l = loc(name); if (l >= 0) GLES20.glUniformMatrix4fv(l, 1, false, m, 0) }

    fun delete() { if (handle != 0) GLES20.glDeleteProgram(handle) }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            Log.e("GLProgram", "Fonte com erro:\n${src.lineSequence().take(60).joinToString("\n")}")
            throw IllegalStateException("Compile falhou: $log")
        }
        return shader
    }
}

/**
 * Quad de tela cheia (triangle strip) com posição + UV.
 * Convenção: vUv = (0,0) no canto INFERIOR esquerdo — a mesma do WebGL,
 * o que permite reaproveitar os shaders 1:1 entre o app e a prévia web.
 */
class FullscreenQuad {

    private val buffer: FloatBuffer = ByteBuffer
        .allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            //  x     y     u    v
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                -1f,  1f, 0f, 1f,
                 1f,  1f, 1f, 1f
            ))
            position(0)
        }

    fun draw(program: GLProgram) {
        val aPos = program.attr("aPos")
        val aUv = program.attr("aUv")
        buffer.position(0)
        if (aPos >= 0) {
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, buffer)
        }
        buffer.position(2)
        if (aUv >= 0) {
            GLES20.glEnableVertexAttribArray(aUv)
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, buffer)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (aPos >= 0) GLES20.glDisableVertexAttribArray(aPos)
        if (aUv >= 0) GLES20.glDisableVertexAttribArray(aUv)
    }
}

/** Textura externa (GL_TEXTURE_EXTERNAL_OES): destino zero-copy do MediaPlayer. */
object ExternalTexture {

    fun create(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    fun delete(id: Int) { if (id != 0) GLES20.glDeleteTextures(1, intArrayOf(id), 0) }
}

/** FBO usado para renderizar abaixo da resolução nativa (preferência "Qualidade"). */
class RenderTarget(var width: Int, var height: Int) {

    var texture: Int = 0; private set
    var framebuffer: Int = 0; private set

    fun ensure(w: Int, h: Int) {
        val tw = maxOf(2, w)
        val th = maxOf(2, h)
        if (texture != 0 && tw == width && th == height) return
        destroy()
        width = tw; height = th
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, tw, th, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val fb = IntArray(1)
        GLES20.glGenFramebuffers(1, fb, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0)
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteTextures(1, tex, 0)
            GLES20.glDeleteFramebuffers(1, fb, 0)
            width = 0; height = 0
            return // renderer cai para resolução nativa
        }
        texture = tex[0]
        framebuffer = fb[0]
    }

    val isReady: Boolean get() = framebuffer != 0

    fun destroy() {
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        texture = 0; framebuffer = 0; width = 0; height = 0
    }
}
