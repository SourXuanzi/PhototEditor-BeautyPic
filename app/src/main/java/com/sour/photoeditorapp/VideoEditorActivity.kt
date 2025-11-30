package com.sour.photoeditorapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class VideoEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editor)

        val mediaUri = intent.getStringExtra("mediaUri")
        val mediaPath = intent.getStringExtra("mediaPath")

        // 初始化 Toolbar
        initToolbar()

        // 显示当前编辑的视频信息
        Toast.makeText(this, "正在编辑: ${mediaPath?.substringAfterLast("/")}", Toast.LENGTH_SHORT).show()
    }

    private fun initToolbar() {
        toolbar = findViewById(R.id.toolbar)

        // 设置 Toolbar 为 ActionBar
        setSupportActionBar(toolbar)

        // 启用返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // 设置标题
        supportActionBar?.title = "视频编辑"
    }

    // 处理返回按钮点击
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}