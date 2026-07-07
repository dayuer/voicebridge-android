package com.voicebridge.android.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android 原生音频解码与重采样模块
 * 职责：
 * 1. 使用 MediaExtractor & MediaCodec 解码常见音频格式（m4a, mp3, wav等）为 raw PCM 16-bit
 * 2. 混音多声道为单声道 (Mono)
 * 3. 线性插值重采样至 16000Hz
 * 4. 归一化 Short [-32768, 32767] 至 Float [-1.0, 1.0]
 * 5. 支持精确区间 [startSec, endSec) 解码，避免处理大型音频时的内存溢出
 */
object AudioDecoder {
    private const val TAG = "AudioDecoder"
    private const val TARGET_SAMPLE_RATE = 16000
    private const val TIMEOUT_US = 5000L

    /**
     * 解码音频文件的特定区间，并重采样为 16kHz 单声道 Float32 数组。
     * @param audioPath 音频文件绝对路径
     * @param startSec 起始时间（秒）
     * @param endSec 结束时间（秒），若为负数或0则解码至文件末尾
     */
    @Throws(IOException::class)
    fun decodeRegion(
        audioPath: String,
        startSec: Double = 0.0,
        endSec: Double = 0.0
    ): FloatArray {
        val file = File(audioPath)
        if (!file.exists()) {
            throw IOException("音频文件不存在: $audioPath")
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) {
                throw IOException("音频中未找到有效的音频轨道")
            }
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("未知的音频格式")
            val inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Seek 到指定起始时间前方的最近关键帧
            val startUs = (startSec * 1_000_000).toLong()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // 临时容器用于累积解码的 Short 数组块，避免 ArrayList<Short> 的装箱开销导致的 OOM
            val decodedChunks = ArrayList<ShortArray>()
            var totalShorts = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            // 计算我们需要保留的时间边界
            val limitEndUs = if (endSec > 0.0) (endSec * 1_000_000).toLong() else Long.MAX_VALUE

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val presentationTimeUs = extractor.sampleTime

                            if (sampleSize < 0 || presentationTimeUs > limitEndUs) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputEOS = true
                            } else {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    val presentationTimeUs = bufferInfo.presentationTimeUs

                    // 只收集大于 startUs 且小于 limitEndUs 区间内的数据
                    if (outputBuffer != null && presentationTimeUs >= startUs && presentationTimeUs <= limitEndUs) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        
                        // 以小端字节序读取 16-bit PCM Short
                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val temp = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(temp)
                        decodedChunks.add(temp)
                        totalShorts += temp.size
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 格式发生变更
                }
            }

            val rawSamples = ShortArray(totalShorts)
            var offset = 0
            for (chunk in decodedChunks) {
                System.arraycopy(chunk, 0, rawSamples, offset, chunk.size)
                offset += chunk.size
            }
            // 立即释放 chunks 内存以防止 OOM
            decodedChunks.clear()
            
            // 将降轨 (Mono)、归一化 (Float) 和重采样 (16kHz) 合并为一次遍历，消除三个中间数组的巨大内存开销
            val totalFrames = totalShorts / channelCount
            val ratio = inputSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            val destLength = (totalFrames / ratio).toInt()
            
            val finalSamples = FloatArray(destLength)
            for (i in 0 until destLength) {
                val srcFrameIdx = (i * ratio).toInt().coerceAtMost(totalFrames - 1)
                
                var sum = 0
                val frameOffset = srcFrameIdx * channelCount
                // 取出该帧所有声道的样本并求和，合轨为单声道
                for (c in 0 until channelCount) {
                    sum += rawSamples[frameOffset + c].toInt()
                }
                
                val monoShort = sum / channelCount
                finalSamples[i] = monoShort.toFloat() / 32768.0f
            }
            
            return finalSamples

        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                Log.e(TAG, "释放解码器异常: ${e.message}")
            }
            extractor.release()
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }

    /**
     * 将多声道音频合轨为单声道 (Mono)
     */
    private fun toMono(input: ShortArray, channelCount: Int): ShortArray {
        if (channelCount <= 1) return input
        val outputSize = input.size / channelCount
        val output = ShortArray(outputSize)
        for (i in 0 until outputSize) {
            var sum = 0
            for (c in 0 until channelCount) {
                sum += input[i * channelCount + c]
            }
            output[i] = (sum / channelCount).toShort()
        }
        return output
    }

    /**
     * 线性插值重采样
     */
    private fun resample(input: FloatArray, srcRate: Int, destRate: Int): FloatArray {
        if (srcRate == destRate) return input
        val ratio = srcRate.toDouble() / destRate.toDouble()
        val destLength = (input.size / ratio).toInt()
        val output = FloatArray(destLength)
        for (i in 0 until destLength) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val t = (srcPos - srcIdx).toFloat()
            if (srcIdx >= input.size - 1) {
                output[i] = input[input.size - 1]
            } else {
                output[i] = (1.0f - t) * input[srcIdx] + t * input[srcIdx + 1]
            }
        }
        return output
    }
}
