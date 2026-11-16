package com.hub.livewallpaper.render

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface

/**
 * Contexto EGL mínimo e completo para GLES 2.0.
 *
 * Por que EGL "na mão" e não GLSurfaceView?
 * O WallpaperService entrega um SurfaceHolder — não uma View. GLSurfaceView
 * não pode ser usado dentro de um Engine. Criamos o contexto EGL diretamente
 * sobre o Surface do holder, em uma thread própria, e controlamos nós mesmos
 * o ritmo dos frames (Choreographer + limite de FPS).
 *
 * Todo método aqui deve ser chamado SEMPRE da mesma thread (a RenderThread).
 */
class EglCore {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null

    val isValid: Boolean get() = display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay falhou" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) {
            "eglInitialize falhou: 0x${Integer.toHexString(EGL14.eglGetError())}"
        }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            // Sem depth/stencil: economiza banda de memória em tela 1440x3040.
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0) {
            "eglChooseConfig não encontrou config RGBA8888"
        }
        config = configs[0]

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(context != null && context != EGL14.EGL_NO_CONTEXT) {
            "eglCreateContext falhou: 0x${Integer.toHexString(EGL14.eglGetError())}"
        }
        Log.i(TAG, "EGL ${version[0]}.${version[1]} pronto")
    }

    /** Cria a janela EGL sobre o Surface do WallpaperService (ou de um SurfaceView de prévia). */
    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        val s = EGL14.eglCreateWindowSurface(display, config, surface, attribs, 0)
        check(s != null && s != EGL14.EGL_NO_SURFACE) {
            "eglCreateWindowSurface falhou: 0x${Integer.toHexString(EGL14.eglGetError())}"
        }
        return s
    }

    fun makeCurrent(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
            "eglMakeCurrent falhou: 0x${Integer.toHexString(EGL14.eglGetError())}"
        }
    }

    fun swap(surface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, surface)

    /**
     * Timestamp de apresentação. Ajuda o compositor da One UI a alinhar o
     * frame ao vsync quando estamos abaixo de 60 fps.
     */
    fun setPresentationTime(surface: EGLSurface, nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, surface, nanos)
    }

    fun destroySurface(surface: EGLSurface?) {
        if (surface != null && surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
        }
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        config = null
    }

    private companion object { const val TAG = "EglCore" }
}
