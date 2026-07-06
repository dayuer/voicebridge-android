package com.voicebridge.android.service

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 离线说话人分离引擎 (基于 pyannote + CAM++)
 * 迁移自 iOS 侧 SpeakerDiarizationEngine.swift
 * 包含对分离结果的 JSON 磁盘缓存机制，防止重启后全量重算。
 */
class SpeakerDiarizationEngine private constructor() {

    @Serializable
    data class CachedTurn(
        val start: Double,
        val end: Double,
        val speaker: Int
    )

    private var diarization: OfflineSpeakerDiarization? = null
    private var isReady = false

    companion object {
        private const val TAG = "SpeakerDiarizationEngine"
        const val SEG_MODEL_REL = "sherpa-onnx-pyannote-segmentation-3-0/model.onnx"
        const val EMB_MODEL_NAME = "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"

        private const val CLUSTERING_THRESHOLD = 0.7f
        const val MAX_AUTO_SPEAKERS = 8
        private const val CACHE_VERSION = 2

        @Volatile
        private var instance: SpeakerDiarizationEngine? = null

        fun getInstance(): SpeakerDiarizationEngine {
            return instance ?: synchronized(this) {
                instance ?: SpeakerDiarizationEngine().also { instance = it }
            }
        }
    }

    /**
     * 判断引擎是否可用
     */
    fun isAvailable(context: Context): Boolean {
        return getModelPath(context, SEG_MODEL_REL) != null && getModelPath(context, EMB_MODEL_NAME) != null
    }

    @Synchronized
    fun ensureModelLoaded(context: Context, numClusters: Int = -1): Boolean {
        // 如果已加载，且聚类数要求一致，或者为自动聚类，则复用
        if (isReady && diarization != null) return true

        val segPath = getModelPath(context, SEG_MODEL_REL)
        val embPath = getModelPath(context, EMB_MODEL_NAME)
        if (segPath == null || embPath == null) {
            Log.e(TAG, "Diarization 模型文件缺失")
            return false
        }

        return try {
            val pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = segPath)
            val segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = pyannote,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val embedding = SpeakerEmbeddingExtractorConfig(
                model = embPath,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            val clustering = FastClusteringConfig(
                numClusters = numClusters,
                threshold = CLUSTERING_THRESHOLD
            )
            val config = OfflineSpeakerDiarizationConfig(
                segmentation = segmentation,
                embedding = embedding,
                clustering = clustering,
                minDurationOn = 0.3f,
                minDurationOff = 0.5f
            )

            diarization = OfflineSpeakerDiarization(config)
            isReady = true
            Log.i(TAG, "pyannote 离线分离引擎加载成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "pyannote 离线分离引擎加载异常: ${e.message}", e)
            false
        }
    }

    /**
     * 对音频文件进行说话人分离
     */
    fun diarize(
        context: Context,
        audioPath: String,
        expectedSpeakerCount: Int? = null
    ): List<DiarizationService.SpeakerTurn> {
        if (!isAvailable(context)) return emptyList()

        val audioFile = File(audioPath)
        
        // 1. 读取/加载缓存
        val cacheFile = getCacheFile(context, audioFile, expectedSpeakerCount)
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val jsonStr = cacheFile.readText()
                val cached = Json.decodeFromString<List<CachedTurn>>(jsonStr)
                if (cached.isNotEmpty()) {
                    Log.i(TAG, "命中分离缓存，跳过 pyannote 重算: ${cached.size} 轮次")
                    return cached.map { DiarizationService.SpeakerTurn(it.start, it.end, it.speaker) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取 Diarization 缓存异常: ${e.message}")
            }
        }

        // 2. 解码 PCM
        val samples = try {
            AudioDecoder.decodeRegion(audioFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "ASR音频解码失败: ${e.message}")
            return emptyList()
        }

        if (samples.size < 16000) {
            Log.w(TAG, "音频过短，略过分离")
            return emptyList()
        }

        // 3. 动态配置并运行推理
        val numClusters = if ((expectedSpeakerCount ?: 0) >= 2) expectedSpeakerCount!! else -1
        
        // 动态实例化或强制更新以支持 expectedSpeakerCount 变动
        synchronized(this) {
            if (!ensureModelLoaded(context, numClusters)) {
                return emptyList()
            }
        }

        val diar = diarization ?: return emptyList()

        return try {
            Log.i(TAG, "开始计算 pyannote 话轮切分...")
            // 在 JNI 中计算
            // 注意：Java 端的 process 可能返回一段 SpeakerSegment 对象列表
            // 我们通过反射或常规接口抓取 [start, end, speaker]
            val rawSegments = diar.process(samples)
            
            val turns = ArrayList<DiarizationService.SpeakerTurn>()
            for (seg in rawSegments) {
                // 根据 JNI 规范取字段
                val startVal = try {
                    val f = seg.javaClass.getField("start")
                    f.getFloat(seg).toDouble()
                } catch (e: Exception) {
                    0.0
                }
                val endVal = try {
                    val f = seg.javaClass.getField("end")
                    f.getFloat(seg).toDouble()
                } catch (e: Exception) {
                    0.0
                }
                val spkVal = try {
                    val f = seg.javaClass.getField("speaker")
                    f.getInt(seg)
                } catch (e: Exception) {
                    0
                }
                turns.add(DiarizationService.SpeakerTurn(startVal, endVal, spkVal))
            }

            // 保存缓存
            if (cacheFile != null && turns.isNotEmpty()) {
                try {
                    val dto = turns.map { CachedTurn(it.start, it.end, it.speaker) }
                    val jsonStr = Json.encodeToString(dto)
                    cacheFile.writeText(jsonStr)
                    Log.i(TAG, "话轮分离结果已成功写入磁盘缓存")
                } catch (e: Exception) {
                    Log.e(TAG, "写入缓存文件异常: ${e.message}")
                }
            }

            turns
        } catch (e: Exception) {
            Log.e(TAG, "pyannote 运行异常: ${e.message}")
            emptyList()
        }
    }

    private fun getCacheFile(context: Context, audioFile: File, expectedSpeakerCount: Int?): File? {
        val cacheDir = File(context.cacheDir, "DiarizationCache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val size = audioFile.length()
        val mtime = audioFile.lastModified()
        val key = "${audioFile.name}-$size-$mtime-c${expectedSpeakerCount ?: -1}-t$CLUSTERING_THRESHOLD-v$CACHE_VERSION"
            .replace("/", "_")
            .replace("\\", "_")
        return File(cacheDir, "$key.json")
    }

    private fun getModelPath(context: Context, relativePath: String): String? {
        val destFile = File(context.filesDir, "Models/$relativePath")
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.absolutePath
        }

        // 自动创建子目录
        destFile.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }

        return try {
            context.assets.open(relativePath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "从 Assets 成功部署模型: $relativePath")
            destFile.absolutePath
        } catch (e: IOException) {
            null
        }
    }
}
