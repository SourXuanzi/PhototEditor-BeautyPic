package com.sour.photoeditorapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object VideoFilterHelper {

    enum class FilterType {
        NONE,
        GRAYSCALE,
        SEPIA,
        VINTAGE,
        BRIGHTNESS,
        CONTRAST,
        SATURATION,
        INVERT,
        PIXELATE,
        SKETCH,
        TOON
    }

    // 使用 Android 原生 ColorMatrix 实现滤镜（无需 GPUImage）
    suspend fun applyFilterToImage(
        context: Context,
        imageUri: Uri,
        filterType: FilterType
    ): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return@withContext null

            applyColorFilter(originalBitmap, filterType)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun applyColorFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)

        // 对于不需要特殊处理的滤镜，使用 ColorMatrix
        if (filterType != FilterType.PIXELATE && filterType != FilterType.SKETCH && filterType != FilterType.TOON) {
            val canvas = android.graphics.Canvas(result)
            val paint = android.graphics.Paint()

            val colorMatrix = when (filterType) {
                FilterType.GRAYSCALE -> {
                    android.graphics.ColorMatrix().apply {
                        setSaturation(0f)
                    }
                }
                FilterType.SEPIA -> {
                    android.graphics.ColorMatrix().apply {
                        set(floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                }
                FilterType.VINTAGE -> {
                    android.graphics.ColorMatrix().apply {
                        set(floatArrayOf(
                            0.5f, 0.3f, 0.1f, 0f, 50f,
                            0.2f, 0.6f, 0.1f, 0f, 50f,
                            0.1f, 0.1f, 0.7f, 0f, 50f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                }
                FilterType.BRIGHTNESS -> {
                    android.graphics.ColorMatrix().apply {
                        setScale(1.2f, 1.2f, 1.2f, 1f)
                    }
                }
                FilterType.CONTRAST -> {
                    android.graphics.ColorMatrix().apply {
                        val contrast = 1.5f
                        val scale = contrast
                        val translate = (-0.5f * contrast + 0.5f) * 255f
                        set(floatArrayOf(
                            scale, 0f, 0f, 0f, translate,
                            0f, scale, 0f, 0f, translate,
                            0f, 0f, scale, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                }
                FilterType.SATURATION -> {
                    android.graphics.ColorMatrix().apply {
                        setSaturation(2f)
                    }
                }
                FilterType.INVERT -> {
                    android.graphics.ColorMatrix().apply {
                        set(floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                }
                else -> android.graphics.ColorMatrix() // 无滤镜
            }

            val filter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            paint.colorFilter = filter
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
        } else {
            // 特殊处理的滤镜
            when (filterType) {
                FilterType.PIXELATE -> return createPixelatedBitmap(bitmap)
                FilterType.SKETCH -> return createSketchBitmap(bitmap)
                FilterType.TOON -> return createToonBitmap(bitmap)
                else -> {}
            }
        }

        return result
    }

    private fun createPixelatedBitmap(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val pixelSize = 10

        for (x in 0 until bitmap.width step pixelSize) {
            for (y in 0 until bitmap.height step pixelSize) {
                val color = bitmap.getPixel(x, y)

                for (px in x until minOf(x + pixelSize, bitmap.width)) {
                    for (py in y until minOf(y + pixelSize, bitmap.height)) {
                        result.setPixel(px, py, color)
                    }
                }
            }
        }

        return result
    }

    private fun createSketchBitmap(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint()

        // 转换为灰度
        val grayMatrix = android.graphics.ColorMatrix()
        grayMatrix.setSaturation(0f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(grayMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 边缘检测（简单实现）
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                val centerColor = Color.red(pixels[y * width + x])
                val leftColor = Color.red(pixels[y * width + (x - 1)])
                val topColor = Color.red(pixels[(y - 1) * width + x])

                val edge = kotlin.math.abs(centerColor - leftColor) + kotlin.math.abs(centerColor - topColor)
                val grayValue = (255 - edge.coerceIn(0, 255)).toInt()

                pixels[y * width + x] = Color.rgb(grayValue, grayValue, grayValue)
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun createToonBitmap(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint()

        // 降低色阶（减少颜色数量）
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setScale(1f, 0.8f, 0.8f, 1f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 增强边缘
        return result
    }

    // 保存滤镜处理后的图片
    suspend fun saveFilteredImage(
        context: Context,
        bitmap: Bitmap,
        filterType: FilterType
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val outputDir = File(context.getExternalFilesDir(null), "FilteredImages")
            if (!outputDir.exists()) outputDir.mkdirs()

            val fileName = "${filterType.name.lowercase()}_${System.currentTimeMillis()}.jpg"
            val outputFile = File(outputDir, fileName)

            val fos = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.close()

            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 获取所有可用滤镜
    fun getAvailableFilters(): List<Pair<FilterType, String>> {
        return listOf(
            Pair(FilterType.NONE, "无滤镜"),
            Pair(FilterType.GRAYSCALE, "黑白"),
            Pair(FilterType.SEPIA, "复古"),
            Pair(FilterType.VINTAGE, "老电影"),
            Pair(FilterType.BRIGHTNESS, "明亮"),
            Pair(FilterType.CONTRAST, "高对比度"),
            Pair(FilterType.SATURATION, "高饱和度"),
            Pair(FilterType.INVERT, "反色"),
            Pair(FilterType.PIXELATE, "像素化"),
            Pair(FilterType.SKETCH, "素描"),
            Pair(FilterType.TOON, "卡通")
        )
    }
}