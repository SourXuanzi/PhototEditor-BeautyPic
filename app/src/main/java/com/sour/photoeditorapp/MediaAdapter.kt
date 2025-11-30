package com.sour.photoeditorapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MediaAdapter(
    private val mediaList: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.mediaThumbnail)
        val videoDuration: TextView = itemView.findViewById(R.id.videoDuration)
        val selectedOverlay: View = itemView.findViewById(R.id.selectedOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_grid, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaItem = mediaList[position]

        // 使用 Glide 加载缩略图
        Glide.with(holder.itemView.context)
            .load(mediaItem.uri)
            .override(300, 300)
            .centerCrop()
            .into(holder.thumbnail)

        // 如果是视频，显示时长
        if (mediaItem.type == MediaType.VIDEO) {
            holder.videoDuration.visibility = View.VISIBLE
            holder.videoDuration.text = formatDuration(mediaItem.duration)
        } else {
            holder.videoDuration.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(mediaItem)
        }
    }

    override fun getItemCount(): Int = mediaList.size

    private fun formatDuration(duration: Long): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format("%02d:%02d", minutes, seconds % 60)
        }
    }
}