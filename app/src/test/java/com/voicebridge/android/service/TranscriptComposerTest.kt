package com.voicebridge.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 针对 TranscriptComposer 的算法单元测试
 */
class TranscriptComposerTest {

    @Test
    fun testPackWindows() {
        val regions = listOf(
            Pair(0.0, 5.0),
            Pair(5.1, 10.0),   // gap = 0.1
            Pair(12.5, 18.0),  // gap = 2.5 -> strong gap
            Pair(18.5, 23.0)   // gap = 0.5
        )

        val windows = TranscriptComposer.packWindows(regions, maxSpan = 28.0, strongGap = 2.0)
        
        // 应该在第三个区间前切开，因为 gap = 2.5 >= strongGap
        assertEquals(2, windows.size)
        
        assertEquals(0.0, windows[0].start, 0.001)
        assertEquals(10.0, windows[0].end, 0.001)
        assertEquals(2.5, windows[0].gapAfter, 0.001)

        assertEquals(12.5, windows[1].start, 0.001)
        assertEquals(23.0, windows[1].end, 0.001)
        assertEquals(0.0, windows[1].gapAfter, 0.001)
    }

    @Test
    fun testStripPunctuation() {
        val input = "Hello, world! 这是一个，测试。123"
        val output = TranscriptComposer.stripPunctuation(input)
        // 标点应该被剥除，且保留字母、数字、中文
        assertEquals("Hello world 这是一个 测试 123", output)
    }

    @Test
    fun testSplitSentences() {
        val input = "今天天气真好！你想去公园吗？好的，出发。"
        val sentences = TranscriptComposer.splitSentences(input)
        assertEquals(3, sentences.size)
        assertEquals("今天天气真好！", sentences[0])
        assertEquals("你想去公园吗？", sentences[1])
        assertEquals("好的，出发。", sentences[2])
    }

    @Test
    fun testSplitBack() {
        val rawPieces = listOf("hello", "world")
        // 模拟恢复完标点后的长串
        val punctuated = "Hello, World!"
        
        val result = TranscriptComposer.splitBack(punctuated, rawPieces)
        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("Hello,", result[0])
        assertEquals("World!", result[1])
    }

    @Test
    fun testSplitBackAlignmentFailed() {
        val rawPieces = listOf("hello", "world")
        // 出现了原文没有的字符 "extra"，对齐应该失败返回 null
        val punctuated = "Hello, extra World!"
        val result = TranscriptComposer.splitBack(punctuated, rawPieces)
        assertNull(result)
    }

    @Test
    fun testFallbackPunctuate() {
        // 中文 fallback
        val outputZh = TranscriptComposer.fallbackPunctuate("今天天气好,,,", isStrongBoundary = true)
        assertEquals("今天天气好。", outputZh)

        // 英文 fallback
        val outputEn = TranscriptComposer.fallbackPunctuate("hello... world", isStrongBoundary = true)
        assertEquals("hello. world.", outputEn)
    }

    @Test
    fun testCompose() {
        val windows = listOf(
            TranscriptWindow("今天我们讨论下", 0.0, 5.0, 0.1),
            TranscriptWindow("Android平台迁移", 5.1, 10.0, 2.5), // gap = 2.5 -> 触发分段
            TranscriptWindow("好的，开始吧", 12.5, 15.0, 0.0)
        )

        // 模拟不需要标点恢复服务的 fallback 模式
        val paragraphs = TranscriptComposer.compose(windows, punctuate = null, paragraphGap = 2.0)
        
        assertEquals(2, paragraphs.size)
        assertEquals("今天我们讨论下Android平台迁移。", paragraphs[0].text)
        assertEquals(0.0, paragraphs[0].start, 0.001)

        assertEquals("好的，开始吧。", paragraphs[1].text)
        assertEquals(12.5, paragraphs[1].start, 0.001)
    }
}
