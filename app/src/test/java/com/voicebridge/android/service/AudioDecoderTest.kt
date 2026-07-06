package com.voicebridge.android.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 针对 AudioDecoder 音频重构与重采样底座的单元测试
 */
class AudioDecoderTest {

    @Test
    fun testToMono() {
        // 利用反射测试 AudioDecoder.toMono 私有方法，验证立体声平均合轨 Mono 逻辑
        val decoderClass = AudioDecoder::class.java
        val toMonoMethod = decoderClass.getDeclaredMethod(
            "toMono",
            ShortArray::class.java,
            Int::class.java
        )
        toMonoMethod.isAccessible = true

        val stereoInput = shortArrayOf(10, 20, -5, 15, 100, -50) // 3组双声道采样
        val expectedMono = shortArrayOf(15, 5, 25)

        val monoOutput = toMonoMethod.invoke(AudioDecoder, stereoInput, 2) as ShortArray
        assertArrayEquals(expectedMono, monoOutput)
    }

    @Test
    fun testResample() {
        // 利用反射测试 AudioDecoder.resample 线性插值算法
        val decoderClass = AudioDecoder::class.java
        val resampleMethod = decoderClass.getDeclaredMethod(
            "resample",
            FloatArray::class.java,
            Int::class.java,
            Int::class.java
        )
        resampleMethod.isAccessible = true

        val input = floatArrayOf(0.5f, 1.0f, -0.5f)
        
        // 1. 采样率相同：直接原样返回
        val sameOutput = resampleMethod.invoke(AudioDecoder, input, 16000, 16000) as FloatArray
        assertArrayEquals(input, sameOutput, 0.001f)

        // 2. 采样率降低：从 32000Hz 降到 16000Hz (2:1 压缩)
        val downsampled = resampleMethod.invoke(AudioDecoder, input, 32000, 16000) as FloatArray
        // ratio = 2.0, destLength = 3 / 2 = 1
        assertEquals(1, downsampled.size)
        assertEquals(0.5f, downsampled[0], 0.001f)
    }
}
