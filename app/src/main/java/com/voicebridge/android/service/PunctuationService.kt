package com.voicebridge.android.service

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflinePunctuation
import com.k2fsa.sherpa.onnx.OfflinePunctuationConfig
import com.k2fsa.sherpa.onnx.OfflinePunctuationModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * CT-Transformer 离线标点恢复服务
 * 职责：
 * 1. 自动定位标点模型文件（Assets 拷贝 / CDN 下载路径）
 * 2. 懒加载标点模型实例
 * 3. 当模型不可用时自动降级到 fallback 标点拼装逻辑
 */
class PunctuationService private constructor() {

    private var offlinePunctuation: OfflinePunctuation? = null
    private var loadFailed = false

    companion object {
        private const val TAG = "PunctuationService"
        const val MODEL_NAME = "punct.int8.onnx"

        @Volatile
        private var instance: PunctuationService? = null

        fun getInstance(): PunctuationService {
            return instance ?: synchronized(this) {
                instance ?: PunctuationService().also { instance = it }
            }
        }
    }

    /**
     * 判断模型是否可用（本地磁盘存在或 Assets 存在）
     */
    fun isAvailable(context: Context): Boolean {
        return getModelPath(context) != null
    }

    /**
     * 恢复标点。若模型缺失或推理故障则返回 null，由调用方走降级路径。
     */
    fun restore(context: Context, text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text
        
        val punct = loadIfNeeded(context) ?: return null
        return try {
            val out = punct.addPunctuation(trimmed)
            if (out.isEmpty()) null else out
        } catch (e: Exception) {
            Log.e(TAG, "标点恢复推理异常: ${e.message}")
            null
        }
    }

    @Synchronized
    private fun loadIfNeeded(context: Context): OfflinePunctuation? {
        if (offlinePunctuation != null) return offlinePunctuation
        if (loadFailed) return null

        val path = getModelPath(context)
        if (path == null) {
            Log.w(TAG, "标点模型文件 $MODEL_NAME 缺失，将降级标点")
            loadFailed = true
            return null
        }

        return try {
            val modelConfig = OfflinePunctuationModelConfig(
                ctTransformer = path,
                numThreads = 2
            )
            val config = OfflinePunctuationConfig(model = modelConfig)
            val p = OfflinePunctuation(config = config)
            Log.i(TAG, "CT-Transformer 标点模型就绪")
            offlinePunctuation = p
            p
        } catch (e: Exception) {
            Log.e(TAG, "加载标点模型异常: ${e.message}")
            loadFailed = true
            null
        }
    }

    @Synchronized
    fun unload() {
        offlinePunctuation = null
        loadFailed = false
        Log.i(TAG, "标点引擎资源已释放")
    }

    /**
     * 动态查找并返回标点模型的存储系统绝对路径。
     * 支持 Assets 拷贝兜底。
     */
    private fun getModelPath(context: Context): String? {
        val destDir = File(context.filesDir, "Models")
        if (!destDir.exists()) destDir.mkdirs()
        
        val destFile = File(destDir, MODEL_NAME)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.absolutePath
        }

        // 开发/测试模式 fallback：从 assets 中拷贝到存储区
        return try {
            context.assets.open(MODEL_NAME).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "从 Assets 成功部署模型: $MODEL_NAME")
            destFile.absolutePath
        } catch (e: IOException) {
            // 如果 assets 中也没有，说明必须依赖动态下载（国内CDN/PAD）
            null
        }
    }
}
