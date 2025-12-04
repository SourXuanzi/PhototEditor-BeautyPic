package com.sour.photoeditorapp

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.nio.ByteBuffer

object VideoEditHelper {

    private const val TAG = "VideoEditHelper"
    private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer

    // 获取视频信息
    suspend fun getVideoInfo(context: Context, videoUri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        return@withContext try {
            val retriever = MediaMetadataRetriever()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.setDataSource(context, videoUri)
            } else {
                val path = getVideoFilePath(context, videoUri)
                if (path != null) {
                    retriever.setDataSource(path)
                } else {
                    retriever.setDataSource(context, videoUri)
                }
            }

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0

            retriever.release()

            VideoInfo(duration, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "获取视频信息失败: ${e.message}")
            null
        }
    }

    // 剪辑视频（使用 MediaExtractor + MediaMuxer）
    suspend fun cutVideo(
        context: Context,
        inputUri: Uri,
        startTimeMs: Long,
        endTimeMs: Long
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val inputPath = getVideoFilePath(context, inputUri) ?: return@withContext null
            val outputFile = createOutputFile(context, "cut_", ".mp4")

            Log.d(TAG, "开始剪辑视频: $inputPath")
            Log.d(TAG, "时间范围: ${startTimeMs}ms - ${endTimeMs}ms")
            Log.d(TAG, "输出文件: ${outputFile.absolutePath}")

            val extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            val trackCount = extractor.trackCount
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 跟踪视频和音频轨道的索引
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            // 选择视频和音频轨道
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                when {
                    mime?.startsWith("video/") == true && videoTrackIndex == -1 -> {
                        videoTrackIndex = i
                        videoFormat = format
                        extractor.selectTrack(i)
                    }
                    mime?.startsWith("audio/") == true && audioTrackIndex == -1 -> {
                        audioTrackIndex = i
                        audioFormat = format
                    }
                }
            }

            // 添加轨道到 Muxer
            val muxerVideoTrackIndex = if (videoFormat != null) {
                muxer.addTrack(videoFormat!!)
            } else -1

            val muxerAudioTrackIndex = if (audioFormat != null) {
                muxer.addTrack(audioFormat!!)
            } else -1

            muxer.start()

            // 处理视频轨道
            if (videoTrackIndex != -1) {
                processTrack(extractor, muxer, videoTrackIndex, muxerVideoTrackIndex, startTimeMs, endTimeMs)
            }

            // 处理音频轨道
            if (audioTrackIndex != -1) {
                extractor.selectTrack(audioTrackIndex)
                processTrack(extractor, muxer, audioTrackIndex, muxerAudioTrackIndex, startTimeMs, endTimeMs)
            }

            extractor.release()
            muxer.stop()
            muxer.release()

            Log.d(TAG, "视频剪辑完成: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "剪辑视频失败: ${e.message}", e)
            null
        }
    }

    private fun processTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        extractorTrackIndex: Int,
        muxerTrackIndex: Int,
        startTimeMs: Long,
        endTimeMs: Long
    ) {
        extractor.seekTo(startTimeMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()

        var isFinished = false

        while (!isFinished) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)

            if (sampleSize < 0) {
                isFinished = true
                break
            }

            val sampleTime = extractor.sampleTime / 1000 // 转换为毫秒
            if (sampleTime > endTimeMs) {
                isFinished = true
                break
            }

            // 调整时间戳，使其从0开始
            val presentationTimeUs = extractor.sampleTime - (startTimeMs * 1000)

            // 转换 MediaExtractor 标志到 MediaCodec 标志
            val extractorFlags = extractor.sampleFlags
            var codecFlags = 0

            // MediaExtractor.SAMPLE_FLAG_SYNC 对应 MediaCodec.BUFFER_FLAG_KEY_FRAME
            if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            }

            // MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME 对应 MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            }

            bufferInfo.set(0, sampleSize, presentationTimeUs, codecFlags)
            buffer.limit(sampleSize)

            if (muxerTrackIndex >= 0) {
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
            }

            extractor.advance()
        }
    }

    // 提取视频帧作为图片
    suspend fun extractVideoFrame(
        context: Context,
        videoUri: Uri,
        timeMs: Long
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val retriever = MediaMetadataRetriever()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.setDataSource(context, videoUri)
            } else {
                val path = getVideoFilePath(context, videoUri)
                if (path != null) {
                    retriever.setDataSource(path)
                } else {
                    retriever.setDataSource(context, videoUri)
                }
            }

            val frame = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()

            if (frame != null) {
                val outputFile = createOutputFile(context, "frame_", ".jpg")
                val fos = FileOutputStream(outputFile)
                frame.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.close()
                outputFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取视频帧失败: ${e.message}")
            null
        }
    }

    // 获取视频文件路径
    private fun getVideoFilePath(context: Context, uri: Uri): String? {
        return try {
            if (uri.toString().startsWith("content://")) {
                val projection = arrayOf("_data")
                val cursor = context.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndexOrThrow("_data")
                        it.getString(columnIndex)
                    } else {
                        null
                    }
                }
            } else {
                uri.path
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取视频文件路径失败: ${e.message}")
            null
        }
    }

    // 创建输出文件
    private fun createOutputFile(context: Context, prefix: String, extension: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}${timeStamp}${extension}"

        val outputDir = File(
            context.getExternalFilesDir(null),
            "VideoEditor"
        )

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        return File(outputDir, fileName)
    }

    // 清理临时文件
    fun cleanupTempFiles(context: Context) {
        val outputDir = File(context.getExternalFilesDir(null), "VideoEditor")
        if (outputDir.exists() && outputDir.isDirectory) {
            outputDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".mp4") || file.name.endsWith(".jpg"))) {
                    try {
                        file.delete()
                        Log.d(TAG, "删除临时文件: ${file.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "删除文件失败: ${file.name}")
                    }
                }
            }
        }
    }
}

data class VideoInfo(
    val duration: Long, // 毫秒
    val width: Int,
    val height: Int
)