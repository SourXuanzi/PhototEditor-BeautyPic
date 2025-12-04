package com.sour.photoeditorapp

import android.graphics.*
import android.util.Log

object ImageEditUtils {

    // 旋转图片
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // 缩放图片
    fun scaleBitmap(bitmap: Bitmap, scale: Float): Bitmap {
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // 裁剪图片（简单居中裁剪）- 修复版本
    fun cropBitmap(bitmap: Bitmap, ratio: Float): Bitmap {
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        var cropWidth = bitmap.width
        var cropHeight = bitmap.height

        if (bitmapRatio > ratio) {
            // 图片比目标比例宽，裁剪宽度
            cropWidth = (bitmap.height * ratio).toInt()
            cropHeight = bitmap.height
        } else {
            // 图片比目标比例高，裁剪高度
            cropHeight = (bitmap.width / ratio).toInt()
            cropWidth = bitmap.width
        }

        // 确保裁剪尺寸有效
        if (cropWidth <= 0) cropWidth = 1
        if (cropHeight <= 0) cropHeight = 1

        // 确保不超过原图尺寸
        cropWidth = cropWidth.coerceAtMost(bitmap.width)
        cropHeight = cropHeight.coerceAtMost(bitmap.height)

        val x = (bitmap.width - cropWidth) / 2
        val y = (bitmap.height - cropHeight) / 2

        // 确保起始位置有效
        val startX = x.coerceIn(0, bitmap.width - cropWidth)
        val startY = y.coerceIn(0, bitmap.height - cropHeight)

        Log.d("ImageEditUtils", "裁剪: 原图${bitmap.width}x${bitmap.height}, 目标${cropWidth}x${cropHeight}, 位置($startX,$startY)")

        return if (cropWidth > 0 && cropHeight > 0 &&
            startX + cropWidth <= bitmap.width &&
            startY + cropHeight <= bitmap.height) {
            Bitmap.createBitmap(bitmap, startX, startY, cropWidth, cropHeight)
        } else {
            Log.e("ImageEditUtils", "裁剪参数无效，返回原图")
            bitmap
        }
    }

    // 调整亮度 - 修复版本
    fun adjustBrightness(bitmap: Bitmap, brightness: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        // 亮度范围改为 -1.0 到 1.0，效果更明显
        val adjustedBrightness = brightness.coerceIn(-1.0f, 1.0f)

        // 创建亮度调整矩阵（RGB分别调整，alpha不变）
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, adjustedBrightness * 255,
            0f, 1f, 0f, 0f, adjustedBrightness * 255,
            0f, 0f, 1f, 0f, adjustedBrightness * 255,
            0f, 0f, 0f, 1f, 0f
        )

        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        Log.d("ImageEditUtils", "亮度调整: $adjustedBrightness")
        return result
    }

    // 调整对比度 - 修复版本
    fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        // 对比度范围：0.0 到 4.0，1.0 表示无变化
        val adjustedContrast = contrast.coerceIn(0.0f, 4.0f)

        // 创建对比度调整矩阵
        val scale = adjustedContrast
        val translate = (1.0f - adjustedContrast) * 0.5f * 255

        val matrix = floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )

        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        Log.d("ImageEditUtils", "对比度调整: $adjustedContrast, scale: $scale, translate: $translate")
        return result
    }

    // 应用黑白滤镜
    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        val matrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    // 应用复古滤镜 - 增强效果
    fun applyVintage(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        // 创建复古效果矩阵：降低饱和度，添加暖色调
        val matrix = ColorMatrix().apply {
            setSaturation(0.7f) // 降低饱和度
            val vintageMatrix = floatArrayOf(
                1.2f, 0.0f, 0.0f, 0.0f, 30.0f,
                0.0f, 1.0f, 0.0f, 0.0f, 20.0f,
                0.0f, 0.0f, 0.8f, 0.0f, 20.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            )
            postConcat(ColorMatrix(vintageMatrix))
        }

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    // 应用冷色调滤镜
    fun applyCoolTone(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        // 创建冷色调矩阵 - 增强效果
        val matrix = ColorMatrix(floatArrayOf(
            0.8f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.9f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.2f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        ))

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    // 调试方法：显示Bitmap信息
    fun logBitmapInfo(bitmap: Bitmap, tag: String) {
        Log.d("ImageEditUtils", "$tag: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
    }
}