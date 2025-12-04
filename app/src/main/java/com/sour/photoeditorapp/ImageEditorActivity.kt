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

    // 用于跟踪对话框状态，防止多次显示
    private var isDialogShowing = false

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

                        // 设置图片到GLSurfaceView
                        glSurfaceView.setImageBitmap(bitmap)
                        saveToHistory(bitmap)
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
        // 移除缩放功能，只保留其他功能
        val tools = listOf(
            EditTool("旋转", android.R.drawable.ic_menu_rotate) { applyRotate() },
            EditTool("裁剪", android.R.drawable.ic_menu_crop) { showCropDialog() },
            EditTool("亮度", android.R.drawable.ic_menu_edit) { showBrightnessDialog() },
            EditTool("对比度", android.R.drawable.ic_menu_agenda) { showContrastDialog() },
            EditTool("滤镜", android.R.drawable.ic_menu_gallery) { showFilterDialog() },
            EditTool("撤销", android.R.drawable.ic_menu_revert) { undoEdit() }
        )

        // 计算按钮宽度：每个按钮占屏幕宽度的1/5.5，留出间距
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val buttonWidth = (screenWidth / 5.5).toInt() // 设置为屏幕宽度的1/5.5，留出间距

        tools.forEach { tool ->
            val button = Button(this).apply {
                text = tool.name
                setCompoundDrawablesWithIntrinsicBounds(0, tool.iconRes, 0, 0)
                setOnClickListener { tool.action.invoke() }

                // 设置按钮宽度和布局参数
                layoutParams = LinearLayout.LayoutParams(
                    buttonWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(4, 8, 4, 8) // 设置按钮间距
                }

                // 保持原来的样式，只调整内边距和文本处理
                setPadding(32, 16, 32, 16)
                setBackgroundColor(ContextCompat.getColor(this@ImageEditorActivity, android.R.color.white))
                setTextColor(ContextCompat.getColor(this@ImageEditorActivity, android.R.color.black))

                // 设置文本单行显示，省略号结尾
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            editToolsLayout.addView(button)
        }
    }

    data class EditTool(val name: String, val iconRes: Int, val action: () -> Unit)

    // 编辑功能实现 - 移除所有与缩放相关的方法
    private fun applyRotate() {
        val currentBmp = currentBitmap ?: return
        Log.d("ImageEditor", "应用旋转")
        val rotatedBitmap = ImageEditUtils.rotateBitmap(currentBmp, 90f)
        applyBitmapEdit(rotatedBitmap, "旋转")
    }

    private fun showCropDialog() {
        if (isDialogShowing) return
        isDialogShowing = true

        val ratios = arrayOf("1:1 (正方形)", "3:4 (竖图)", "4:3 (横图)", "9:16 (手机)", "16:9 (宽屏)")

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
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
                isDialogShowing = false
            }
            .setOnDismissListener {
                isDialogShowing = false
            }
            .show()
    }

    private fun applyCrop(ratio: Float) {
        val currentBmp = currentBitmap ?: run {
            Log.e("ImageEditor", "applyCrop: currentBitmap is null!")
            Toast.makeText(this, "当前图片不可用", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("ImageEditor", "开始应用裁剪: 比例 $ratio, 原图尺寸 ${currentBmp.width}x${currentBmp.height}")

        try {
            // 记录裁剪前的内存状态（可选）
            val croppedBitmap = ImageEditUtils.cropBitmap(currentBmp, ratio)

            if (croppedBitmap == currentBmp) {
                Log.w("ImageEditor", "裁剪返回了原图，可能参数无效")
                Toast.makeText(this, "裁剪比例与图片相同或无效", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d("ImageEditor", "裁剪成功，新尺寸: ${croppedBitmap.width}x${croppedBitmap.height}")
            applyBitmapEdit(croppedBitmap, "裁剪")

        } catch (e: Exception) {
            Log.e("ImageEditor", "裁剪过程中发生异常: ${e.message}", e)
            e.printStackTrace()
            Toast.makeText(this, "裁剪失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()

            // 确保出错时视图显示正常的图片
            glSurfaceView.setImageBitmap(currentBmp)
        }
    }

    private fun showBrightnessDialog() {
        if (isDialogShowing) return
        isDialogShowing = true

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
                isDialogShowing = false
            }
            .setNegativeButton("取消") { dialog, _ ->
                // 恢复原图
                Log.d("ImageEditor", "取消亮度调整")
                glSurfaceView.setImageBitmap(currentBmp)
                dialog.dismiss()
                isDialogShowing = false
            }
            .setOnDismissListener {
                isDialogShowing = false
            }
            .create()
        dialog.show()
    }

    private fun showContrastDialog() {
        if (isDialogShowing) return
        isDialogShowing = true

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
                isDialogShowing = false
            }
            .setNegativeButton("取消") { dialog, _ ->
                // 恢复原图
                Log.d("ImageEditor", "取消对比度调整")
                glSurfaceView.setImageBitmap(currentBmp)
                dialog.dismiss()
                isDialogShowing = false
            }
            .setOnDismissListener {
                isDialogShowing = false
            }
            .create()
        dialog.show()
    }

    private fun showFilterDialog() {
        if (isDialogShowing) return
        isDialogShowing = true

        val filters = arrayOf("无滤镜", "黑白", "复古", "冷色调")

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择滤镜")
            .setItems(filters) { dialog, which ->
                applyFilter(which)
                dialog.dismiss()
                isDialogShowing = false
            }
            .setOnDismissListener {
                isDialogShowing = false
            }
            .show()
    }

    private fun applyFilter(filterIndex: Int) {
        val currentBmp = currentBitmap ?: return

        when (filterIndex) {
            0 -> {
                // 无滤镜 - 使用当前图片（不做处理）
                Log.d("ImageEditor", "移除滤镜")
                Toast.makeText(this, "已移除滤镜", Toast.LENGTH_SHORT).show()
            }
            1 -> {
                Log.d("ImageEditor", "应用黑白滤镜")
                val filteredBitmap = ImageEditUtils.applyGrayscale(currentBmp)
                applyBitmapEdit(filteredBitmap, "黑白滤镜")
            }
            2 -> {
                Log.d("ImageEditor", "应用复古滤镜")
                val filteredBitmap = ImageEditUtils.applyVintage(currentBmp)
                applyBitmapEdit(filteredBitmap, "复古滤镜")
            }
            3 -> {
                Log.d("ImageEditor", "应用冷色调滤镜")
                val filteredBitmap = ImageEditUtils.applyCoolTone(currentBmp)
                applyBitmapEdit(filteredBitmap, "冷色调滤镜")
            }
        }
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

        // **关键修改：先保存旧Bitmap的引用**
        val oldBitmap = currentBitmap

        // 1. 更新当前Bitmap引用
        currentBitmap = newBitmap

        // 2. 设置新图到GLSurfaceView (触发OpenGL渲染)
        Log.d("ImageEditor", "设置到GLSurfaceView")
        glSurfaceView.setImageBitmap(newBitmap)

        // 3. 在设置完成后，再安全地回收旧Bitmap
        // 确保不是在回收刚传入的新Bitmap
        if (oldBitmap != null && oldBitmap != newBitmap && !oldBitmap.isRecycled) {
            Log.d("ImageEditor", "安全回收旧Bitmap")
            oldBitmap.recycle()
        }

        // 4. 保存历史记录
        saveToHistory(newBitmap)
        Toast.makeText(this, "已$operation", Toast.LENGTH_SHORT).show()
    }

    private fun undoEdit() {
        if (currentEditIndex > 0) {
            currentEditIndex--
            val previousBitmap = editHistory[currentEditIndex]

            // 回收当前图片
            val oldBitmap = currentBitmap
            currentBitmap = previousBitmap

            glSurfaceView.setImageBitmap(previousBitmap)

            // 安全回收旧Bitmap
            if (oldBitmap != null && oldBitmap != previousBitmap && !oldBitmap.isRecycled) {
                oldBitmap.recycle()
            }

            Toast.makeText(this, "已撤销", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "无法撤销", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToHistory(bitmap: Bitmap) {
        if (editHistory.size >= 10) {
            val removed = editHistory.removeAt(0)
            if (!removed.isRecycled) {
                removed.recycle()
            }
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

    // 处理返回按钮 - 由于我们使用自定义 Toolbar，这个方法可能不会被调用
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ImageEditor", "ImageEditorActivity销毁")

        // **关键修改：按顺序清理，避免OpenGL上下文问题**

        // 1. 先停止GLSurfaceView的渲染
        glSurfaceView.onPause()

        // 2. 清理OpenGL资源
        glSurfaceView.cleanup()

        // 3. 最后回收Bitmap（此时OpenGL已停止使用它们）
        originalBitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
            originalBitmap = null
        }

        currentBitmap?.let {
            // 注意：这里不要回收currentBitmap，因为它可能在editHistory中
            currentBitmap = null
        }

        // editHistory的清理保持不变
        editHistory.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        editHistory.clear()
    }
}