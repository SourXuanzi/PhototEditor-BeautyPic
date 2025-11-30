package com.sour.photoeditorapp

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
        val mediaList = when (mediaType) {
            "image" -> {
                toolbar.title = "选择图片"
                MediaLoader.loadImages(this)
            }
            "video" -> {
                toolbar.title = "选择视频"
                MediaLoader.loadVideos(this)
            }
            else -> emptyList()
        }

        val adapter = MediaAdapter(mediaList) { mediaItem ->
            // 处理媒体项点击，跳转到编辑界面
            navigateToEditor(mediaItem)
        }

        recyclerView.adapter = adapter
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