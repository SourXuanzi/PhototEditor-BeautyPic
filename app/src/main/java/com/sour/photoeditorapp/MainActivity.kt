package com.sour.photoeditorapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.MediaScannerConnection
import java.io.File

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Toast.makeText(this, "BeautyPic 启动成功！", Toast.LENGTH_SHORT).show()
    }

    // 检查是否有适当的权限
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要细粒度权限
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 及以下需要通用存储权限
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 请求适当的权限
    private fun requestStoragePermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 请求细粒度权限
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            // Android 12 及以下请求通用存储权限
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions,
            PERMISSION_REQUEST_CODE
        )
    }

    // 处理权限请求结果
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要存储权限来访问相册", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 导入照片按钮点击事件
    fun onImportPhotoClick(view: View) {
        if (hasStoragePermission()) {
            val intent = Intent(this, MediaGridActivity::class.java).apply {
                putExtra("mediaType", "image")
            }
            startActivity(intent)
        } else {
            requestStoragePermission()
        }
    }

    // 修视频按钮点击事件
    fun onEditVideoClick(view: View) {
        if (hasStoragePermission()) {
            val intent = Intent(this, MediaGridActivity::class.java).apply {
                putExtra("mediaType", "video")
            }
            startActivity(intent)
        } else {
            requestStoragePermission()
        }
    }

    // 其他按钮点击事件正在开发
    fun onCollageClick(view: View) {
        Toast.makeText(this, "拼图功能开发中", Toast.LENGTH_SHORT).show()
    }

    fun onFilterClick(view: View) {
        Toast.makeText(this, "滤镜功能开发中", Toast.LENGTH_SHORT).show()
    }

    fun onStickerClick(view: View) {
        Toast.makeText(this, "贴纸功能开发中", Toast.LENGTH_SHORT).show()
    }

    private fun scanMediaFile(filePath: String) {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(filePath),
            null
        ) { path, uri ->
            println("媒体文件已扫描: path=$path, uri=$uri")
            Toast.makeText(this, "媒体文件已重新扫描", Toast.LENGTH_SHORT).show()
        }
    }

//    // 可以添加一个调试按钮或直接调用
//    fun scanAllMedia(view: View) {
//        // 扫描 DCIM/Camera 目录
//        val dcimDir = File("/sdcard/DCIM/Camera")
//        if (dcimDir.exists() && dcimDir.isDirectory) {
//            dcimDir.listFiles()?.forEach { file ->
//                if (file.isFile && (file.name.endsWith(".jpg") ||
//                            file.name.endsWith(".jpeg") ||
//                            file.name.endsWith(".png") ||
//                            file.name.endsWith(".mp4"))) {
//                    scanMediaFile(file.absolutePath)
//                }
//            }
//        }
//        Toast.makeText(this, "正在扫描媒体文件...", Toast.LENGTH_SHORT).show()
//    }
}
