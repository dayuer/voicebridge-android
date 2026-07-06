package com.voicebridge.android.service

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 离线声纹向量提取器
 * 职责：
 * 1. 自动定位 CAM++ 中英双语声纹模型文件 3dspeaker_speech_campplus...
 * 2. 封装 sherpa-onnx SpeakerEmbeddingExtractor 接口
 * 3. 提取 16kHz Float32 PCM 声纹向量（512维），在 <1.0s 时自动过滤保护。
 */
class SpeakerEmbeddingService private constructor() {

    private var extractor: SpeakerEmbeddingExtractor? = null
    private var isReady = false

    companion object {
        private const val TAG = "SpeakerEmbeddingService"
        const val MODEL_NAME = "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"

        @Volatile
        private var instance: SpeakerEmbeddingService? = null

        fun getInstance(): SpeakerEmbeddingService {
            return instance ?: synchronized(this) {
                instance ?: SpeakerEmbeddingService().also { instance = it }
            }
        }
    }

    /**
     * 判断模型是否存在
     */
    fun isAvailable(context: Context): Boolean {
        return getModelPath(context) != null
    }

    /**
     * 声纹向量维度，CAM++ 返回 512
     */
    fun getEmbeddingDimension(): Int {
        return extractor?.dim() ?: 0
    }

    @Synchronized
    fun ensureModelLoaded(context: Context): Boolean {
        if (isReady) return true
        val path = getModelPath(context)
        if (path == null) {
            Log.e(TAG, "声纹提取模型文件 $MODEL_NAME 缺失")
            return false
        }
        return try {
            val config = SpeakerEmbeddingExtractorConfig(
                model = path,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            extractor = SpeakerEmbeddingExtractor(config = config)
            isReady = true
            Log.i(TAG, "声纹提取引擎初始化就绪，维度: ${extractor?.dim()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "声纹提取引擎初始化失败: ${e.message}")
            false
        }
    }

    /**
     * 从 16kHz mono Float PCM 样本中提取归一化声纹向量
     */
    @Synchronized
    fun extractEmbedding(context: Context, samples: FloatArray, sampleRate: Int = 16000): FloatArray? {
        if (!ensureModelLoaded(context)) return null
        val ext = extractor ?: return null

        // CAM++ 在小于 1.0 秒（即 16000 样本）上极不准确，直接过滤
        if (samples.size < sampleRate) {
            Log.d(TAG, "样本数 ${samples.size} 过少，略过声纹提取")
            return null
        }

        return try {
            val stream = ext.createStream()
            stream.acceptWaveform(samples, sampleRate)
            stream.inputFinished()
            val embedding = ext.compute(stream)
            stream.release()
            
            if (embedding == null || embedding.isEmpty()) {
                null
            } else {
                embedding
            }
        } catch (e: Exception) {
            Log.e(TAG, "声纹提取计算异常: ${e.message}")
            null
        }
    }

    /**
     * 定位并部署声纹提取模型
     */
    private fun getModelPath(context: Context): String? {
        val destDir = File(context.filesDir, "Models")
        if (!destDir.exists()) destDir.mkdirs()
        
        val destFile = File(destDir, MODEL_NAME)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.absolutePath
        }

        return try {
            context.assets.open(MODEL_NAME).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "从 Assets 成功部署模型: $MODEL_NAME")
            destFile.absolutePath
        } catch (e: IOException) {
            null
        }
    }
}
