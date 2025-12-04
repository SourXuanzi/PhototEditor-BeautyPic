package com.sour.photoeditorapp

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ImageGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var bitmap: Bitmap? = null
    private var textureId = 0
    private var textureNeedsUpdate = false // 添加一个标志来跟踪是否需要更新纹理
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var programHandle = 0
    private var positionHandle = 0
    private var textureCoordHandle = 0
    private var textureHandle = 0
    private var mvpMatrixHandle = 0

    // 顶点坐标 (x, y, z)
    private val vertices = floatArrayOf(
        -1.0f, -1.0f, 0.0f,  // 左下
        1.0f, -1.0f, 0.0f,   // 右下
        -1.0f, 1.0f, 0.0f,   // 左上
        1.0f, 1.0f, 0.0f     // 右上
    )

    // 纹理坐标 (s, t)
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f,  // 左下
        1.0f, 1.0f,  // 右下
        0.0f, 0.0f,  // 左上
        1.0f, 0.0f   // 右上
    )

    fun setBitmap(bitmap: Bitmap) {
        // 清除旧纹理
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }

        this.bitmap = bitmap
        textureNeedsUpdate = true // 标记需要更新纹理
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景色为黑色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 启用透明混合
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 编译着色器
        compileShaders()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        // 设置投影矩阵
        val ratio = width.toFloat() / height.toFloat()
        Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, -1f, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        bitmap?.let { bmp ->
            // 如果纹理不存在或者需要更新，重新加载纹理
            if (textureId == 0 || textureNeedsUpdate) {
                textureId = loadTexture(bmp)
                textureNeedsUpdate = false
            }

            // 绑定纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            // 重置视图矩阵
            Matrix.setIdentityM(viewMatrix, 0)

            // 计算 MVP 矩阵
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

            // 使用程序
            GLES20.glUseProgram(programHandle)

            // 传递 MVP 矩阵
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            // 传递顶点坐标
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, floatArrayToBuffer(vertices))

            // 传递纹理坐标
            GLES20.glEnableVertexAttribArray(textureCoordHandle)
            GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, floatArrayToBuffer(textureCoords))

            // 设置纹理单元
            GLES20.glUniform1i(textureHandle, 0)

            // 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // 禁用顶点数组
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(textureCoordHandle)
        }
    }

    private fun compileShaders() {
        // 顶点着色器
        val vertexShaderCode = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            uniform mat4 u_MVPMatrix;
            void main() {
                gl_Position = u_MVPMatrix * a_Position;
                v_TexCoord = a_TexCoord;
            }
        """.trimIndent()

        // 片段着色器
        val fragmentShaderCode = """
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform sampler2D u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programHandle = GLES20.glCreateProgram()
        GLES20.glAttachShader(programHandle, vertexShader)
        GLES20.glAttachShader(programHandle, fragmentShader)
        GLES20.glLinkProgram(programHandle)

        // 获取属性位置
        positionHandle = GLES20.glGetAttribLocation(programHandle, "a_Position")
        textureCoordHandle = GLES20.glGetAttribLocation(programHandle, "a_TexCoord")
        textureHandle = GLES20.glGetUniformLocation(programHandle, "u_Texture")
        mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVPMatrix")
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun loadTexture(bitmap: Bitmap): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

            // 设置纹理过滤
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            // 加载bitmap到纹理
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        }

        return textureHandle[0]
    }

    private fun floatArrayToBuffer(array: FloatArray): java.nio.FloatBuffer {
        val buffer = java.nio.ByteBuffer.allocateDirect(array.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(array)
        buffer.position(0)
        return buffer
    }

    fun cleanup() {
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }
}