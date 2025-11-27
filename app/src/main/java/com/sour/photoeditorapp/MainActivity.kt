package com.sour.photoeditorapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 确认应用正常启动
        Toast.makeText(this, "BeautyPic 启动成功！", Toast.LENGTH_SHORT).show()
    }

    // 导入照片按钮点击事件
    fun onImportPhotoClick(view: View) {
        Toast.makeText(this, "导入照片功能开发中", Toast.LENGTH_SHORT).show()
        // 后续可以在这里实现图片选择逻辑
    }

    // 修视频按钮点击事件
    fun onEditVideoClick(view: View) {
        Toast.makeText(this, "修视频功能开发中", Toast.LENGTH_SHORT).show()
    }

    // 拼图按钮点击事件
    fun onCollageClick(view: View) {
        Toast.makeText(this, "拼图功能开发中", Toast.LENGTH_SHORT).show()
    }

    // 滤镜按钮点击事件
    fun onFilterClick(view: View) {
        Toast.makeText(this, "滤镜功能开发中", Toast.LENGTH_SHORT).show()
    }

    // 贴纸按钮点击事件
    fun onStickerClick(view: View) {
        Toast.makeText(this, "贴纸功能开发中", Toast.LENGTH_SHORT).show()
    }
}