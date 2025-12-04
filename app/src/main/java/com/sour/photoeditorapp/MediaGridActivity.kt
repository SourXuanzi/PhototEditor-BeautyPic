package com.sour.photoeditorapp

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaGridActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_grid)

        initViews()
        loadMedia()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.mediaRecyclerView)
        toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 设置网格布局
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.addItemDecoration(SpacingItemDecoration(2))
    }

    private fun loadMedia() {
        val mediaType = intent.getStringExtra("mediaType")

        // 使用协程加载媒体文件
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val mediaList = when (mediaType) {
                    "image" -> {
                        toolbar.title = "选择图片"
                        withContext(Dispatchers.IO) {
                            MediaLoader.loadImages(this@MediaGridActivity)
                        }
                    }
                    "video" -> {
                        toolbar.title = "选择视频"
                        withContext(Dispatchers.IO) {
                            MediaLoader.loadVideos(this@MediaGridActivity)
                        }
                    }
                    else -> emptyList()
                }

                val adapter = MediaAdapter(mediaList) { mediaItem ->
                    // 处理媒体项点击，跳转到编辑界面
                    navigateToEditor(mediaItem)
                }

                recyclerView.adapter = adapter

                // 显示加载结果
                if (mediaList.isEmpty()) {
                    toolbar.subtitle = "未找到媒体文件"
                } else {
                    toolbar.subtitle = "找到 ${mediaList.size} 个文件"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                toolbar.subtitle = "加载失败: ${e.message}"
            }
        }
    }

    private fun navigateToEditor(mediaItem: MediaItem) {
        val intent = when (mediaItem.type) {
            MediaType.IMAGE -> Intent(this, ImageEditorActivity::class.java)
            MediaType.VIDEO -> Intent(this, VideoEditorActivity::class.java)
        }.apply {
            putExtra("mediaUri", mediaItem.uri.toString())
            putExtra("mediaPath", mediaItem.path)
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// 网格间距装饰器
class SpacingItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.left = spacing
        outRect.right = spacing
        outRect.top = spacing
        outRect.bottom = spacing
    }
}