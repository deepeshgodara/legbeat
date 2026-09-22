package com.legbeat.video

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * High-performance OpenGL ES 2.0 full-frame texture renderer.
 * Renders an Android Bitmap onto the MediaCodec input surface viewport via GPU hardware.
 */
class FullFrameTextureRenderer {

    companion object {
        private const val FLOAT_SIZE_BYTES = 4
        private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 4 * FLOAT_SIZE_BYTES
        private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 2

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }

    // Coordinates: X, Y, U, V
    // Mapped so Bitmap (0,0 top-left) renders right-side up in OpenGL clip space
    private val triangleVerticesData = floatArrayOf(
        -1.0f, -1.0f,  0.0f, 1.0f, // bottom-left
         1.0f, -1.0f,  1.0f, 1.0f, // bottom-right
        -1.0f,  1.0f,  0.0f, 0.0f, // top-left
         1.0f,  1.0f,  1.0f, 0.0f  // top-right
    )

    private val triangleVertices: FloatBuffer = ByteBuffer.allocateDirect(triangleVerticesData.size * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(triangleVerticesData)
            position(0)
        }

    private var program = 0
    private var aPositionHandle = 0
    private var aTextureCoordHandle = 0
    private var sTextureHandle = 0
    private var textureId = 0
    private var textureAllocated = false

    fun init() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val error = GLES20.glGetProgramInfoLog(it)
                GLES20.glDeleteProgram(it)
                throw RuntimeException("Could not link program: $error")
            }
        }

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        sTextureHandle = GLES20.glGetUniformLocation(program, "sTexture")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    /**
     * Uploads the bitmap to the GPU texture and draws a full-screen quad to the current EGL surface.
     */
    fun drawFrame(bitmap: Bitmap, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        if (!textureAllocated) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            textureAllocated = true
        } else {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        }
        GLES20.glUniform1i(sTextureHandle, 0)

        triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            aPositionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            triangleVertices
        )
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            aTextureCoordHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            triangleVertices
        )
        GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTextureCoordHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    fun release() {
        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            textureId = 0
            textureAllocated = false
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader $shaderType: $error")
        }
        return shader
    }
}
