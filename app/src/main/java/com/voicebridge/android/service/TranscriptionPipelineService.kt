package com.voicebridge.android.service

import android.content.Context
import com.voicebridge.android.data.entity.SupportedLanguage
import java.io.IOException

/**
 * 语音转译总控管线
 * 职责：
 * 1. 确保离线 ASR 模型加载
 * 2. 路由语种，支持 SenseVoice 引擎解码
 * 3. 拦截不支持的语种并给出提示
 * 4. 进度规范化回调，映射 ASR 阶段至进度 [0.05, 0.70]
 */
class TranscriptionPipelineService(
    private val sherpaASRService: SherpaASRService = SherpaASRService.getInstance()
) {

    data class ProgressUpdate(
        val progress: Double,
        val statusText: String?
    )

    data class TranscribedSegment(
        val text: String,
        val start: Double,
        val end: Double
    )

    data class TranscriptionResult(
        val segments: List<TranscribedSegment>
    )

    /**
     * 转录离线音频文件
     * @param context Android 上下文
     * @param audioPath 音频文件存储绝对路径
     * @param languageCode 指定的语言类型，若为 null 则交由 SenseVoice 进行多语言混合自动检测
     * @param onProgress 进度回调
     */
    suspend fun transcribe(
        context: Context,
        audioPath: String,
        languageCode: SupportedLanguage?,
        onProgress: (ProgressUpdate) -> Unit
    ): TranscriptionResult {

        // 1. 判断是否能够路由到 SenseVoice 引擎
        if (!usesSenseVoice(languageCode)) {
            val displayName = languageCode?.displayName ?: "未知语言"
            throw IOException("Android 目前不支持系统级 SpeechRecognizer 降级。语种 [${displayName}] 暂不可用，请选择中文、英文、日文、韩文或粤语。")
        }

        // 2. 确保模型已经加载
        if (!sherpaASRService.isModelReady.value) {
            onProgress(ProgressUpdate(0.02, "正在加载离线语音识别模型..."))
            sherpaASRService.loadModels(context)
            if (!sherpaASRService.isModelReady.value) {
                throw IOException("ASR 语音模型加载失败，无法开始转录")
            }
        }

        onProgress(ProgressUpdate(0.05, "正在识别语音内容..."))

        // 3. 执行 ASR
        val (sherpaSegments, _) = sherpaASRService.transcribeAudioFile(
            context = context,
            audioPath = audioPath,
            startChunkIndex = 0,
            onChunkDone = { _, chunkIdx, total ->
                // 将 ASR 解码窗口进度等比映射到 0.05 -> 0.70
                val p = 0.05 + 0.65 * (chunkIdx + 1).toDouble() / total.toDouble()
                onProgress(
                    ProgressUpdate(
                        progress = p.coerceAtMost(0.70),
                        statusText = "正在识别语音 (${chunkIdx + 1}/${total})..."
                    )
                )
            }
        )

        // 4. 打包输出
        val resultSegments = sherpaSegments.map {
            TranscribedSegment(
                text = it.text.trim(),
                start = it.startTime.toDouble(),
                end = (it.startTime + it.duration).toDouble()
            )
        }

        return TranscriptionResult(resultSegments)
    }

    companion object {
        /**
         * 语种是否受 SenseVoice 引擎支持
         */
        fun usesSenseVoice(language: SupportedLanguage?): Boolean {
            if (language == null) return true // null 代表混合语种自动检测，走 SenseVoice
            return language.isSenseVoiceSupported
        }
    }
}
