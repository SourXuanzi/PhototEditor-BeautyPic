package com.sour.photoeditorapp

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
//import java.text.SimpleDateFormat
//import java.util.*
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever

class MediaAdapter(
    private val mediaList: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

    private var job: Job? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.mediaImageView)
        val videoIcon: View? = view.findViewById(R.id.videoIcon)
        val durationText: TextView? = view.findViewById(R.id.durationText)
        val nameText: TextView? = view.findViewById(R.id.nameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val mediaItem = mediaList[position]

        // 显示视频时长
        if (mediaItem.type == MediaType.VIDEO && holder.durationText != null) {
            holder.durationText.visibility = View.VISIBLE
            holder.durationText.text = formatDuration(mediaItem.duration)
        } else if (holder.durationText != null) {
            holder.durationText.visibility = View.GONE
        }

        // 显示视频图标
        if (mediaItem.type == MediaType.VIDEO && holder.videoIcon != null) {
            holder.videoIcon.visibility = View.VISIBLE
        } else if (holder.videoIcon != null) {
            holder.videoIcon.visibility = View.GONE
        }

        // 加载缩略图
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = loadThumbnail(holder.itemView.context, mediaItem)
                withContext(Dispatchers.Main) {
                    holder.imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(mediaItem)
        }
    }

    override fun getItemCount() = mediaList.size

    private fun loadThumbnail(context: Context, mediaItem: MediaItem): Bitmap? {
        return try {
            when (mediaItem.type) {
                MediaType.VIDEO -> {
                    // 尝试多种方式获取视频缩略图
                    try {
                        // 方法1: 使用MediaStore
                        MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver,
                            ContentUris.parseId(mediaItem.uri),
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        )
                    } catch (e: Exception) {
                        // 方法2: 使用MediaMetadataRetriever
                        try {
                            val retriever = MediaMetadataRetriever()
                            if (mediaItem.path.startsWith("content://")) {
                                retriever.setDataSource(context, Uri.parse(mediaItem.path))
                            } else {
                                retriever.setDataSource(mediaItem.path)
                            }
                            retriever.frameAtTime
                        } catch (e: Exception) {
                            // 方法3: 返回默认图片
                            null
                        }
                    }
                }
                MediaType.IMAGE -> {
                    try {
                        MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver,
                            ContentUris.parseId(mediaItem.uri),
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null
                        )
                    } catch (e: Exception) {
                        // 尝试直接加载
                        try {
                            val options = BitmapFactory.Options()
                            options.inSampleSize = 8
                            if (mediaItem.path.startsWith("content://")) {
                                context.contentResolver.openInputStream(Uri.parse(mediaItem.path))?.use {
                                    BitmapFactory.decodeStream(it, null, options)
                                }
                            } else {
                                BitmapFactory.decodeFile(mediaItem.path, options)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDuration(duration: Long): String {
        if (duration <= 0) return ""
        val seconds = duration / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    fun cancelJobs() {
        job?.cancel()
    }
}