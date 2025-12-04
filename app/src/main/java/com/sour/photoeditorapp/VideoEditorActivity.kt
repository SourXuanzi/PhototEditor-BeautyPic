package com.sour.photoeditorapp

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoEditorActivity : AppCompatActivity() {

    private lateinit var playerView: com.google.android.exoplayer2.ui.PlayerView
    private lateinit var playPauseButton: ImageButton
    private lateinit var videoTimeText: TextView
    private lateinit var saveButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var cutButton: LinearLayout
    private lateinit var cutControls: LinearLayout
    private lateinit var startTimeSeekBar: SeekBar
    private lateinit var endTimeSeekBar: SeekBar
    private lateinit var startTimeText: TextView
    private lateinit var endTimeText: TextView
    private lateinit var applyCutButton: Button

    private var player: ExoPlayer? = null
    private var videoUri: Uri? = null
    private var videoDuration: Long = 0L
    private var isPlaying = false
    private var startTime = 0L
    private var endTime = 0L
    private val videoUpdateHandler = android.os.Handler()
    private val videoUpdateRunnable = object : Runnable {
        override fun run() {
            updateVideoTime()
            videoUpdateHandler.postDelayed(this, 1000)
        }
    }

    // 用于防止重复点击的标志
    private var isProcessingClick = false
    private var isVideoLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editor)

        initViews()
        setupVideo()
        setupClickListeners()
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        playPauseButton = findViewById(R.id.playPauseButton)
        videoTimeText = findViewById(R.id.videoTimeText)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)
        cutButton = findViewById(R.id.cutButton)
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

        if (videoUri == null) {
            Toast.makeText(this, "视频URI为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化 ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val dataSourceFactory = DefaultDataSource.Factory(this)
        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUri!!))

        player?.setMediaSource(mediaSource)
        player?.prepare()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        videoDuration = player?.duration ?: 0L
                        if (videoDuration == 0L) {
                            Toast.makeText(this@VideoEditorActivity, "视频时长异常，可能格式不支持", Toast.LENGTH_SHORT).show()
                            finish()
                            return
                        }

                        endTime = videoDuration
                        updateTimeText()

                        // 设置SeekBar范围
                        val maxProgress = if (videoDuration > Int.MAX_VALUE.toLong()) {
                            Int.MAX_VALUE
                        } else {
                            videoDuration.toInt()
                        }

                        startTimeSeekBar.max = maxProgress
                        endTimeSeekBar.max = maxProgress
                        endTimeSeekBar.progress = maxProgress

                        // 只在第一次加载时自动播放
                        if (!isVideoLoaded) {
                            player?.seekTo(startTime)
                            player?.play()
                            isPlaying = true
                            isVideoLoaded = true
                        }

                        updatePlayPauseButton()
                        videoUpdateHandler.post(videoUpdateRunnable)

                        Log.d("VideoEditor", "视频加载成功，时长: ${formatTime(videoDuration)}")
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d("VideoEditor", "视频缓冲中...")
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        updatePlayPauseButton()
                        player?.seekTo(startTime)
                        videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
                    }
                    Player.STATE_IDLE -> {
                        Log.d("VideoEditor", "播放器空闲")
                    }
                }
            }

            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                Log.e("VideoEditor", "视频播放错误: ${error.message}")
                Toast.makeText(this@VideoEditorActivity, "视频播放失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 同步播放状态
                this@VideoEditorActivity.isPlaying = isPlaying
                updatePlayPauseButton()
                if (isPlaying) {
                    videoUpdateHandler.post(videoUpdateRunnable)
                } else {
                    videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
                }
            }
        })
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        playPauseButton.setOnClickListener {
            // 防止重复点击
            if (isProcessingClick) return@setOnClickListener

            isProcessingClick = true
            playPauseButton.postDelayed({
                isProcessingClick = false
            }, 500)

            togglePlayPause()
        }

        saveButton.setOnClickListener {
            saveVideoToGallery()
        }

        cutButton.setOnClickListener {
            showCutControls()
        }

        applyCutButton.setOnClickListener {
            applyCutWithNative()
        }

        startTimeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 将SeekBar的进度值转换为实际的时间值
                    startTime = if (videoDuration > Int.MAX_VALUE.toLong()) {
                        (progress.toLong() * videoDuration) / startTimeSeekBar.max
                    } else {
                        progress.toLong()
                    }

                    if (startTime >= endTime) {
                        startTime = endTime - 1000
                        // 更新SeekBar的显示位置
                        val newProgress = if (videoDuration > Int.MAX_VALUE.toLong()) {
                            ((startTime * startTimeSeekBar.max) / videoDuration).toInt()
                        } else {
                            startTime.toInt()
                        }
                        startTimeSeekBar.progress = newProgress
                    }
                    updateTimeText()
                    player?.seekTo(startTime)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // 当用户开始拖动时暂停视频
                if (player?.isPlaying == true) {
                    player?.pause()
                    videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // 当用户停止拖动时，如果之前是播放状态，则继续播放
                if (isPlaying) {
                    player?.play()
                    videoUpdateHandler.post(videoUpdateRunnable)
                }
            }
        })

        endTimeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 将SeekBar的进度值转换为实际的时间值
                    endTime = if (videoDuration > Int.MAX_VALUE.toLong()) {
                        (progress.toLong() * videoDuration) / endTimeSeekBar.max
                    } else {
                        progress.toLong()
                    }

                    if (endTime <= startTime) {
                        endTime = startTime + 1000
                        // 更新SeekBar的显示位置
                        val newProgress = if (videoDuration > Int.MAX_VALUE.toLong()) {
                            ((endTime * endTimeSeekBar.max) / videoDuration).toInt()
                        } else {
                            endTime.toInt()
                        }
                        endTimeSeekBar.progress = newProgress
                    }
                    updateTimeText()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // 当用户开始拖动时暂停视频
                if (player?.isPlaying == true) {
                    player?.pause()
                    videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // 当用户停止拖动时，如果之前是播放状态，则继续播放
                if (isPlaying) {
                    player?.play()
                    videoUpdateHandler.post(videoUpdateRunnable)
                }
            }
        })
    }

    private fun updatePlayPauseButton() {
        runOnUiThread {
            playPauseButton.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }
    }

    private fun applyCutWithNative() {
        if (videoUri == null) {
            Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show()
            return
        }

        // 验证剪辑时间
        if (endTime - startTime <= 1000L) {
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
                    startTimeMs = startTime,
                    endTimeMs = endTime
                )

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (outputPath != null) {
                        val outputFile = File(outputPath)
                        val outputUri = Uri.fromFile(outputFile)

                        // 暂停当前播放
                        player?.pause()
                        videoUpdateHandler.removeCallbacks(videoUpdateRunnable)

                        // 重置视频加载状态
                        isVideoLoaded = false

                        // 更新视频URI
                        videoUri = outputUri
                        val dataSourceFactory = DefaultDataSource.Factory(this@VideoEditorActivity)
                        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(outputUri))

                        // 重置播放器
                        player?.stop()
                        player?.clearMediaItems()
                        player?.setMediaSource(mediaSource)
                        player?.prepare()

                        // 等待视频加载完成后再播放
                        player?.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY) {
                                    player?.play()
                                    player?.removeListener(this)
                                }
                            }
                        })

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

    private fun togglePlayPause() {
        val currentPlayer = player ?: return

        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
            videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
        } else {
            currentPlayer.play()
            videoUpdateHandler.post(videoUpdateRunnable)
        }

        // 状态会在onIsPlayingChanged中更新
    }

    private fun updateVideoTime() {
        runOnUiThread {
            val currentPosition = player?.currentPosition ?: 0L
            updateTimeText(currentPosition)

            // 检查是否到达结束时间
            if (currentPosition >= endTime && isPlaying) {
                player?.pause()
                player?.seekTo(startTime)
                videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
            }
        }
    }

    private fun updateTimeText(currentPos: Long = -1L) {
        val current = if (currentPos == -1L) player?.currentPosition ?: 0L else currentPos
        val currentText = formatTime(current)
        val durationText = formatTime(videoDuration)
        videoTimeText.text = "$currentText / $durationText"

        startTimeText.text = "开始时间: ${formatTime(startTime)}"
        endTimeText.text = "结束时间: ${formatTime(endTime)}"
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun showCutControls() {
        cutControls.visibility = LinearLayout.VISIBLE
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

    override fun onPause() {
        super.onPause()
        // 暂停视频播放并移除更新回调
        player?.pause()
        videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
    }

    override fun onResume() {
        super.onResume()
        // 只在离开时是播放状态时才恢复播放
        if (isPlaying) {
            player?.play()
            videoUpdateHandler.post(videoUpdateRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理所有资源
        videoUpdateHandler.removeCallbacks(videoUpdateRunnable)
        player?.release()
        player = null
    }
}