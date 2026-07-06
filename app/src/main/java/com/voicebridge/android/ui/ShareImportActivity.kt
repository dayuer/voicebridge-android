package com.voicebridge.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.MeetingRecordEntity
import com.voicebridge.android.service.ImportTaskQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID

/**
 * 响应系统级分享导入的 Activity
 * 对标 iOS 侧的 Share Extension (ShareViewController)
 * 职责：
 * 1. 拦截 android.intent.action.SEND 分享面板
 * 2. 拷贝外部共享的音频 Uri 至 App 的沙盒私有存储中
 * 3. 物理入库并入队 FIFO 转写队列
 */
class ShareImportActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareImportActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = this.intent
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type.startsWith("audio/")) {
                handleAudioShare(intent)
            } else {
                Toast.makeText(this, "不受支持的分享类型", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun handleAudioShare(intent: Intent) {
        val audioUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (audioUri == null) {
            Toast.makeText(this, "未能获取到共享文件", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Toast.makeText(this, "正在为您导入共享音频...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                // 1. 复制文件到 Documents/Recordings/
                val localPath = copySharedFileToSandbox(audioUri)
                if (localPath == null) {
                    Toast.makeText(this@ShareImportActivity, "音频导入复制失败", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                // 2. 写入 Room 数据库
                val meetingId = UUID.randomUUID().toString()
                val fileName = File(localPath).name
                val meeting = MeetingRecordEntity(
                    id = meetingId,
                    title = "导入-$fileName",
                    startTime = Date(),
                    audioFilePath = localPath,
                    importProgress = 0.0,
                    isCompleted = false
                )

                // 我们动态静态构造 Room，这里采用 Hilt 或 buildDatabase
                val db = androidx.room.Room.databaseBuilder(
                    applicationContext,
                    VoiceBridgeDatabase::class.java,
                    "voicebridge.db"
                ).build()

                withContext(Dispatchers.IO) {
                    db.meetingRecordDao().insert(meeting)
                }

                // 3. 入队转写
                val queue = ImportTaskQueue.getInstance()
                queue.configure(db)
                queue.enqueue(
                    applicationContext,
                    ImportTaskQueue.ImportJob(meetingId, localPath, null)
                )

                Toast.makeText(this@ShareImportActivity, "导入成功，已在后台自动排队转写", Toast.LENGTH_LONG).show()
                
                // 4. 拉起主界面
                val mainIntent = Intent(this@ShareImportActivity, Class.forName("com.voicebridge.android.MainActivity")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(mainIntent)

            } catch (e: Exception) {
                Log.e(TAG, "处理分享文件异常", e)
                Toast.makeText(this@ShareImportActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }

    private suspend fun copySharedFileToSandbox(uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = contentResolver
        val cursor = resolver.query(uri, null, null, null, null)
        val displayName = cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        } ?: "shared_audio_${System.currentTimeMillis()}.m4a"

        val recordingsDir = File(filesDir, "Documents/Recordings")
        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        val destFile = File(recordingsDir, displayName)
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            // 返回相对于 filesDir 的相对路径，以供持久化与 iOS 设计对拍一致
            "Documents/Recordings/$displayName"
        } catch (e: Exception) {
            Log.e(TAG, "复制文件至沙盒异常", e)
            null
        }
    }
}
