package com.sour.photoeditorapp

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val dateAdded: Long,
    val type: MediaType, // IMAGE or VIDEO
    val duration: Long = 0 // 仅视频使用
)

enum class MediaType {
    IMAGE, VIDEO
}