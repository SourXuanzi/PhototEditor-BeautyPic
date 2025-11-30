package com.sour.photoeditorapp

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

object MediaLoader {

    fun loadImages(context: Context): List<MediaItem> {
        return loadMedia(context, MediaType.IMAGE)
    }

    fun loadVideos(context: Context): List<MediaItem> {
        return loadMedia(context, MediaType.VIDEO)
    }

    private fun loadMedia(context: Context, mediaType: MediaType): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        val collection = when (mediaType) {
            MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        // 针对不同 Android 版本使用不同的列
        val projection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ 使用 RELATIVE_PATH 而不是 DATA
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.RELATIVE_PATH
            ).apply {
                if (mediaType == MediaType.VIDEO) {
                    plus(MediaStore.Video.VideoColumns.DURATION)
                }
            }
        } else {
            // Android 9 及以下使用 DATA
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATA
            ).apply {
                if (mediaType == MediaType.VIDEO) {
                    plus(MediaStore.Video.VideoColumns.DURATION)
                }
            }
        }

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                // 根据 Android 版本选择路径列
                val pathColumn = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                }

                val durationColumn = if (mediaType == MediaType.VIDEO) {
                    cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                } else -1

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val dateAdded = cursor.getLong(dateColumn)
                        val path = cursor.getString(pathColumn)
                        val duration = if (durationColumn != -1 && !cursor.isNull(durationColumn)) {
                            cursor.getLong(durationColumn)
                        } else 0

                        val contentUri = ContentUris.withAppendedId(collection, id)

                        mediaList.add(
                            MediaItem(
                                id = id,
                                uri = contentUri,
                                path = path ?: "",
                                name = name ?: "Unknown",
                                dateAdded = dateAdded,
                                type = mediaType,
                                duration = duration
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("MediaLoader", "Error reading media item: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaLoader", "Error querying media: ${e.message}")
        }

        return mediaList
    }
}