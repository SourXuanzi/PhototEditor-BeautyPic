package com.sour.photoeditorapp

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class ImageGLSurfaceView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    private val renderer: ImageGLRenderer

    init {
        // 创建 OpenGL ES 2.0 context
        setEGLContextClientVersion(2)

        // 设置渲染器
        renderer = ImageGLRenderer(context)
        setRenderer(renderer)

        // 设置为按需渲染模式（性能更好）
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    fun setImageBitmap(bitmap: Bitmap) {
        renderer.setBitmap(bitmap)
        requestRender() // 这里调用 requestRender()
    }

    fun cleanup() {
        renderer.cleanup()
    }
}