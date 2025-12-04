package com.sour.photoeditorapp

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val path: String,
    val type: MediaType,
    val name: String? = null,
    val dateAdded: Long = 0,
    val size: Long = 0,
    val duration: Long = 0
)