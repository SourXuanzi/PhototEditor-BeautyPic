package com.sour.photoeditorapp

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ImageGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var bitmap: Bitmap? = null
    private var textureId = 0
    private var textureNeedsUpdate = false

    // 使用模型-视图-投影矩阵
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private final val mvpMatrix = FloatArray(16)

    // 视图和图片尺寸
    private var viewWidth = 0
    private var viewHeight = 0
    private var bitmapWidth = 0
    private var bitmapHeight = 0

    private var programHandle = 0
    private var positionHandle = 0
    private var textureCoordHandle = 0
    private var textureHandle = 0
    private var mvpMatrixHandle = 0

    // 顶点坐标 (x, y, z) - 使用单位正方形
    private val baseVertices = floatArrayOf(
        -1.0f, -1.0f, 0.0f,  // 左下
        1.0f, -1.0f, 0.0f,   // 右下
        -1.0f, 1.0f, 0.0f,   // 左上
        1.0f, 1.0f, 0.0f     // 右上
    )

    // 用于存储调整后的顶点坐标
    private val vertices = FloatArray(baseVertices.size)

    // 纹理坐标 (s, t) - 保持不变
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f,  // 左下
        1.0f, 1.0f,  // 右下
        0.0f, 0.0f,  // 左上
        1.0f, 0.0f   // 右上
    )

    fun setBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        this.bitmapWidth = bitmap.width
        this.bitmapHeight = bitmap.height
        textureNeedsUpdate = true

        Log.d("ImageGLRenderer", "设置图片: ${bitmapWidth}x${bitmapHeight}")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景色为黑色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 启用透明混合
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 初始化矩阵
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(mvpMatrix, 0)

        // 编译着色器
        compileShaders()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewWidth = width
        viewHeight = height

        Log.d("ImageGLRenderer", "表面改变: ${viewWidth}x${viewHeight}")

        // 设置固定的正交投影矩阵，覆盖整个视图
        val aspect = width.toFloat() / height.toFloat()
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
    }

    private fun updateVertices() {
        if (viewWidth == 0 || viewHeight == 0 || bitmapWidth == 0 || bitmapHeight == 0) {
            System.arraycopy(baseVertices, 0, vertices, 0, baseVertices.size)
            return
        }

        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val imageAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()

        Log.d("ImageGLRenderer", "更新顶点: 视图比例=$viewAspect, 图片比例=$imageAspect")

        // 计算缩放比例，保持图片完整显示
        val scaleX: Float
        val scaleY: Float

        if (imageAspect > viewAspect) {
            // 图片比视图宽，按宽度适应
            scaleX = 1.0f
            scaleY = viewAspect / imageAspect
            Log.d("ImageGLRenderer", "按宽度适应: scaleY=$scaleY")
        } else {
            // 图片比视图高，按高度适应
            scaleX = imageAspect / viewAspect
            scaleY = 1.0f
            Log.d("ImageGLRenderer", "按高度适应: scaleX=$scaleX")
        }

        // 调整顶点坐标
        for (i in 0 until 4) {
            vertices[i * 3] = baseVertices[i * 3] * scaleX     // x
            vertices[i * 3 + 1] = baseVertices[i * 3 + 1] * scaleY // y
            vertices[i * 3 + 2] = baseVertices[i * 3 + 2]       // z
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        // 清除为黑色背景
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        bitmap?.let { bmp ->
            // 如果纹理不存在或者需要更新，重新加载纹理
            if (textureId == 0 || textureNeedsUpdate) {
                if (textureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                }
                textureId = loadTexture(bmp)
                textureNeedsUpdate = false

                // 更新顶点坐标
                updateVertices()
            }

            // 更新顶点坐标（如果视图尺寸改变）
            updateVertices()

            // 绑定纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            // 重置模型矩阵（不进行额外的变换）
            Matrix.setIdentityM(modelMatrix, 0)

            // 计算 MVP 矩阵
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

            // 使用程序
            GLES20.glUseProgram(programHandle)

            // 传递 MVP 矩阵
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            // 传递顶点坐标（使用调整后的顶点）
            val vertexBuffer = floatArrayToBuffer(vertices)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

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
        // 在清除纹理前，确保我们有一个有效的OpenGL上下文
        if (textureId != 0) {
            // 在Android上，我们需要确保在OpenGL线程中执行删除
            val texId = textureId
            GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
            textureId = 0
            Log.d("ImageGLRenderer", "清理纹理: $texId")
        }
    }
}