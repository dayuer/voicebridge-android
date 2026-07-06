package com.voicebridge.android.service

import com.voicebridge.android.data.entity.SpeakerProfileEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 针对声纹提取与匹配算法 SpeakerMatcher 的单元测试
 */
class SpeakerMatcherTest {

    @Test
    fun testVectorCosineSimilarity() {
        val a = floatArrayOf(1.0f, 0.0f, 0.0f)
        val b = floatArrayOf(1.0f, 0.0f, 0.0f)
        val c = floatArrayOf(0.0f, 1.0f, 0.0f)

        // 相同向量余弦值为 1.0
        assertEquals(1.0f, VectorSearchService.cosineSimilarity(a, b), 0.001f)
        // 正交向量余弦值为 0.0
        assertEquals(0.0f, VectorSearchService.cosineSimilarity(a, c), 0.001f)
    }

    @Test
    fun testL2Normalization() {
        val a = floatArrayOf(3.0f, 4.0f, 0.0f) // 模长 = 5.0
        SpeakerMatcher.l2Normalize(a)
        
        // 归一化后模长为 1.0
        val norm = Math.sqrt((a[0] * a[0] + a[1] * a[1]).toDouble()).toFloat()
        assertEquals(1.0f, norm, 0.001f)
        assertEquals(0.6f, a[0], 0.001f)
        assertEquals(0.8f, a[1], 0.001f)
    }

    @Test
    fun testAdaptiveThreshold() {
        val profile = SpeakerProfileEntity(
            id = "spk_1",
            name = "张三",
            sampleCount = 6,          // bonus = 6 * 0.005 = 0.03
            embeddingVariance = 0.02f // penalty = 0.02 * 0.5 = 0.01
        )
        // threshold = 0.78 - 0.03 + 0.01 = 0.76
        val threshold = SpeakerMatcher.adaptiveThreshold(profile)
        assertEquals(0.76f, threshold, 0.001f)
    }

    @Test
    fun testEnrollmentQuality() {
        // 两组高一致性向量（完全一致）
        val sample1 = floatArrayOf(1.0f, 0.0f)
        val sample2 = floatArrayOf(1.0f, 0.0f)
        
        val quality = SpeakerMatcher.validateEnrollmentQuality(listOf(sample1, sample2))
        assertEquals(0.0f, quality.first, 0.001f)
        assertEquals(true, quality.second)
    }

    @Test
    fun testAHCClustering() {
        // 创建 4 个特征向量。
        // v0, v1 极度接近 (余弦相似度 ~0.99)
        // v2, v3 极度接近 (余弦相似度 ~0.99)
        // 两组之间余弦相似度极低 (~0)
        val v0 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v1 = floatArrayOf(0.99f, 0.1f, 0.0f).apply { SpeakerMatcher.l2Normalize(this) }
        
        val v2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        val v3 = floatArrayOf(0.0f, 0.99f, 0.1f).apply { SpeakerMatcher.l2Normalize(this) }

        val embeddings = listOf(v0, v1, v2, v3)

        // 聚类阈值设为 0.3 (余弦距离 >0.3 不合并)
        val labels = SpeakerMatcher.cluster(embeddings, distanceThreshold = 0.3f, maxSpeakers = 8)

        // 预期得到两簇：v0 和 v1 属于一簇；v2 和 v3 属于另一簇
        assertEquals(4, labels.size)
        assertEquals(labels[0], labels[1])
        assertEquals(labels[2], labels[3])
        
        // 且两个簇的 ID 应该是不同的
        assert(labels[0] != labels[2])
    }

    @Test
    fun testLargestClusterConcentration() {
        val labels = intArrayOf(0, 0, 0, 1, 2)
        val conc = SpeakerMatcher.largestClusterConcentration(labels)
        // 最大簇 (0) 有 3 个元素，总共 5 个，比例 = 3/5 = 0.6
        assertEquals(0.6f, conc, 0.001f)
    }

    @Test
    fun testProfileMatching() {
        val database = listOf(
            SpeakerProfileEntity(
                id = "spk_1",
                name = "张三",
                voiceprint = listOf(1.0f, 0.0f, 0.0f),
                sampleCount = 4
            ),
            SpeakerProfileEntity(
                id = "spk_2",
                name = "李四",
                voiceprint = listOf(0.0f, 1.0f, 0.0f),
                sampleCount = 4
            )
        )

        val input = floatArrayOf(0.99f, 0.02f, 0.0f)
        
        // 应该匹配到张三
        val match = SpeakerMatcher.match(input, database)
        assertNotNull(match)
        assertEquals("张三", match?.name)

        // 测试不满足阈值匹配失败的场景
        val orthogonalInput = floatArrayOf(0.0f, 0.0f, 1.0f)
        val matchFail = SpeakerMatcher.match(orthogonalInput, database)
        assertNull(matchFail)
    }
}
