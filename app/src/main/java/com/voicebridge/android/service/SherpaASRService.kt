package com.voicebridge.android.service

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class SherpaTranscriptionSegment(
    val text: String,
    val startTime: Float,
    val duration: Float,
    val language: String,
    val emotion: String
)

/**
 * sherpa-onnx 统一 ASR 服务 — 对应 iOS 侧 SherpaASRService.swift
 */
class SherpaASRService private constructor() {

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady

    private val _isModelBusy = MutableStateFlow(false)
    val isModelBusy: StateFlow<Boolean> = _isModelBusy

    private val _modelStatusText = MutableStateFlow("就绪")
    val modelStatusText: StateFlow<String> = _modelStatusText

    private val _modelError = MutableStateFlow<String?>(null)
    val modelError: StateFlow<String?> = _modelError

    private val _downloadProgress = MutableStateFlow(0.0)
    val downloadProgress: StateFlow<Double> = _downloadProgress

    private var offlineRecognizer: OfflineRecognizer? = null

    companion object {
        private const val TAG = "SherpaASRService"
        const val SENSEVOICE_MODEL = "model.int8.onnx"
        const val SENSEVOICE_TOKENS = "sense-voice-tokens.txt"
        const val VAD_MODEL = "silero_vad.onnx"

        @Volatile
        private var instance: SherpaASRService? = null

        fun getInstance(): SherpaASRService {
            return instance ?: synchronized(this) {
                instance ?: SherpaASRService().also { instance = it }
            }
        }
    }

    /**
     * 判断 ASR 模型是否可用（本地磁盘存在或 Assets 存在）
     */
    fun isASRPackAvailable(context: Context): Boolean {
        val modelPath = getFilePath(context, SENSEVOICE_MODEL)
        val tokensPath = getFilePath(context, SENSEVOICE_TOKENS)
        return modelPath != null && tokensPath != null
    }

    /**
     * 加载离线 SenseVoice ASR 模型
     */
    suspend fun loadModels(context: Context) = withContext(Dispatchers.IO) {
        if (_isModelReady.value) return@withContext

        _isModelBusy.value = true
        _modelStatusText.value = "正在加载语音模型…"
        _modelError.value = null
        _downloadProgress.value = 0.0

        val senseVoiceModel = getFilePath(context, SENSEVOICE_MODEL)
        val senseVoiceTokens = getFilePath(context, SENSEVOICE_TOKENS)

        if (senseVoiceModel == null || senseVoiceTokens == null) {
            _modelError.value = "语音模型文件缺失"
            _modelStatusText.value = "语音模型加载失败"
            _isModelBusy.value = false
            return@withContext
        }

        try {
            Log.i(TAG, "开始初始化离线 SenseVoice 引擎...")
            
            val senseVoiceConfig = OfflineSenseVoiceModelConfig(
                model = senseVoiceModel,
                language = "", // 自动识别
                useInverseTextNormalization = true
            )

            val modelConfig = OfflineModelConfig(
                tokens = senseVoiceTokens,
                numThreads = 2,
                debug = false,
                senseVoice = senseVoiceConfig
            )

            val recognizerConfig = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )

            offlineRecognizer = OfflineRecognizer(config = recognizerConfig)
            _isModelReady.value = true
            _modelStatusText.value = "就绪"
            _downloadProgress.value = 1.0
            Log.i(TAG, "SenseVoice ASR 引擎加载成功")
        } catch (e: Exception) {
            Log.e(TAG, "SenseVoice ASR 初始化失败: ${e.message}", e)
            _modelError.value = "引擎初始化失败: ${e.message}"
            _modelStatusText.value = "模型加载失败"
            _isModelReady.value = false
        } finally {
            _isModelBusy.value = false
        }
    }

    /**
     * 对原始 Float PCM 样本直接执行离线识别
     */
    fun transcribeSamples(samples: FloatArray, sampleRate: Int = 16000): SherpaTranscriptionSegment? {
        val recognizer = offlineRecognizer ?: return null
        if (samples.isEmpty()) return null

        return try {
            val stream = recognizer.createStream()
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            val text = result.text.trim()
            stream.release()

            if (text.isEmpty()) null else SherpaTranscriptionSegment(
                text = text,
                startTime = 0f,
                duration = samples.size.toFloat() / sampleRate,
                language = result.lang ?: "",
                emotion = result.emotion ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "识别失败: ${e.message}")
            null
        }
    }

    /**
     * 对完整音频文件执行离线转录（包含 VAD 静音切片 + 断点续传）
     */
    suspend fun transcribeAudioFile(
        context: Context,
        audioPath: String,
        startChunkIndex: Int = 0,
        onChunkDone: ((SherpaTranscriptionSegment?, Int, Int) -> Unit)? = null,
        onProgress: ((Double) -> Unit)? = null
    ): Pair<List<SherpaTranscriptionSegment>, Int> = withContext(Dispatchers.IO) {
        val recognizer = offlineRecognizer ?: throw IOException("ASR 引擎未就绪")
        
        onProgress?.invoke(0.02)
        Log.i(TAG, "开始读取音频文件进行 VAD 分段: $audioPath")

        val totalDurationSec = AudioDecoder.getDurationSec(audioPath)
        if (totalDurationSec <= 0) {
            throw IOException("无法获取音频时长或音频为空")
        }

        // 1. 语音区域检测 (VAD) - 采用分块解码，彻底解决大文件 Native OOM
        onProgress?.invoke(0.04)
        val regions = detectSpeechRegionsStream(context, audioPath, totalDurationSec, onProgress)
        if (regions.isEmpty()) {
            Log.w(TAG, "VAD 未检测到任何语音区间！")
            onChunkDone?.invoke(null, 0, 1)
            return@withContext Pair(emptyList(), 1)
        }

        // 2. 将区间打包为解码窗口
        val windows = TranscriptComposer.packWindows(regions)
        val totalWindows = windows.size
        Log.i(TAG, "VAD: ${regions.size} 区间 -> $totalWindows 解码窗口")
        onProgress?.invoke(0.08)

        val segments = ArrayList<SherpaTranscriptionSegment>()

        // 3. 按窗口按需解码，消除全文件浮点数组导致的 Java OOM
        for (i in startChunkIndex until totalWindows) {
            val w = windows[i]
            
            // 按需解码窗口内的数据，占用内存极小
            val chunkSamples = AudioDecoder.decodeRegion(audioPath, w.start, w.end)

            var chunkSegment: SherpaTranscriptionSegment? = null
            if (chunkSamples.isNotEmpty()) {
                val stream = recognizer.createStream()
                stream.acceptWaveform(chunkSamples, 16000)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                val text = result.text.trim()
                stream.release()

                if (text.isNotEmpty()) {
                    val seg = SherpaTranscriptionSegment(
                        text = text,
                        startTime = w.start.toFloat(),
                        duration = (w.end - w.start).toFloat(),
                        language = result.lang ?: "",
                        emotion = result.emotion ?: ""
                    )
                    segments.add(seg)
                    chunkSegment = seg
                    Log.d(TAG, "窗口 ${i + 1}/$totalWindows: [${result.lang}] $text")
                }
            }

            onChunkDone?.invoke(chunkSegment, i, totalWindows)
            onProgress?.invoke(0.1 + 0.9 * (i + 1).toDouble() / totalWindows.toDouble())
        }

        return@withContext Pair(segments, totalWindows)
    }

    /**
     * VAD 语音区间检测，按 60 秒块流式解码，大幅降低内存占用
     */
    private suspend fun detectSpeechRegionsStream(
        context: Context,
        audioPath: String,
        totalDurationSec: Double,
        onProgress: ((Double) -> Unit)?
    ): List<Pair<Double, Double>> {
        val vadModelPath = getFilePath(context, VAD_MODEL)
        if (vadModelPath == null) {
            Log.w(TAG, "VAD 模块模型缺失，采用 20 秒平均切片降级模式")
            return fallbackSegment(totalDurationSec)
        }

        try {
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(model = vadModelPath),
                sampleRate = 16000
            )
            val detector = Vad(config = vadConfig)
            val regions = ArrayList<Pair<Double, Double>>()
            
            val chunkSec = 60.0
            var curSec = 0.0
            
            while (curSec < totalDurationSec) {
                val endSec = (curSec + chunkSec).coerceAtMost(totalDurationSec)
                val samples = AudioDecoder.decodeRegion(audioPath, curSec, endSec)
                
                // 将 60s 块按 1s 小块喂给 VAD，避免 ONNX 张量溢出
                val subChunkSamples = 16000
                var offset = 0
                while (offset < samples.size) {
                    val end = (offset + subChunkSamples).coerceAtMost(samples.size)
                    val subChunk = samples.copyOfRange(offset, end)
                    detector.acceptWaveform(subChunk)
                    
                    while (!detector.empty()) {
                        val segment: SpeechSegment = detector.front()
                        detector.pop()
                        
                        val duration = segment.samples.size.toDouble() / 16000.0
                        // 加上外层大块的基础时间
                        val startTime = curSec + (segment.start.toDouble() / 16000.0)
                        regions.add(Pair(startTime, startTime + duration))
                    }
                    offset += subChunkSamples
                }
                
                curSec += chunkSec
                // 更新 VAD 进度：从 0.04 -> 0.08
                onProgress?.invoke(0.04 + 0.04 * (curSec / totalDurationSec))
            }
            
            try {
                detector.javaClass.getMethod("flush").invoke(detector)
                while (!detector.empty()) {
                    val segment: SpeechSegment = detector.front()
                    detector.pop()
                    val duration = segment.samples.size.toDouble() / 16000.0
                    val startTime = curSec + (segment.start.toDouble() / 16000.0)
                    regions.add(Pair(startTime, startTime + duration))
                }
            } catch (e: Exception) { }
            
            detector.release()
            return if (regions.isEmpty()) fallbackSegment(totalDurationSec) else regions
        } catch (e: Exception) {
            Log.e(TAG, "VAD 检测异常，降轨到 fallback 切片: ${e.message}")
            return fallbackSegment(totalDurationSec)
        }
    }

    /**
     * 强降轨机制：每隔 20 秒产生一个解码区间
     */
    private fun fallbackSegment(totalSec: Double): List<Pair<Double, Double>> {
        val regions = ArrayList<Pair<Double, Double>>()
        var cur = 0.0
        val step = 20.0
        while (cur < totalSec) {
            val end = (cur + step).coerceAtMost(totalSec)
            regions.add(Pair(cur, end))
            cur += step
        }
        return regions
    }

    @Synchronized
    fun cleanup() {
        offlineRecognizer = null
        _isModelReady.value = false
        Log.i(TAG, "SenseVoice ASR 推理资源已释放")
    }

    /**
     * 文件路径查找与 Assets 自动拷贝
     */
    private fun getFilePath(context: Context, name: String): String? {
        val destDir = File(context.filesDir, "Models")
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, name)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.absolutePath
        }

        return try {
            context.assets.open(name).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "从 Assets 部署 ASR 资源: $name")
            destFile.absolutePath
        } catch (e: IOException) {
            null
        }
    }
}
