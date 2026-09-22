package com.legbeat.video

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

/**
 * Manages an EGL display, context, and window surface connected to a MediaCodec input surface.
 * Enables hardware-accelerated, zero-copy OpenGL rendering directly into the video encoder.
 */
class CodecInputSurface(private val surface: Surface) {

    companion object {
        private const val TAG = "CodecInputSurface"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        eglSetup()
    }

    private fun eglSetup() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("unable to initialize EGL14")
        }

        // Configure EGL for OpenGL ES 2.0 with Android recordable surface support
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        var success = EGL14.eglChooseConfig(
            eglDisplay, attribList, 0, configs, 0, configs.size,
            numConfigs, 0
        )

        if (!success || numConfigs[0] == 0) {
            // Fallback: choose config without EGL_RECORDABLE_ANDROID (for emulator / generic GPUs)
            val fallbackAttribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            success = EGL14.eglChooseConfig(
                eglDisplay, fallbackAttribList, 0, configs, 0, configs.size,
                numConfigs, 0
            )
            if (!success || numConfigs[0] == 0) {
                throw RuntimeException("unable to find suitable EGLConfig")
            }
        }

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            contextAttribs, 0
        )
        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("null EGL context")
        }

        val surfaceAttribs = intArrayOf(
            EGL14.EGL_NONE
        )
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, configs[0], surface,
            surfaceAttribs, 0
        )
        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("null EGL surface")
        }
    }

    /**
     * Makes this surface current for rendering on the calling thread.
     */
    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    /**
     * Sets the presentation timestamp for the next frame in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
    }

    /**
     * Submits the rendered frame to the MediaCodec input surface.
     */
    fun swapBuffers(): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    /**
     * Releases all EGL resources.
     */
    fun release() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
            }
            if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}
