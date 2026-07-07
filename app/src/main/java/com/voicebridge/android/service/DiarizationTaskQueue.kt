package com.voicebridge.android.service

import android.content.Context
import android.util.Log
import com.voicebridge.android.util.AppLog
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.take
import java.io.File
import java.io.IOException

/**
 * 说话人分离与声纹提取后台串行执行队列 (Stage B)
 * 迁移自 iOS 侧 DiarizationTaskQueue.swift
 */
class DiarizationTaskQueue private constructor() {

    private val TAG = "DiarizationTaskQueue"
    private val queueScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    private val queue = ArrayList<Pair<String, String>>() // Pair(meetingId, audioFilePath)
    private var activeJobJob: Job? = null

    private val _activeJobId = MutableStateFlow<String?>(null)
    val activeJobId: StateFlow<String?> = _activeJobId

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount

    private var database: VoiceBridgeDatabase? = null

    companion object {
        @Volatile
        private var instance: DiarizationTaskQueue? = null

        fun getInstance(): DiarizationTaskQueue {
            return instance ?: synchronized(this) {
                instance ?: DiarizationTaskQueue().also { instance = it }
            }
        }
    }

    /**
     * 配置注入数据库
     */
    fun configure(db: VoiceBridgeDatabase) {
        this.database = db
    }

    /**
     * 队列中和执行中是否有任务
     */
    val hasIncompleteJobs: Boolean
        get() = _activeJobId.value != null || queue.isNotEmpty()

    /**
     * 重启 App 时自动恢复未决的声纹聚类任务
     */
    fun resumeIncompleteJobs(context: Context) {
        val db = database ?: return
        queueScope.launch {
            // 获取所有 ASR 结束（isCompleted = true）且声纹分离处于 pending(1) 或 running(2) 的会议
            val all = db.meetingRecordDao().getAllMeetingsComplete().firstOrNull() ?: emptyList()
            val incomplete = all.filter {
                it.meetingRecord.isCompleted && (it.meetingRecord.diarizationState == 1 || it.meetingRecord.diarizationState == 2)
            }
            Log.i(TAG, "Diarization 扫描发现 ${incomplete.size} 个未完成的声纹分离任务待恢复")
            for (item in incomplete) {
                val path = item.meetingRecord.audioFilePath ?: continue
                enqueue(context, item.meetingRecord.id, path)
            }
        }
    }

    /**
     * 任务入队
     */
    fun enqueue(context: Context, meetingId: String, audioFilePath: String?) {
        if (audioFilePath.isNullOrEmpty()) {
            Log.w(TAG, "音频文件路径为空，跳过 Diarization 入队: $meetingId")
            markJobStatus(meetingId, 3) // done
            return
        }

        queueScope.launch {
            mutex.withLock {
                if (_activeJobId.value == meetingId || queue.any { it.first == meetingId }) {
                    return@launch
                }
                // 标记状态为 pending (1)
                markJobStatus(meetingId, 1)
                queue.add(Pair(meetingId, audioFilePath))
                _pendingCount.value = queue.size + (if (_activeJobId.value != null) 1 else 0)
                Log.i(TAG, "声纹任务入队: $meetingId, 队列长度: ${queue.size}")
                AppLog.voiceprint("声纹分离任务入队，队列长度 ${queue.size}")
            }
            processNextIfIdle(context)
        }
    }

    /**
     * 联动删除：取消或移除任务
     */
    fun removeJob(meetingId: String) {
        queueScope.launch {
            mutex.withLock {
                queue.removeAll { it.first == meetingId }
                if (_activeJobId.value == meetingId) {
                    activeJobJob?.cancel()
                    activeJobJob = null
                    _activeJobId.value = null
                }
                _pendingCount.value = queue.size + (if (_activeJobId.value != null) 1 else 0)
            }
        }
    }

    private suspend fun processNextIfIdle(context: Context) {
        mutex.withLock {
            if (_activeJobId.value != null) return
            if (queue.isEmpty()) return

            val next = queue.removeAt(0)
            _activeJobId.value = next.first
            _pendingCount.value = queue.size + 1

            activeJobJob = queueScope.launch {
                try {
                    executeJob(context, next.first, next.second)
                } catch (e: Exception) {
                    Log.e(TAG, "声纹聚类任务执行失败: ${next.first}", e)
                    AppLog.error("声纹聚类任务执行失败: ${e.message}")
                    markJobStatus(next.first, 4) // failed
                } finally {
                    mutex.withLock {
                        _activeJobId.value = null
                        activeJobJob = null
                        _pendingCount.value = queue.size
                    }
                    processNextIfIdle(context)
                }
            }
        }
    }

    private suspend fun executeJob(context: Context, meetingId: String, audioPath: String) = withContext(Dispatchers.IO) {
        val db = database ?: throw IOException("Database has not been configured")
        
        // 1. 标记状态为 running (2)
        markJobStatus(meetingId, 2)

        val audioFile = File(audioPath)
        val absolutePath = if (audioFile.isAbsolute) {
            audioFile.absolutePath
        } else {
            File(context.filesDir, audioPath).absolutePath
        }

        if (!File(absolutePath).exists()) {
            throw IOException("音频文件丢失: $absolutePath")
        }

        Log.i(TAG, "开始计算声纹与话轮对齐...")
        AppLog.voiceprint("开始计算声纹与话轮对齐")

        // 调用 Stage B 话轮化与声纹落库总管
        TranscriptFinalizer.applyDiarization(
            context = context,
            meetingId = meetingId,
            audioPath = absolutePath,
            db = db
        )
    }

    private fun markJobStatus(meetingId: String, state: Int) {
        val db = database ?: return
        queueScope.launch(Dispatchers.IO) {
            val meeting = db.meetingRecordDao().getById(meetingId)
            if (meeting != null) {
                db.meetingRecordDao().update(
                    meeting.copy(diarizationState = state)
                )
            }
        }
    }

    // 辅助 Flow 拓展取首个值
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrNull(): T? {
        var result: T? = null
        this.take(1).collect { result = it }
        return result
    }
}
