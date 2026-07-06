package com.voicebridge.android.service

import com.voicebridge.android.data.entity.SpeakerProfileEntity
import kotlin.math.sqrt

/**
 * 离线声纹聚类与跨会议匹配算法
 * 迁移自 iOS 侧 SpeakerMatcher.swift
 */
object SpeakerMatcher {

    /**
     * AHC 聚类所用的内部类
     */
    private class SpeakerCluster(
        val id: Int,
        val segmentIndices: MutableSet<Int> = mutableSetOf(),
        val voiceprints: MutableList<FloatArray> = mutableListOf()
    ) {
        /**
         * 计算 L2 归一化平均声纹中心向量
         */
        fun getCentroid(): FloatArray {
            if (voiceprints.isEmpty()) return FloatArray(0)
            val dim = voiceprints[0].size
            val sum = FloatArray(dim)
            for (vp in voiceprints) {
                for (i in 0 until dim) {
                    sum[i] += vp[i]
                }
            }
            val count = voiceprints.size.toFloat()
            val avg = FloatArray(dim) { i -> sum[i] / count }
            l2Normalize(avg)
            return avg
        }
    }

    /**
     * 原地 L2 归一化 FloatArray，模长归一为 1.0
     */
    fun l2Normalize(vector: FloatArray) {
        var sumSquared = 0.0f
        for (v in vector) {
            sumSquared += v * v
        }
        val norm = sqrt(sumSquared)
        if (norm < 1e-8f) return
        for (i in vector.indices) {
            vector[i] /= norm
        }
    }

    /**
     * 返回 L2 归一化副本
     */
    fun normalized(vector: FloatArray): FloatArray {
        val copy = vector.clone()
        l2Normalize(copy)
        return copy
    }

    fun normalized(vector: List<Float>): List<Float> {
        val arr = FloatArray(vector.size) { i -> vector[i] }
        l2Normalize(arr)
        return arr.toList()
    }

    /**
     * 基于声纹注册质量的自适应余弦度量匹配阈值
     */
    fun adaptiveThreshold(profile: SpeakerProfileEntity): Float {
        val baseThreshold = 0.78f
        val sampleBonus = (profile.sampleCount * 0.005f).coerceAtMost(0.04f)
        val variancePenalty = profile.embeddingVariance * 0.5f
        return (baseThreshold - sampleBonus + variancePenalty).coerceIn(0.70f, 0.90f)
    }

    /**
     * 检验注册声纹样本的一致性
     */
    fun validateEnrollmentQuality(
        embeddings: List<FloatArray>,
        maxVariance: Float = 0.15f
    ): Pair<Float, Boolean> {
        if (embeddings.size < 2) return Pair(0.0f, true)

        val distances = ArrayList<Float>()
        for (i in 0 until embeddings.size - 1) {
            for (j in i + 1 until embeddings.size) {
                val similarity = VectorSearchService.cosineSimilarity(embeddings[i], embeddings[j])
                distances.add(1.0f - similarity)
            }
        }

        if (distances.isEmpty()) return Pair(0.0f, true)

        val mean = distances.sum() / distances.size
        var sumSquaredDiff = 0.0f
        for (d in distances) {
            sumSquaredDiff += (d - mean) * (d - mean)
        }
        val variance = sumSquaredDiff / distances.size
        return Pair(variance, variance <= maxVariance)
    }

    /**
     * 跨会议已知声纹库比对
     */
    fun match(
        activeEmbedding: FloatArray,
        databaseProfiles: List<SpeakerProfileEntity>,
        threshold: Float = 0.82f,
        useAdaptiveThreshold: Boolean = true
    ): SpeakerProfileEntity? {
        if (activeEmbedding.isEmpty()) return null
        val normalizedInput = normalized(activeEmbedding)

        var bestMatch: SpeakerProfileEntity? = null
        var highestScore = 0.0f

        for (profile in databaseProfiles) {
            val registered = profile.voiceprint ?: continue
            val registeredArr = FloatArray(registered.size) { i -> registered[i] }
            val score = VectorSearchService.cosineSimilarity(normalizedInput, registeredArr)

            val effectiveThreshold = if (useAdaptiveThreshold) {
                adaptiveThreshold(profile)
            } else {
                threshold
            }

            if (score > effectiveThreshold && score > highestScore) {
                highestScore = score
                bestMatch = profile
            }
        }

        return bestMatch
    }

    /**
     * AHC 凝聚层次聚类（纯 CPU 实现）
     */
    fun cluster(
        segmentEmbeddings: List<FloatArray>,
        distanceThreshold: Float = 0.32f,
        maxSpeakers: Int = 8
    ): IntArray {
        val n = segmentEmbeddings.size
        if (n == 0) return IntArray(0)

        // 1. 初始化簇
        val clusters = ArrayList<SpeakerCluster>()
        for (i in 0 until n) {
            clusters.add(SpeakerCluster(id = i).apply {
                segmentIndices.add(i)
                voiceprints.add(segmentEmbeddings[i])
            })
        }

        // 2. 层次迭代合并
        while (clusters.size > 1) {
            var minDistance = Float.MAX_VALUE
            var mergeI = -1
            var mergeJ = -1

            // 寻找最小距离对
            for (i in 0 until clusters.size - 1) {
                for (j in i + 1 until clusters.size) {
                    val dist = clusterDistance(clusters[i], clusters[j])
                    if (dist < minDistance) {
                        minDistance = dist
                        mergeI = i
                        mergeJ = j
                    }
                }
            }

            // 距离超出阈值时停止合并
            if (minDistance > distanceThreshold) {
                break
            }

            if (clusters.size <= 1) break
            if (mergeI == -1 || mergeJ == -1) break

            // 将 J 合并到 I 中
            val clusterJ = clusters.removeAt(mergeJ)
            val clusterI = clusters[mergeI]
            clusterI.segmentIndices.addAll(clusterJ.segmentIndices)
            clusterI.voiceprints.addAll(clusterJ.voiceprints)
        }

        // 3. 重排标签映射
        val labels = IntArray(n)
        for (newId in clusters.indices) {
            val cluster = clusters[newId]
            for (idx in cluster.segmentIndices) {
                labels[idx] = newId
            }
        }

        return labels
    }

    /**
     * 计算最大簇的集中比率以检测聚类是否失败
     */
    fun largestClusterConcentration(labels: IntArray): Float {
        if (labels.isEmpty()) return 0f
        val counts = HashMap<Int, Int>()
        for (l in labels) {
            counts[l] = counts.getOrDefault(l, 0) + 1
        }
        val maxVal = counts.values.maxOrNull() ?: 0
        return maxVal.toFloat() / labels.size.toFloat()
    }

    private fun clusterDistance(c1: SpeakerCluster, c2: SpeakerCluster): Float {
        var sumDistance = 0.0f
        var pairCount = 0.0f
        for (v1 in c1.voiceprints) {
            for (v2 in c2.voiceprints) {
                val similarity = VectorSearchService.cosineSimilarity(v1, v2)
                sumDistance += (1.0f - similarity)
                pairCount += 1.0f
            }
        }
        return if (pairCount > 0f) sumDistance / pairCount else 1.0f
    }
}
