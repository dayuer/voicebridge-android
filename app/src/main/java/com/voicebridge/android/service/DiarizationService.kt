package com.voicebridge.android.service

/**
 * 发言人聚类 + 段落拆分
 * 迁移自 iOS 侧 DiarizationService.swift
 */
object DiarizationService {

    data class SegmentRef(
        val start: Double,
        val end: Double,
        val text: String
    )

    data class TurnRef(
        val start: Double,
        val end: Double,
        val speakerIndex: Int
    ) : Comparable<TurnRef> {
        override fun compareTo(other: TurnRef): Int {
            return this.start.compareTo(other.start)
        }
    }

    data class SpeakerTurn(
        val start: Double,
        val end: Double,
        val speaker: Int
    )

    data class Slice(
        val speakerIndex: Int,
        val text: String,
        val start: Double,
        val end: Double
    )

    data class DiarizationPlan(
        val updates: List<SegmentUpdate>,
        val clusterIdToProfileName: Map<Int, String>
    )

    data class SegmentUpdate(
        val originalId: String,
        val replacements: List<Slice>
    )

    /**
     * 将一个 segment 按相邻 turn 中点拆分成 N 个不丢字的切片。
     */
    fun partition(segment: SegmentRef, turns: List<TurnRef>): List<Slice> {
        val segStart = segment.start
        val segEnd = if (segment.end > segStart) segment.end else segStart + 30.0
        val segDuration = segEnd - segStart
        if (segDuration <= 0.0 || segment.text.isEmpty()) return emptyList()

        // 过滤重叠极短的 turn (重叠 < 0.1s 过滤)
        val validTurns = turns
            .filter { Math.min(it.end, segEnd) - Math.max(it.start, segStart) > 0.1 }
            .sorted()

        if (validTurns.isEmpty()) return emptyList()
        val speakerCount = validTurns.map { it.speakerIndex }.toSet().size
        if (speakerCount <= 1) {
            // 单一发言人，直接返回整段标注
            return listOf(
                Slice(
                    speakerIndex = validTurns[0].speakerIndex,
                    text = segment.text,
                    start = segStart,
                    end = segEnd
                )
            )
        }

        // 计算相邻 turn 的中点作为切片时间边界
        val splitTimes = ArrayList<Double>()
        splitTimes.add(segStart)
        for (i in 1 until validTurns.size) {
            val prevEnd = validTurns[i - 1].end
            val curStart = validTurns[i].start
            val boundary = if (prevEnd <= curStart) {
                (prevEnd + curStart) / 2.0
            } else {
                (curStart + prevEnd) / 2.0
            }
            splitTimes.add(boundary)
        }

        val textLength = segment.text.length
        val slices = ArrayList<Slice>()
        var lastCharEnd = 0

        for (i in validTurns.indices) {
            val turn = validTurns[i]
            val isLast = (i == validTurns.size - 1)
            val sliceStart = Math.max(splitTimes[i], segStart)
            val sliceEnd = if (isLast) segEnd else Math.min(splitTimes[i + 1], segEnd)

            var charStart = (textLength * (sliceStart - segStart) / segDuration).toInt()
            val charEnd = if (isLast) {
                textLength
            } else {
                Math.max(
                    (textLength * (sliceEnd - segStart) / segDuration).toInt(),
                    charStart + 1
                )
            }

            charStart = Math.max(lastCharEnd, Math.min(charStart, Math.max(0, textLength - 1)))
            val finalEnd = if (isLast) textLength else Math.min(charEnd, textLength)
            lastCharEnd = finalEnd
            if (finalEnd <= charStart) continue

            val rawSlice = segment.text.substring(charStart, finalEnd)
            val trimmed = rawSlice.trim()
            if (trimmed.isEmpty()) continue

            slices.add(
                Slice(
                    speakerIndex = turn.speakerIndex,
                    text = trimmed,
                    start = sliceStart,
                    end = sliceEnd
                )
            )
        }

        return slices
    }

    /**
     * 对一组 voiceSample 聚类并应用到 segments 上，产生替换计划
     */
    fun plan(
        voiceSamples: List<Triple<Double, Double, FloatArray>>,
        segments: List<Quadruple<String, Double, Double, String>>,
        knownProfiles: List<Pair<String, FloatArray>> = emptyList()
    ): DiarizationPlan {
        if (voiceSamples.size < 2) return DiarizationPlan(emptyList(), emptyMap())

        val embeddings = voiceSamples.map { it.third }
        var clusterLabels = SpeakerMatcher.cluster(embeddings)

        // 聚类质量护栏 (Concentration Concentration Concentration!)
        val concentration = SpeakerMatcher.largestClusterConcentration(clusterLabels)
        if (concentration > 0.85f && voiceSamples.size > 5) {
            val stricterLabels = SpeakerMatcher.cluster(
                segmentEmbeddings = embeddings,
                distanceThreshold = 0.20f
            )
            if (SpeakerMatcher.largestClusterConcentration(stricterLabels) < concentration) {
                clusterLabels = stricterLabels
            }
        }

        return assemblePlan(voiceSamples, clusterLabels, segments, knownProfiles)
    }

    /**
     * 从 Pyannote Engine 输出来的 turns 直接生成替换计划
     */
    fun planWithTurns(
        turns: List<SpeakerTurn>,
        speakerEmbeddings: Map<Int, FloatArray>,
        segments: List<Quadruple<String, Double, Double, String>>,
        knownProfiles: List<Pair<String, FloatArray>>
    ): DiarizationPlan {
        if (turns.isEmpty()) return DiarizationPlan(emptyList(), emptyMap())

        val uniqueSpeakers = turns.map { it.speaker }.toSet().sorted()
        val speakerToIndex = HashMap<Int, Int>()
        for ((idx, sid) in uniqueSpeakers.withIndex()) {
            speakerToIndex[sid] = idx
        }

        val turnRefs = turns.map {
            TurnRef(it.start, it.end, speakerToIndex[it.speaker] ?: 0)
        }
        val merged = mergeAdjacentTurns(turnRefs)
        val updates = partitionSegments(merged, segments)

        val displayEmbeddings = HashMap<Int, FloatArray>()
        for ((sid, idx) in speakerToIndex) {
            val emb = speakerEmbeddings[sid]
            if (emb != null) displayEmbeddings[idx] = emb
        }

        val clusterIdToName = matchKnownProfiles(displayEmbeddings, knownProfiles)

        return DiarizationPlan(updates, clusterIdToName)
    }

    private fun assemblePlan(
        voiceSamples: List<Triple<Double, Double, FloatArray>>,
        clusterLabels: IntArray,
        segments: List<Quadruple<String, Double, Double, String>>,
        knownProfiles: List<Pair<String, FloatArray>>
    ): DiarizationPlan {
        val uniqueClusterIds = clusterLabels.toSet().sorted()
        val clusterIdToIndex = HashMap<Int, Int>()
        for ((idx, cid) in uniqueClusterIds.withIndex()) {
            clusterIdToIndex[cid] = idx
        }

        val turns = ArrayList<TurnRef>()
        for (i in voiceSamples.indices) {
            if (i >= clusterLabels.size) break
            turns.add(
                TurnRef(
                    start = voiceSamples[i].first,
                    end = voiceSamples[i].second,
                    speakerIndex = clusterIdToIndex[clusterLabels[i]] ?: 0
                )
            )
        }

        val merged = mergeAdjacentTurns(turns)
        val updates = partitionSegments(merged, segments)

        // 声纹匹配库
        val displayEmbeddings = HashMap<Int, FloatArray>()
        for (clusterId in uniqueClusterIds) {
            val clusterSamples = ArrayList<FloatArray>()
            for (i in voiceSamples.indices) {
                if (i < clusterLabels.size && clusterLabels[i] == clusterId) {
                    clusterSamples.add(voiceSamples[i].third)
                }
            }
            if (clusterSamples.isEmpty()) continue
            val dim = clusterSamples[0].size
            val centroid = FloatArray(dim)
            for (emb in clusterSamples) {
                for (i in 0 until dim) {
                    if (i < emb.size) centroid[i] += emb[i]
                }
            }
            val count = clusterSamples.size.toFloat()
            for (i in 0 until dim) {
                centroid[i] /= count
            }
            SpeakerMatcher.l2Normalize(centroid)
            val idx = clusterIdToIndex[clusterId]
            if (idx != null) displayEmbeddings[idx] = centroid
        }

        val clusterIdToName = matchKnownProfiles(displayEmbeddings, knownProfiles)
        return DiarizationPlan(updates, clusterIdToName)
    }

    private fun mergeAdjacentTurns(turns: List<TurnRef>): List<TurnRef> {
        val merged = ArrayList<TurnRef>()
        for (turn in turns.sorted()) {
            val last = merged.lastOrNull()
            if (last != null && last.speakerIndex == turn.speakerIndex && (turn.start - last.end) < 3.0) {
                merged[merged.size - 1] = TurnRef(
                    start = last.start,
                    end = Math.max(last.end, turn.end),
                    speakerIndex = last.speakerIndex
                )
            } else {
                merged.add(turn)
            }
        }
        return merged
    }

    private fun partitionSegments(
        merged: List<TurnRef>,
        segments: List<Quadruple<String, Double, Double, String>>
    ): List<SegmentUpdate> {
        val updates = ArrayList<SegmentUpdate>()
        for (seg in segments) {
            val segStart = seg.second
            val segEnd = if (seg.third > segStart) seg.third else segStart + 30.0
            val overlapping = merged.filter { it.end > segStart && it.start < segEnd }
            if (overlapping.isEmpty()) continue

            val slices = partition(
                segment = SegmentRef(segStart, segEnd, seg.fourth),
                by = overlapping
            )
            if (slices.isEmpty()) continue

            updates.add(SegmentUpdate(seg.first, slices))
        }
        return updates
    }

    private fun matchKnownProfiles(
        displayEmbeddings: Map<Int, FloatArray>,
        knownProfiles: List<Pair<String, FloatArray>>
    ): Map<Int, String> {
        if (knownProfiles.isEmpty()) return emptyMap()
        val result = HashMap<Int, String>()
        for ((idx, embedding) in displayEmbeddings) {
            if (embedding.isEmpty()) continue
            var bestName: String? = null
            var bestScore = 0.0f
            for (profile in knownProfiles) {
                val score = VectorSearchService.cosineSimilarity(embedding, profile.second)
                if (score > 0.78f && score > bestScore) {
                    bestScore = score
                    bestName = profile.first
                }
            }
            if (bestName != null) {
                result[idx] = bestName
            }
        }
        return result
    }

    // 辅助四元组数据类
    data class Quadruple<out A, out B, out C, out D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
