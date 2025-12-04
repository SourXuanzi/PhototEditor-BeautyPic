package com.sour.photoeditorapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import android.content.ContentValues
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.IOException

class ImageEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var saveButton: ImageButton
    private lateinit var glSurfaceView: ImageGLSurfaceView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var editToolsLayout: LinearLayout

    private var originalBitmap: Bitmap? = null
    private var currentBitmap: Bitmap? = null
    private var mediaUri: String? = null
    private var mediaPath: String? = null

    // 编辑操作记录
    private val editHistory = mutableListOf<Bitmap>()
    private var currentEditIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("ImageEditor", "ImageEditorActivity创建")

        // 隐藏默认的 ActionBar
        supportActionBar?.hide()
        setContentView(R.layout.activity_image_editor)

        mediaUri = intent.getStringExtra("mediaUri")
        mediaPath = intent.getStringExtra("mediaPath")

        initViews()
        setupBackPressedHandler()
        loadImage()
        setupEditTools()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        saveButton = findViewById(R.id.saveButton)
        glSurfaceView = findViewById(R.id.glSurfaceView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        editToolsLayout = findViewById(R.id.editToolsLayout)

        // 完全自定义 Toolbar，不使用 ActionBar
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // 设置自定义标题
        val fileName = mediaPath?.substringAfterLast("/") ?: "未命名"
        toolbarTitle.text = "图片编辑 - $fileName"

        // 设置保存按钮点击事件
        saveButton.setOnClickListener {
            saveImage()
        }

        // 手动设置返回图标
        val upArrow = ContextCompat.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        upArrow?.setTint(ContextCompat.getColor(this, android.R.color.black))
        toolbar.navigationIcon = upArrow
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun loadImage() {
        if (mediaUri.isNullOrEmpty()) {
            Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri = Uri.parse(mediaUri)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                withContext(Dispatchers.Main) {
                    loadingProgressBar.visibility = View.GONE
                    if (bitmap != null) {
                        Log.d("ImageEditor", "图片加载成功: ${bitmap.width}x${bitmap.height}")

                        originalBitmap = bitmap
                        currentBitmap = bitmap
                        glSurfaceView.setImageBitmap(bitmap)
                        saveToHistory(bitmap)

                        // 测试编辑功能
                        testEditFunction()
                    } else {
                        Toast.makeText(this@ImageEditorActivity, "图片加载失败", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    loadingProgressBar.visibility = View.GONE
                    Toast.makeText(this@ImageEditorActivity, "图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun setupEditTools() {
        val tools = listOf(
            EditTool("旋转", android.R.drawable.ic_menu_rotate) { applyRotate() },
            EditTool("缩放", android.R.drawable.ic_menu_zoom) { showScaleDialog() },
            EditTool("裁剪", android.R.drawable.ic_menu_crop) { showCropDialog() },
            EditTool("亮度", android.R.drawable.ic_menu_edit) { showBrightnessDialog() },
            EditTool("对比度", android.R.drawable.ic_menu_agenda) { showContrastDialog() },
            EditTool("滤镜", android.R.drawable.ic_menu_gallery) { showFilterDialog() },
            EditTool("撤销", android.R.drawable.ic_menu_revert) { undoEdit() }
            // 移除了重置按钮
        )

        tools.forEach { tool ->
            val button = Button(this).apply {
                text = tool.name
                setCompoundDrawablesWithIntrinsicBounds(0, tool.iconRes, 0, 0)
                setOnClickListener { tool.action.invoke() }
                setPadding(32, 16, 32, 16)
                setBackgroundColor(ContextCompat.getColor(this@ImageEditorActivity, android.R.color.white))
                setTextColor(ContextCompat.getColor(this@ImageEditorActivity, android.R.color.black))
            }
            editToolsLayout.addView(button)
        }

        // 添加调试按钮（仅用于调试）
        val debugButton = Button(this).apply {
            text = "调试"
            setOnClickListener {
                showDebugInfo()
            }
            setPadding(32, 16, 32, 16)
            setBackgroundColor(ContextCompat.getColor(this@ImageEditorActivity, android.R.color.holo_green_light))
            setTextColor(ContextCompat.getColor(this@ImageEditorActivity, android.R.color.black))
        }
        editToolsLayout.addView(debugButton)
    }

    private fun showDebugInfo() {
        Log.d("ImageEditor", "=== 调试信息 ===")
        Log.d("ImageEditor", "原图: ${originalBitmap?.width}x${originalBitmap?.height}")
        Log.d("ImageEditor", "当前图: ${currentBitmap?.width}x${currentBitmap?.height}")
        Log.d("ImageEditor", "历史记录: ${editHistory.size} 条")
        Log.d("ImageEditor", "当前索引: $currentEditIndex")

        Toast.makeText(this, "调试信息已输出到日志", Toast.LENGTH_SHORT).show()
    }

    data class EditTool(val name: String, val iconRes: Int, val action: () -> Unit)

    // 编辑功能实现
    private fun applyRotate() {
        val currentBmp = currentBitmap ?: return
        Log.d("ImageEditor", "应用旋转")
        val rotatedBitmap = ImageEditUtils.rotateBitmap(currentBmp, 90f)
        applyBitmapEdit(rotatedBitmap, "旋转")
    }

    private fun showScaleDialog() {
        val scales = arrayOf("缩小 50%", "缩小 25%", "原大小", "放大 25%", "放大 50%", "放大 100%")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择缩放比例")
            .setItems(scales) { dialog, which ->
                val scale = when (which) {
                    0 -> 0.5f
                    1 -> 0.75f
                    2 -> 1.0f
                    3 -> 1.25f
                    4 -> 1.5f
                    5 -> 2.0f
                    else -> 1.0f
                }
                applyScale(scale)
                dialog.dismiss()
            }
            .show()
    }

    private fun applyScale(scale: Float) {
        val currentBmp = currentBitmap ?: return
        Log.d("ImageEditor", "应用缩放: $scale")
        val scaledBitmap = ImageEditUtils.scaleBitmap(currentBmp, scale)
        applyBitmapEdit(scaledBitmap, "缩放")
    }

    private fun showCropDialog() {
        val ratios = arrayOf("1:1 (正方形)", "3:4 (竖图)", "4:3 (横图)", "9:16 (手机)", "16:9 (宽屏)")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择裁剪比例")
            .setItems(ratios) { dialog, which ->
                val ratio = when (which) {
                    0 -> 1.0f
                    1 -> 3.0f / 4.0f
                    2 -> 4.0f / 3.0f
                    3 -> 9.0f / 16.0f
                    4 -> 16.0f / 9.0f
                    else -> 1.0f
                }
                applyCrop(ratio)
                dialog.dismiss()
            }
            .show()
    }

    private fun applyCrop(ratio: Float) {
        val currentBmp = currentBitmap ?: return
        Log.d("ImageEditor", "应用裁剪: 比例 $ratio")
        val croppedBitmap = ImageEditUtils.cropBitmap(currentBmp, ratio)
        applyBitmapEdit(croppedBitmap, "裁剪")
    }

    private fun showBrightnessDialog() {
        val currentBmp = currentBitmap ?: return

        // 使用变量保存当前调整值
        var currentAdjustment = 0f

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("调整亮度")
            .setView(createSeekBarLayout("亮度", -1.0f, 1.0f, 0f) { value ->
                // 保存当前调整值
                currentAdjustment = value
                Log.d("ImageEditor", "亮度实时预览: $value")
                // 实时预览
                val adjustedBitmap = ImageEditUtils.adjustBrightness(currentBmp, value)
                glSurfaceView.setImageBitmap(adjustedBitmap)
            })
            .setPositiveButton("应用") { dialog, _ ->
                // 应用更改 - 使用保存的调整值
                Log.d("ImageEditor", "应用亮度调整: $currentAdjustment")
                val adjustedBitmap = ImageEditUtils.adjustBrightness(currentBmp, currentAdjustment)
                applyBitmapEdit(adjustedBitmap, "亮度调整")
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                // 恢复原图
                Log.d("ImageEditor", "取消亮度调整")
                glSurfaceView.setImageBitmap(currentBmp)
                dialog.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun showContrastDialog() {
        val currentBmp = currentBitmap ?: return

        // 使用变量保存当前调整值
        var currentAdjustment = 1.0f

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("调整对比度")
            .setView(createSeekBarLayout("对比度", 0.0f, 4.0f, 1.0f) { value ->
                // 保存当前调整值
                currentAdjustment = value
                Log.d("ImageEditor", "对比度实时预览: $value")
                // 实时预览
                val adjustedBitmap = ImageEditUtils.adjustContrast(currentBmp, value)
                glSurfaceView.setImageBitmap(adjustedBitmap)
            })
            .setPositiveButton("应用") { dialog, _ ->
                // 应用更改 - 使用保存的调整值
                Log.d("ImageEditor", "应用对比度调整: $currentAdjustment")
                val adjustedBitmap = ImageEditUtils.adjustContrast(currentBmp, currentAdjustment)
                applyBitmapEdit(adjustedBitmap, "对比度调整")
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                // 恢复原图
                Log.d("ImageEditor", "取消对比度调整")
                glSurfaceView.setImageBitmap(currentBmp)
                dialog.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun showFilterDialog() {
        val filters = arrayOf("无滤镜", "黑白", "复古", "冷色调")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择滤镜")
            .setItems(filters) { dialog, which ->
                applyFilter(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun applyFilter(filterIndex: Int) {
        val currentBmp = currentBitmap ?: return
        val filteredBitmap = when (filterIndex) {
            1 -> {
                Log.d("ImageEditor", "应用黑白滤镜")
                ImageEditUtils.applyGrayscale(currentBmp)
            }
            2 -> {
                Log.d("ImageEditor", "应用复古滤镜")
                ImageEditUtils.applyVintage(currentBmp)
            }
            3 -> {
                Log.d("ImageEditor", "应用冷色调滤镜")
                ImageEditUtils.applyCoolTone(currentBmp)
            }
            else -> {
                Log.d("ImageEditor", "移除滤镜")
                currentBmp
            }
        }

        val filterName = when (filterIndex) {
            1 -> "黑白滤镜"
            2 -> "复古滤镜"
            3 -> "冷色调滤镜"
            else -> "移除滤镜"
        }

        applyBitmapEdit(filteredBitmap, filterName)
    }

    private fun createSeekBarLayout(
        title: String,
        min: Float,
        max: Float,
        defaultValue: Float,
        onProgressChanged: (Float) -> Unit
    ): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val textView = TextView(this).apply {
            text = "$title: ${"%.2f".format(defaultValue)}"
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }

        val seekBar = SeekBar(this).apply {
            this.max = 100
            progress = ((defaultValue - min) / (max - min) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = min + (progress / 100f) * (max - min)
                    textView.text = "$title: ${"%.2f".format(value)}"
                    onProgressChanged(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        layout.addView(textView)
        layout.addView(seekBar)
        return layout
    }

    private fun applyBitmapEdit(newBitmap: Bitmap, operation: String) {
        Log.d("ImageEditor", "====================")
        Log.d("ImageEditor", "操作: $operation")
        Log.d("ImageEditor", "原图: ${currentBitmap?.width}x${currentBitmap?.height}")
        Log.d("ImageEditor", "新图: ${newBitmap.width}x${newBitmap.height}")
        Log.d("ImageEditor", "是否相同: ${currentBitmap == newBitmap}")

        // 回收旧bitmap
        currentBitmap?.recycle()
        currentBitmap = newBitmap

        Log.d("ImageEditor", "设置到GLSurfaceView")
        glSurfaceView.setImageBitmap(newBitmap)

        saveToHistory(newBitmap)
        Toast.makeText(this, "已$operation", Toast.LENGTH_SHORT).show()

        // 强制重绘
        glSurfaceView.requestRender()
        glSurfaceView.invalidate()
    }

    private fun undoEdit() {
        if (currentEditIndex > 0) {
            currentEditIndex--
            val previousBitmap = editHistory[currentEditIndex]
            currentBitmap = previousBitmap
            glSurfaceView.setImageBitmap(previousBitmap)
            Toast.makeText(this, "已撤销", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "无法撤销", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToHistory(bitmap: Bitmap) {
        if (editHistory.size >= 10) {
            val removed = editHistory.removeAt(0)
            removed.recycle()
            currentEditIndex--
        }

        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val copy = bitmap.copy(config, true)
        editHistory.add(copy)
        currentEditIndex = editHistory.size - 1

        Log.d("ImageEditor", "保存到历史: 当前有${editHistory.size}条记录")
    }

    private fun saveImage() {
        currentBitmap?.let { bitmap ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val savedUri = saveBitmapToGallery(bitmap)
                    withContext(Dispatchers.Main) {
                        if (savedUri != null) {
                            Toast.makeText(
                                this@ImageEditorActivity,
                                "图片已保存到相册",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@ImageEditorActivity,
                                "保存失败，请检查存储权限",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ImageEditorActivity,
                            "保存失败: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } ?: run {
            Toast.makeText(this, "没有可保存的图片", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "edited_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoEditor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let {
            try {
                val outputStream = contentResolver.openOutputStream(it)
                outputStream?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }

                return uri
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
        }

        return null
    }

    // 测试编辑功能
    private fun testEditFunction() {
        currentBitmap?.let { bitmap ->
            Log.d("ImageEditor", "=== 开始测试编辑功能 ===")

            // 测试旋转
            val rotated = ImageEditUtils.rotateBitmap(bitmap, 45f)
            Log.d("ImageEditor", "旋转测试: ${rotated.width}x${rotated.height}")
            rotated.recycle()

            // 测试亮度
            val brightened = ImageEditUtils.adjustBrightness(bitmap, 0.5f)
            Log.d("ImageEditor", "亮度测试: ${brightened.width}x${brightened.height}")
            brightened.recycle()

            // 测试对比度
            val contrasted = ImageEditUtils.adjustContrast(bitmap, 1.5f)
            Log.d("ImageEditor", "对比度测试: ${contrasted.width}x${contrasted.height}")
            contrasted.recycle()

            Log.d("ImageEditor", "=== 编辑功能测试完成 ===")
        }
    }

    // 处理返回按钮 - 由于我们使用自定义 Toolbar，这个方法可能不会被调用
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ImageEditor", "ImageEditorActivity销毁")
        glSurfaceView.cleanup()
        originalBitmap?.recycle()
        currentBitmap?.recycle()
        editHistory.forEach { it.recycle() }
        editHistory.clear()
    }
}