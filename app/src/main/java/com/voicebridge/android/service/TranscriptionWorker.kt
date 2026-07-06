package com.voicebridge.android.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voicebridge.android.data.db.VoiceBridgeDatabase

/**
 * 语音转写后台断点重调度 Worker
 * 对应 iOS 侧 BGProcessingTask
 * 职责：
 * 1. 当 WorkManager 执行后台扫描时，自动拉起未完成的 ASR 或声纹分离任务
 * 2. 绑定前台保活服务进行串行处理
 */
class TranscriptionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "TranscriptionWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "WorkManager 后台任务拉起")
        
        val context = applicationContext
        // 1. 获取 Room 数据库实例
        // 为了简便，我们可以直接在 Worker 中通过 Room.databaseBuilder() 初始化，
        // 或者依赖注入。这里我们通过统一的入口点获取
        val db = try {
            // 我们暂用反射或者静态构建，为了确保 Hilt/Room 能跑，我们在此处提供一个静态获取 Database 实例的逻辑
            // 为了安全，我们在 App 中提供了静态获取（也可直接通过 Room 动态实例化）
            // 在这里我们利用 Room.databaseBuilder 静态加载 db 
            val databaseFile = context.getDatabasePath("voicebridge.db")
            if (databaseFile.exists()) {
                androidx.room.Room.databaseBuilder(
                    context,
                    VoiceBridgeDatabase::class.java,
                    "voicebridge.db"
                ).build()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        if (db == null) {
            Log.e(TAG, "未能在后台 Worker 中加载数据库")
            return Result.failure()
        }

        // 2. 配置并拉起 TaskQueue
        val importQueue = ImportTaskQueue.getInstance()
        val diarizationQueue = DiarizationTaskQueue.getInstance()
        
        importQueue.configure(db)
        diarizationQueue.configure(db)

        // 3. 执行待决任务
        // 如果当前有任务，在后台转译期间挂载前台服务保活
        try {
            // 扫描未决任务并入队
            importQueue.resumeIncompleteJobs(context)
            diarizationQueue.resumeIncompleteJobs(context)

            // 循环监听队列是否全部处理完
            var attempts = 0
            while ((importQueue.hasIncompleteJobs || diarizationQueue.hasIncompleteJobs) && attempts < 60) {
                // 如果发现有进行中的任务，则前台保活
                val activeMeetingId = importQueue.activeJobId.value ?: diarizationQueue.activeJobId.value
                if (activeMeetingId != null) {
                    TranscriptionForegroundService.startService(context, "后台任务整理中")
                }
                
                // 每次轮询睡眠 10 秒，最多等待 10 分钟 (60次)
                kotlinx.coroutines.delay(10000L)
                attempts++
            }
        } catch (e: Exception) {
            Log.e(TAG, "后台转译 Worker 推理异常: ${e.message}")
        } finally {
            TranscriptionForegroundService.stopService(context)
            db.close()
        }

        return Result.success()
    }
}
