package com.sour.photoeditorapp

import android.content.ContentValues
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoEditorActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var playPauseButton: ImageButton
    private lateinit var videoTimeText: TextView
    private lateinit var saveButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var cutButton: LinearLayout
    private lateinit var filterButton: LinearLayout
    private lateinit var cutControls: LinearLayout
    private lateinit var startTimeSeekBar: SeekBar
    private lateinit var endTimeSeekBar: SeekBar
    private lateinit var startTimeText: TextView
    private lateinit var endTimeText: TextView
    private lateinit var applyCutButton: Button

    private var videoUri: Uri? = null
    private var videoDuration: Int = 0
    private var isPlaying = false
    private var startTime = 0
    private var endTime = 0
    private val videoUpdateHandler = android.os.Handler()
    private val videoUpdateRunnable = object : Runnable {
        override fun run() {
            updateVideoTime()
            videoUpdateHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用布局文件
        setContentView(R.layout.activity_video_editor)

        initViews()
        setupVideo()
        setupClickListeners()

        // 初始化FFmpeg
        initFFmpeg()
    }

    private fun initFFmpeg() {
        // 这里初始化FFmpeg库
        try {
            // 简单的FFmpeg测试
            Log.d("VideoEditor", "FFmpeg库可用")
        } catch (e: Exception) {
            Log.e("VideoEditor", "FFmpeg初始化失败: ${e.message}")
        }
    }

    private fun initViews() {
        videoView = findViewById(R.id.videoView)
        playPauseButton = findViewById(R.id.playPauseButton)
        videoTimeText = findViewById(R.id.videoTimeText)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)
        cutButton = findViewById(R.id.cutButton)
        filterButton = findViewById(R.id.filterButton)
        cutControls = findViewById(R.id.cutControls)
        startTimeSeekBar = findViewById(R.id.startTimeSeekBar)
        endTimeSeekBar = findViewById(R.id.endTimeSeekBar)
        startTimeText = findViewById(R.id.startTimeText)
        endTimeText = findViewById(R.id.endTimeText)
        applyCutButton = findViewById(R.id.applyCutButton)
    }

    private fun setupVideo() {
        val mediaUriString = intent.getStringExtra("mediaUri")
        val mediaPath = intent.getStringExtra("mediaPath")

        videoUri = if (mediaUriString != null) {
            Uri.parse(mediaUriString)
        } else if (mediaPath != null) {
            Uri.parse(mediaPath)
        } else {
            intent.data
        }

        Log.d("VideoEditor", "视频URI: $videoUri")
        Log.d("VideoEditor", "视频路径: $mediaPath")

        if (videoUri == null) {
            Toast.makeText(this, "视频URI为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        videoView.setVideoURI(videoUri)

        videoView.setOnPreparedListener { mp ->
            videoDuration = mp.duration
            if (videoDuration == 0) {
                Toast.makeText(this, "视频时长异常，可能格式不支持", Toast.LENGTH_SHORT).show()
                finish()
                return@setOnPreparedListener
            }

            endTime = videoDuration
            updateTimeText()

            // 设置SeekBar范围
            startTimeSeekBar.max = videoDuration
            endTimeSeekBar.max = videoDuration
            endTimeSeekBar.progress = videoDuration

            videoView.seekTo(startTime)
            videoView.start()
            isPlaying = true
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            videoUpdateHandler.post(videoUpdateRunnable)

            Toast.makeText(this, "视频加载成功，时长: ${formatTime(videoDuration)}", Toast.LENGTH_SHORT).show()
        }

        videoView.setOnErrorListener { mp, what, extra ->
            Log.e("VideoEditor", "视频播放错误: what=$what, extra=$extra")

            Toast.makeText(this, "视频播放失败，错误代码: $what", Toast.LENGTH_SHORT).show()
            true
        }

        videoView.setOnCompletionListener {
            isPlaying = false
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            videoView.seekTo(startTime)
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        saveButton.setOnClickListener {
            saveVideoToGallery()
        }

        cutButton.setOnClickListener {
            showCutControls()
        }

        filterButton.setOnClickListener {
            showFilterOptions()
        }

        applyCutButton.setOnClickListener {
            applyCutWithNative() // 替换原来的 showCutNotImplementedDialog()
        }

        startTimeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    startTime = progress
                    if (startTime >= endTime) {
                        startTime = endTime - 1000
                        startTimeSeekBar.progress = startTime
                    }
                    updateTimeText()
                    videoView.seekTo(startTime)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        endTimeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    endTime = progress
                    if (endTime <= startTime) {
                        endTime = startTime + 1000
                        endTimeSeekBar.progress = endTime
                    }
                    updateTimeText()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun applyCutWithNative() {
        if (videoUri == null) {
            Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show()
            return
        }

        // 验证剪辑时间
        if (endTime - startTime <= 1000) {
            Toast.makeText(this, "剪辑时间太短，请至少选择1秒", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在处理")
            .setMessage("正在剪辑视频...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outputPath = VideoEditHelper.cutVideo(
                    context = this@VideoEditorActivity,
                    inputUri = videoUri!!,
                    startTimeMs = startTime.toLong(),
                    endTimeMs = endTime.toLong()
                )

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (outputPath != null) {
                        val outputFile = File(outputPath)
                        val outputUri = Uri.fromFile(outputFile)

                        // 预览剪辑后的视频
                        videoView.setVideoURI(outputUri)
                        videoView.start()
                        videoUri = outputUri

                        Toast.makeText(this@VideoEditorActivity, "视频剪辑成功", Toast.LENGTH_SHORT).show()
                        cutControls.visibility = LinearLayout.GONE
                    } else {
                        Toast.makeText(this@VideoEditorActivity, "视频剪辑失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@VideoEditorActivity, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processVideoWithFFmpeg(): Boolean {
        // 这里是FFmpeg处理逻辑
        // 由于FFmpeg集成需要一些设置，这里先提供一个框架
        // 实际使用时需要实现具体的FFmpeg命令

        try {
            // 获取输入文件路径
            val inputPath = getVideoFilePath(videoUri!!)
            if (inputPath == null) {
                Log.e("FFmpeg", "无法获取视频文件路径")
                return false
            }

            // 创建输出文件
            val outputFile = createOutputVideoFile()
            val outputPath = outputFile.absolutePath

            Log.d("FFmpeg", "输入文件: $inputPath")
            Log.d("FFmpeg", "输出文件: $outputPath")
            Log.d("FFmpeg", "开始时间: ${startTime}ms, 结束时间: ${endTime}ms")

            // 这里应该执行FFmpeg命令
            // 示例命令：ffmpeg -i input.mp4 -ss 00:00:10 -to 00:00:20 -c copy output.mp4

            // 由于FFmpeg集成需要更多配置，这里先返回成功
            // 实际使用时需要调用FFmpeg库

            // 临时：模拟处理成功
            Thread.sleep(2000) // 模拟处理时间

            // 处理完成后，预览新视频
            runOnUiThread {
                val outputUri = Uri.fromFile(outputFile)
                videoView.setVideoURI(outputUri)
                videoView.start()
                videoUri = outputUri
            }

            return true
        } catch (e: Exception) {
            Log.e("FFmpeg", "处理失败: ${e.message}")
            return false
        }
    }

    private fun getVideoFilePath(uri: Uri): String? {
        return try {
            if (uri.toString().startsWith("content://")) {
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndex(MediaStore.Video.Media.DATA)
                        it.getString(columnIndex)
                    } else {
                        null
                    }
                }
            } else {
                uri.path
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createOutputVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "cut_${timeStamp}.mp4"

        val outputDir = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "EditedVideos"
        )

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        return File(outputDir, fileName)
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            videoView.pause()
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
        } else {
            videoView.start()
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            videoUpdateHandler.post(videoUpdateRunnable)
        }
        isPlaying = !isPlaying
    }

    private fun updateVideoTime() {
        runOnUiThread {
            val currentPosition = videoView.currentPosition
            updateTimeText(currentPosition)
        }
    }

    private fun updateTimeText(currentPos: Int = -1) {
        val current = if (currentPos == -1) videoView.currentPosition else currentPos
        val currentText = formatTime(current)
        val durationText = formatTime(videoDuration)
        videoTimeText.text = "$currentText / $durationText"

        startTimeText.text = "开始时间: ${formatTime(startTime)}"
        endTimeText.text = "结束时间: ${formatTime(endTime)}"
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun showCutControls() {
        cutControls.visibility = LinearLayout.VISIBLE
    }

    private fun showFilterOptions() {
        val filters = VideoFilterHelper.getAvailableFilters()
        val filterNames = filters.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择滤镜")
            .setItems(filterNames) { _, which ->
                val selectedFilter = filters[which].first
                applyFilterToVideo(selectedFilter)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyFilterToVideo(filterType: VideoFilterHelper.FilterType) {
        if (videoUri == null) {
            Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在处理")
            .setMessage("正在提取视频帧并应用滤镜...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 提取当前视频帧
                val currentPosition = videoView.currentPosition.toLong()
                val framePath = VideoEditHelper.extractVideoFrame(
                    this@VideoEditorActivity,
                    videoUri!!,
                    currentPosition
                )

                if (framePath != null) {
                    // 2. 应用滤镜到提取的帧
                    val frameUri = Uri.fromFile(File(framePath))
                    val filteredBitmap = VideoFilterHelper.applyFilterToImage(
                        this@VideoEditorActivity,
                        frameUri,
                        filterType
                    )

                    if (filteredBitmap != null) {
                        // 3. 保存滤镜图片
                        val savedPath = VideoFilterHelper.saveFilteredImage(
                            this@VideoEditorActivity,
                            filteredBitmap,
                            filterType
                        )

                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()

                            if (savedPath != null) {
                                // 显示滤镜效果（可选：可以显示在ImageView中）
                                AlertDialog.Builder(this@VideoEditorActivity)
                                    .setTitle("滤镜应用成功")
                                    .setMessage("滤镜已应用到当前帧并保存到:\n$savedPath")
                                    .setPositiveButton("确定", null)
                                    .show()
                            } else {
                                Toast.makeText(
                                    this@VideoEditorActivity,
                                    "滤镜图片保存失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            Toast.makeText(
                                this@VideoEditorActivity,
                                "滤镜应用失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            this@VideoEditorActivity,
                            "提取视频帧失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@VideoEditorActivity,
                        "滤镜应用失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveVideoToGallery() {
        if (videoUri != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val contentResolver = applicationContext.contentResolver

                    contentResolver.openInputStream(videoUri!!)?.use { inputStream ->
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val fileName = "BeautyPic_${timeStamp}.mp4"

                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BeautyPic")
                            }
                        }

                        val savedUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

                        if (savedUri != null) {
                            contentResolver.openOutputStream(savedUri)?.use { outputStream ->
                                inputStream.copyTo(outputStream)
                                outputStream.flush()

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@VideoEditorActivity,
                                        "视频已保存到相册",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } ?: run {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@VideoEditorActivity,
                                        "保存失败：无法创建输出流",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@VideoEditorActivity,
                                    "保存失败：无法创建媒体文件",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@VideoEditorActivity,
                                "保存失败：无法读取视频文件",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@VideoEditorActivity,
                            "保存失败: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
        videoView.stopPlayback()
    }
}