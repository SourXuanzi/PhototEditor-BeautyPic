package com.sour.photoeditorapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import java.util.Random

class CustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bitmap: Bitmap? = null
    private val cornerRadius = 16f.dpToPx(context)

    // 扫光效果
    private var sweepPosition = 0f
    private val sweepAnimator: ValueAnimator
    private val sweepGradient: LinearGradient?
    private val sweepMatrix = Matrix()

    // 粒子效果
    private val particles = mutableListOf<Particle>()
    private var particleAnimator: ValueAnimator? = null
    private val random = Random() // 添加Random实例

    // 粒子类
    private inner class Particle(
        var x: Float, var y: Float,
        var radius: Float,
        var speedX: Float, var speedY: Float,
        var color: Int,
        var alpha: Int = 255
    )

    // 滤镜类型
    enum class FilterType {
        GLOW, RAINBOW, INVERT, BLUR, MULTICOLOR
    }
    private var currentFilter = FilterType.GLOW
    private var filterChangeTime = 0L

    init {
        // 初始化扫光动画
        sweepAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                sweepPosition = animation.animatedValue as Float
                invalidate()
            }
        }

        // 扫光渐变
        sweepGradient = LinearGradient(
            0f, 0f, 200f, 0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(150, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.2f, 0.5f, 0.8f),
            Shader.TileMode.CLAMP
        )

        // 加载默认图片
        loadDefaultImage()

        // 初始化粒子
        initParticles()
        startAnimations()
    }

    private fun loadDefaultImage() {
        try {
            // 从drawable资源加载图片
            val resources = context.resources
            val resourceId = resources.getIdentifier("custompic", "drawable", context.packageName)

            if (resourceId != 0) {
                // 解码图片，按视图尺寸缩放
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeResource(resources, resourceId, options)

                // 计算合适的缩放比例
                val width = options.outWidth
                val height = options.outHeight
                var inSampleSize = 1

                if (height > 400 || width > 400) {
                    val halfHeight = height / 2
                    val halfWidth = width / 2
                    while (halfHeight / inSampleSize >= 400 && halfWidth / inSampleSize >= 400) {
                        inSampleSize *= 2
                    }
                }

                // 实际解码图片
                options.inJustDecodeBounds = false
                options.inSampleSize = inSampleSize
                this.bitmap = BitmapFactory.decodeResource(resources, resourceId, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果加载失败，创建一个彩色渐变作为后备
            createFallbackBitmap()
        }
    }

    private fun createFallbackBitmap() {
        val fallbackBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fallbackBitmap)
        val shader = LinearGradient(
            0f, 0f, 400f, 400f,
            intArrayOf(Color.RED, Color.BLUE, Color.GREEN),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, 400f, 400f, paint)
        paint.shader = null
        this.bitmap = fallbackBitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制动态背景
        drawAnimatedBackground(canvas)

        // 自动切换滤镜效果（每3秒切换一次）
        val currentTime = System.currentTimeMillis()
        if (currentTime - filterChangeTime > 3000) {
            currentFilter = FilterType.values()[random.nextInt(FilterType.values().size)]
            filterChangeTime = currentTime
        }

        // 绘制图片特效
        bitmap?.let {
            drawBitmapWithEffect(canvas, it)
        }

        // 绘制扫光效果
        drawSweepEffect(canvas)

        // 绘制粒子
        drawParticles(canvas)
    }

    private fun drawAnimatedBackground(canvas: Canvas) {
        // 动态渐变背景
        val time = System.currentTimeMillis() % 5000 / 5000f
        val shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                interpolateColor(Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"), time),
                interpolateColor(Color.parseColor("#16213e"), Color.parseColor("#0f3460"), time),
                interpolateColor(Color.parseColor("#0f3460"), Color.parseColor("#1a1a2e"), time)
            ),
            null, Shader.TileMode.MIRROR
        )
        paint.shader = shader
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
        paint.shader = null
    }

    private fun drawBitmapWithEffect(canvas: Canvas, bitmap: Bitmap) {
        when (currentFilter) {
            FilterType.GLOW -> drawGlowEffect(canvas, bitmap)
            FilterType.RAINBOW -> drawRainbowEffect(canvas, bitmap)
            FilterType.INVERT -> drawInvertEffect(canvas, bitmap)
            FilterType.BLUR -> drawBlurEffect(canvas, bitmap)
            FilterType.MULTICOLOR -> drawMulticolorEffect(canvas, bitmap)
        }
    }

    private fun drawGlowEffect(canvas: Canvas, bitmap: Bitmap) {
        // 调整图片尺寸以适应视图
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        // 发光渐变
        val gradient = RadialGradient(
            width / 2f, height / 2f, Math.max(width, height) / 2f,
            intArrayOf(Color.YELLOW, Color.RED, Color.TRANSPARENT),
            floatArrayOf(0.1f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )

        val composeShader = ComposeShader(shader, gradient, PorterDuff.Mode.SCREEN)
        paint.shader = composeShader
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
    }

    private fun drawRainbowEffect(canvas: Canvas, bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        // 彩虹渐变
        val time = System.currentTimeMillis() % 3000 / 3000f
        val gradient = LinearGradient(
            0f, 0f, width * time, height * time,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE, Color.MAGENTA),
            null, Shader.TileMode.MIRROR
        )

        val composeShader = ComposeShader(shader, gradient, PorterDuff.Mode.OVERLAY)
        paint.shader = composeShader
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
    }

    private fun drawInvertEffect(canvas: Canvas, bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

        // 反色效果
        val colorMatrix = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
        paint.colorFilter = null
    }

    private fun drawBlurEffect(canvas: Canvas, bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

        // 简单模糊效果
        val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader

        paint.alpha = 80
        for (i in 0..8) {
            val offset = i * 1.5f
            canvas.drawRoundRect(
                offset, offset,
                width.toFloat() - offset, height.toFloat() - offset,
                cornerRadius, cornerRadius, paint
            )
        }
        paint.alpha = 255
    }

    private fun drawMulticolorEffect(canvas: Canvas, bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        // 多彩混合效果
        val time = System.currentTimeMillis() % 4000 / 4000f
        val gradient = LinearGradient(
            width * time, 0f, 0f, height * time,
            intArrayOf(
                Color.argb(150, 255, 0, 0),
                Color.argb(150, 0, 255, 0),
                Color.argb(150, 0, 0, 255)
            ),
            floatArrayOf(0.2f, 0.5f, 0.8f),
            Shader.TileMode.CLAMP
        )

        val composeShader = ComposeShader(shader, gradient, PorterDuff.Mode.LIGHTEN)
        paint.shader = composeShader
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
    }

    private fun drawSweepEffect(canvas: Canvas) {
        sweepGradient?.let {
            sweepMatrix.setTranslate(sweepPosition * width * 1.5f - width * 0.25f, 0f)
            it.setLocalMatrix(sweepMatrix)

            paint.shader = it
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
            paint.xfermode = null
        }
    }

    private fun initParticles() {
        particles.clear()
        for (i in 0 until 25) {
            particles.add(Particle(
                x = random.nextFloat() * width,
                y = random.nextFloat() * height,
                radius = getRandomFloat(3f, 12f), // 修复的随机数生成
                speedX = getRandomFloat(-1.5f, 1.5f), // 修复的随机数生成
                speedY = getRandomFloat(-1.5f, 1.5f), // 修复的随机数生成
                color = Color.argb(
                    getRandomInt(80, 180),
                    getRandomInt(50, 255),
                    getRandomInt(50, 255),
                    getRandomInt(50, 255)
                )
            ))
        }
    }

    // 生成指定范围内的随机浮点数
    private fun getRandomFloat(min: Float, max: Float): Float {
        return min + random.nextFloat() * (max - min)
    }

    // 生成指定范围内的随机整数
    private fun getRandomInt(min: Int, max: Int): Int {
        return random.nextInt(max - min + 1) + min
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            // 更新位置
            particle.x += particle.speedX
            particle.y += particle.speedY

            // 边界反弹
            if (particle.x < 0 || particle.x > width) particle.speedX *= -1
            if (particle.y < 0 || particle.y > height) particle.speedY *= -1

            // 绘制粒子
            paint.color = particle.color
            canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
        }
    }

    private fun startAnimations() {
        if (!sweepAnimator.isRunning) {
            sweepAnimator.start()
        }

        // 粒子动画
        particleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                invalidate() // 持续重绘
            }
            start()
        }

        filterChangeTime = System.currentTimeMillis()
    }

    private fun stopAnimations() {
        sweepAnimator.cancel()
        particleAnimator?.cancel()
    }

    // 颜色插值函数
    private fun interpolateColor(color1: Int, color2: Int, factor: Float): Int {
        val a = (Color.alpha(color1) + (Color.alpha(color2) - Color.alpha(color1)) * factor).toInt()
        val r = (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * factor).toInt()
        val g = (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * factor).toInt()
        val b = (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * factor).toInt()
        return Color.argb(a, r, g, b)
    }

    // 保留设置图片的方法以便后续扩展
    fun setImage(bitmap: Bitmap) {
        this.bitmap = bitmap
        invalidate()
    }

    // 清除图片
    fun clearImage() {
        loadDefaultImage() // 重新加载默认图片
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initParticles() // 尺寸变化时重新初始化粒子
    }
}

// 扩展函数：dp转px
fun Float.dpToPx(context: Context): Float {
    return this * context.resources.displayMetrics.density
}