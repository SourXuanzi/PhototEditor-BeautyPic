package com.sour.photoeditorapp

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaLoader {

    suspend fun loadImages(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        val imageList = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                imageList.add(
                    MediaItem(
                        uri = contentUri,
                        path = contentUri.toString(),
                        type = MediaType.IMAGE,
                        name = name,
                        dateAdded = dateAdded,
                        size = size
                    )
                )
            }
        }

        Log.d("MediaLoader", "加载了 ${imageList.size} 张图片")
        return@withContext imageList
    }

    suspend fun loadVideos(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val duration = cursor.getLong(durationColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )

                videoList.add(
                    MediaItem(
                        uri = contentUri,
                        path = contentUri.toString(),
                        type = MediaType.VIDEO,
                        name = name,
                        dateAdded = dateAdded,
                        size = size,
                        duration = duration
                    )
                )

                Log.d("MediaLoader", "找到视频: $name, 大小: $size, 时长: $duration")
            }
        }

        Log.d("MediaLoader", "加载了 ${videoList.size} 个视频")
        return@withContext videoList
    }
}