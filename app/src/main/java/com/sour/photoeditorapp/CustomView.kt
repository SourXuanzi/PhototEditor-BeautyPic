package com.sour.photoeditorapp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var bitmap: Bitmap? = null
    private var bitmapShader: BitmapShader? = null
    private var viewWidth = 0
    private var viewHeight = 0

    // 扫光动画
    private var scanOffset = 0f
    private lateinit var scanAnimator: ValueAnimator

    // 渐变效果
    private var gradientRotation = 0f
    private lateinit var gradientAnimator: ValueAnimator

    init {
        initView()
    }

    private fun initView() {
        // 从drawable加载图片 - 修复R引用问题
        try {
            val drawableId = context.resources.getIdentifier("custompic", "drawable", context.packageName)
            bitmap = BitmapFactory.decodeResource(context.resources, drawableId)
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果图片加载失败，创建一个默认的Bitmap
            bitmap = createDefaultBitmap()
        }
        setupAnimations()
    }

    private fun createDefaultBitmap(): Bitmap {
        val width = 400
        val height = 300
        val defaultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(defaultBitmap)

        val tempPaint = Paint().apply {
            color = Color.BLUE
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tempPaint)

        tempPaint.apply {
            color = Color.YELLOW
            textSize = 60f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("BeautyPic", width / 2f, height / 2f, tempPaint)

        return defaultBitmap
    }

    private fun setupAnimations() {
        // 扫光动画
        scanAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                scanOffset = animation.animatedValue as Float
                invalidate()
            }
        }

        // 渐变旋转动画
        gradientAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                gradientRotation = animation.animatedValue as Float
                invalidate()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h

        // 设置BitmapShader
        bitmap?.let { bmp ->
            bitmapShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

            // 计算缩放比例，使图片适应View大小
            val scale: Float
            val dx: Float
            val dy: Float

            if (bmp.width * viewHeight > bmp.height * viewWidth) {
                scale = viewHeight.toFloat() / bmp.height.toFloat()
                dx = (viewWidth - bmp.width * scale) * 0.5f
                dy = 0f
            } else {
                scale = viewWidth.toFloat() / bmp.width.toFloat()
                dx = 0f
                dy = (viewHeight - bmp.height * scale) * 0.5f
            }

            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)
            bitmapShader?.setLocalMatrix(matrix)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制背景
        drawBackground(canvas)

        // 绘制图片
        drawImage(canvas)

        // 绘制扫光效果
        drawScanEffect(canvas)

        // 绘制渐变边框
        drawGradientBorder(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat(), paint)
    }

    private fun drawImage(canvas: Canvas) {
        bitmapShader?.let { shader ->
            paint.shader = shader
            // 绘制圆角矩形图片
            val rect = RectF(20f, 20f, viewWidth - 20f, viewHeight - 20f)
            canvas.drawRoundRect(rect, 16f, 16f, paint)
            paint.shader = null
        }
    }

    private fun drawScanEffect(canvas: Canvas) {
        // 创建扫光渐变
        val scanWidth = viewWidth * 0.3f
        val scanX = -scanWidth + scanOffset * (viewWidth + scanWidth)

        val scanGradient = LinearGradient(
            scanX, 0f, scanX + scanWidth, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(100, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        paint.shader = scanGradient
        val rect = RectF(20f, 20f, viewWidth - 20f, viewHeight - 20f)
        canvas.drawRoundRect(rect, 16f, 16f, paint)
        paint.shader = null
    }

    private fun drawGradientBorder(canvas: Canvas) {
        // 创建旋转渐变边框
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        val sweepGradient = SweepGradient(
            centerX, centerY,
            intArrayOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED
            ),
            null
        )

        // 应用旋转矩阵
        val gradientMatrix = Matrix()
        gradientMatrix.postRotate(gradientRotation, centerX, centerY)
        sweepGradient.setLocalMatrix(gradientMatrix)

        paint.shader = sweepGradient
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f

        val borderRect = RectF(16f, 16f, viewWidth - 16f, viewHeight - 16f)
        canvas.drawRoundRect(borderRect, 20f, 20f, paint)

        // 重置paint
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scanAnimator.start()
        gradientAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scanAnimator.cancel()
        gradientAnimator.cancel()
    }
}