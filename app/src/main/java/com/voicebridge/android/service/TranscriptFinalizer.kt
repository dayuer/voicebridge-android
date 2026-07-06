package com.voicebridge.android.service

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.MeetingRecordEntity
import com.voicebridge.android.data.entity.SpeakerProfileEntity
import com.voicebridge.android.data.entity.TranscriptSegmentEntity
import com.voicebridge.android.data.entity.VoiceSampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.take
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 话轮化 Finalize —「说话人标注」与「声纹标注」合一的重建落库流程
 * 迁移自 iOS 侧 TranscriptFinalizer.swift
 */
object TranscriptFinalizer {
    private const val TAG = "TranscriptFinalizer"
    
    // 全局发言人硬上限
    const val MAX_SPEAKERS = 3

    @Serializable
    data class CodableRawSeg(
        val text: String,
        val start: Double,
        val end: Double
    )

    data class VP(
        val speaker: Int,
        val start: Double,
        val end: Double,
        val embedding: FloatArray
    )

    private class Computed(
        val paras: List<Para>,
        val voiceprints: List<VP>,
        val usedDiarization: Boolean
    ) {
        class Para(
            val speaker: Int, // -1 表示无分离（文章化回退）
            val text: String,
            val start: Double,
            val end: Double
        )
    }

    /**
     * 仅整理文本（Stage A ASR 结束）：智能标点分段并直接落库（无发言人标签），暂存 ASR 输入 JSON
     */
    suspend fun finalizeTextOnly(
        context: Context,
        meetingId: String,
        rawSegments: List<CodableRawSeg>,
        db: VoiceBridgeDatabase
    ) = withContext(Dispatchers.IO) {
        if (rawSegments.isEmpty()) return@withContext

        val meeting = db.meetingRecordDao().getById(meetingId) ?: return@withContext

        // 1. 组装整稿段落
        val windows = rawSegments.sortedBy { it.start }.mapIndexed { idx, s ->
            val end = if (s.end > s.start) s.end else s.start + 1.0
            val gap = if (idx + 1 < rawSegments.size) {
                Math.max(0.0, rawSegments[idx + 1].start - end)
            } else {
                0.0
            }
            TranscriptWindow(s.text, s.start, end, gap)
        }

        val punctuate: (String) -> String? = { text ->
            PunctuationService.getInstance().restore(context, text)
        }

        val composed = TranscriptComposer.compose(windows, punctuate)
        PunctuationService.getInstance().unload()

        // 2. Room 事务中清理旧数据
        db.withTransaction {
            db.transcriptSegmentDao().deleteByMeetingId(meetingId)
            // 级联删除在第一阶段Room中已通过ForeignKey声明，
            // 理论上删除旧段落即可，但我们在此处进行原子清除
            
            // 3. 落库纯文字段落 (speakerIndex = -1, speakerLabel = "")
            val fallbackEnd = rawSegments.maxOfOrNull { it.end } ?: 0.0
            val paras = toParas(composed, speaker = -1, fallbackEnd = fallbackEnd)

            val segments = paras.map { p ->
                TranscriptSegmentEntity(
                    meetingId = meetingId,
                    speakerLabel = "",
                    speakerColorIndex = 0,
                    text = p.text,
                    timestamp = p.start,
                    endTimestamp = p.end,
                    isFinal = true
                )
            }
            db.transcriptSegmentDao().insertAll(segments)
        }

        // 4. 将 ASR 窗口数据存入 JSON 缓存，供 Stage B 使用
        try {
            val jsonBytes = Json.encodeToString(rawSegments).toByteArray(Charsets.UTF_8)
            db.meetingRecordDao().update(
                meeting.copy(pendingDiarizationInputJson = jsonBytes)
            )
        } catch (e: Exception) {
            Log.e(TAG, "ASR 输入序列化失败: ${e.message}")
        }
    }

    /**
     * 后台异步分离声纹并重建话轮（Stage B）
     */
    suspend fun applyDiarization(
        context: Context,
        meetingId: String,
        audioPath: String,
        db: VoiceBridgeDatabase
    ) = withContext(Dispatchers.IO) {
        val meeting = db.meetingRecordDao().getById(meetingId) ?: return@withContext
        val data = meeting.pendingDiarizationInputJson
        if (data == null) {
            Log.w(TAG, "会议没有未决的 ASR JSON 缓存，忽略 Diarization: ${meeting.title}")
            db.meetingRecordDao().update(meeting.copy(diarizationState = 3)) // done
            return@withContext
        }

        val codableSegs = try {
            val jsonStr = String(data, Charsets.UTF_8)
            Json.decodeFromString<List<CodableRawSeg>>(jsonStr)
        } catch (e: Exception) {
            null
        }

        if (codableSegs == null || codableSegs.isEmpty()) {
            Log.w(TAG, "解析 ASR 暂存数据失败或为空")
            db.meetingRecordDao().update(meeting.copy(diarizationState = 3)) // done
            return@withContext
        }

        // 1. 获取本场预设参会人 profile 过滤
        val expectedIDs = meeting.expectedSpeakerIds
        val expectedCount = if (expectedIDs.size >= 2) expectedIDs.size else null

        // 2. 在 IO 线程执行 pyannote 分离 + 声纹 + 限制合并
        val computed = computeDiarization(
            context = context,
            audioPath = audioPath,
            rawSegments = codableSegs,
            expectedSpeakerCount = expectedCount
        )

        PunctuationService.getInstance().unload()

        // 3. 统计稳定发言人索引映射
        val uniqueSpeakers = computed.paras.map { it.speaker }.filter { it >= 0 }.toSet().sorted()
        val speakerToIndex = HashMap<Int, Int>()
        for ((idx, sid) in uniqueSpeakers.withIndex()) {
            speakerToIndex[sid] = idx
        }

        // 4. 读取已知声纹列表
        val knownProfiles = fetchKnownProfiles(db, expectedIDs)

        // 5. 开启 Room 数据库事务重建段落与声纹落库
        db.withTransaction {
            // 清理此会议下的旧段落与声纹样本
            db.transcriptSegmentDao().deleteByMeetingId(meetingId)
            // 理论上级联删除会自动处理，但在 Room 实体化层面最好保持显式操作

            // 统计声纹并计算簇质心 (Centroid)
            val samplesBySpeaker = HashMap<Int, ArrayList<VoiceSampleEntity>>()
            val sumBySpeaker = HashMap<Int, FloatArray>()
            val cntBySpeaker = HashMap<Int, Int>()

            for (vp in computed.voiceprints) {
                val vsId = UUID.randomUUID().toString()
                val vs = VoiceSampleEntity(
                    id = vsId,
                    meetingId = meetingId,
                    startTime = vp.start,
                    endTime = vp.end,
                    embedding = vp.embedding.toList()
                )
                db.voiceSampleDao().insert(vs)
                
                samplesBySpeaker.getOrPut(vp.speaker) { ArrayList() }.add(vs)
                
                val dim = vp.embedding.size
                val sumV = sumBySpeaker.getOrPut(vp.speaker) { FloatArray(dim) }
                for (i in 0 until dim) {
                    sumV[i] += vp.embedding[i]
                }
                cntBySpeaker[vp.speaker] = cntBySpeaker.getOrDefault(vp.speaker, 0) + 1
            }

            val centroidBySpeaker = HashMap<Int, FloatArray>()
            for ((sid, sumV) in sumBySpeaker) {
                val n = Math.max(1, cntBySpeaker[sid] ?: 1).toFloat()
                val c = FloatArray(sumV.size) { i -> sumV[i] / n }
                SpeakerMatcher.l2Normalize(c)
                centroidBySpeaker[sid] = c
            }

            // 跨会议已知声纹匹配
            val speakerToProfile = HashMap<Int, SpeakerProfileEntity>()
            val speakerToName = HashMap<Int, String>()
            for ((sid, centroid) in centroidBySpeaker) {
                var bestProfile: SpeakerProfileEntity? = null
                var bestScore = 0.0f
                for (profile in knownProfiles) {
                    val vp = profile.voiceprint ?: continue
                    val vpArr = FloatArray(vp.size) { i -> vp[i] }
                    val score = VectorSearchService.cosineSimilarity(centroid, vpArr)
                    if (score > 0.78f && score > bestScore) {
                        bestScore = score
                        bestProfile = profile
                    }
                }
                if (bestProfile != null) {
                    speakerToProfile[sid] = bestProfile
                    speakerToName[sid] = bestProfile.name
                }
            }

            // 绑定样本与发言人身份
            for ((sid, samples) in samplesBySpeaker) {
                val profile = speakerToProfile[sid] ?: continue
                for (vs in samples) {
                    db.voiceSampleDao().update(
                        vs.copy(speakerProfileId = profile.id)
                    )
                }
            }

            // 组装新段落插入
            val segments = computed.paras.map { p ->
                val colorIdx = if (p.speaker >= 0) (speakerToIndex[p.speaker] ?: 0) else 0
                val label = when {
                    p.speaker >= 0 && speakerToName.containsKey(p.speaker) -> speakerToName[p.speaker]!!
                    p.speaker >= 0 -> "发言人 ${colorIdx + 1}"
                    else -> ""
                }
                val profile = speakerToProfile[p.speaker]
                TranscriptSegmentEntity(
                    meetingId = meetingId,
                    speakerLabel = label,
                    speakerColorIndex = colorIdx,
                    text = p.text,
                    timestamp = p.start,
                    endTimestamp = p.end,
                    isFinal = true,
                    speakerProfileId = profile?.id
                )
            }
            db.transcriptSegmentDao().insertAll(segments)

            // 标记 DiarizationState 为 DONE (3)，清理 JSON
            val currentMeeting = db.meetingRecordDao().getById(meetingId)
            if (currentMeeting != null) {
                db.meetingRecordDao().update(
                    currentMeeting.copy(
                        diarizationState = 3,
                        pendingDiarizationInputJson = null
                    )
                )
            }
        }

        Log.i(TAG, "✅ 话轮重组及声纹绑定完毕: ${meeting.title}")
    }

    private fun computeDiarization(
        context: Context,
        audioPath: String,
        rawSegments: List<CodableRawSeg>,
        expectedSpeakerCount: Int?
    ): Computed {
        val ordered = rawSegments.sortedBy { it.start }
        val punctuate: (String) -> String? = { text ->
            PunctuationService.getInstance().restore(context, text)
        }
        val punct: (String) -> String = { punctuate(it) ?: it }

        // 1. 调用 pyannote 进行离线话轮切分
        var turns = SpeakerDiarizationEngine.getInstance().diarize(
            context = context,
            audioPath = audioPath,
            expectedSpeakerCount = expectedSpeakerCount
        )

        // 降级回退：如果 pyannote 无话轮输出，保持纯文本段落（speaker = -1）
        if (turns.isEmpty()) {
            val fallbackEnd = ordered.lastOrNull()?.end ?: 0.0
            val composed = TranscriptComposer.compose(makeWindows(ordered), punctuate)
            val paras = toParas(composed, speaker = -1, fallbackEnd = fallbackEnd)
            return Computed(paras, emptyList(), usedDiarization = false)
        }

        // 2. 提取每个话轮的声纹样本
        val voiceprints = ArrayList<VP>()
        for (t in turns) {
            if ((t.end - t.start) >= 1.0) {
                // 读取特定区间的 Float PCM 数据并提取
                val chunkSamples = try {
                    AudioDecoder.decodeRegion(audioPath, t.start, t.end)
                } catch (e: Exception) {
                    FloatArray(0)
                }
                if (chunkSamples.isNotEmpty()) {
                    val emb = SpeakerEmbeddingService.getInstance().extractEmbedding(context, chunkSamples)
                    if (emb != null) {
                        voiceprints.add(VP(t.speaker, t.start, t.end, emb))
                    }
                }
            }
        }

        // 3. 说话人上限合并 (MAX_SPEAKERS = 3)
        val merged = mergeSpeakers(turns, voiceprints, MAX_SPEAKERS)
        turns = merged.first
        val finalVoiceprints = merged.second

        // 4. 重定位 ASR 归属说话人
        fun getSpeakerFor(s: CodableRawSeg): Int {
            if (turns.isEmpty()) return -1
            var best = turns[0].speaker
            var bestOverlap = 0.0
            var nearest = turns[0].speaker
            var nearestGap = Double.MAX_VALUE
            val mid = (s.start + s.end) / 2.0
            for (t in turns) {
                val overlap = Math.min(s.end, t.end) - Math.max(s.start, t.start)
                if (overlap > bestOverlap) {
                    bestOverlap = overlap
                    best = t.speaker
                }
                val gap = when {
                    t.start > mid -> t.start - mid
                    mid > t.end -> mid - t.end
                    else -> 0.0
                }
                if (gap < nearestGap) {
                    nearestGap = gap
                    nearest = t.speaker
                }
            }
            return if (bestOverlap > 0.0) best else nearest
        }

        // 5. 按话轮合并重新分段与标点恢复
        val paras = ArrayList<Computed.Para>()
        var i = 0
        while (i < ordered.size) {
            val sid = getSpeakerFor(ordered[i])
            val group = ArrayList<CodableRawSeg>()
            while (i < ordered.size && getSpeakerFor(ordered[i]) == sid) {
                group.add(ordered[i])
                i++
            }
            val composed = TranscriptComposer.compose(makeWindows(group), punctuate)
            val groupEnd = group.lastOrNull()?.let { if (it.end > it.start) it.end else it.start + 1.0 } ?: 0.0
            paras.addAll(toParas(composed, speaker = sid, fallbackEnd = groupEnd))
        }
        paras.sortBy { it.start }

        return Computed(paras, finalVoiceprints, usedDiarization = true)
    }

    private fun mergeSpeakers(
        turns: List<DiarizationService.SpeakerTurn>,
        voiceprints: List<VP>,
        maxAllowedSpeakers: Int
    ): Pair<List<DiarizationService.SpeakerTurn>, List<VP>> {
        val mutTurns = turns.toMutableList()
        val mutVps = voiceprints.toMutableList()
        val uniqueSpeakers = mutTurns.map { it.speaker }.filter { it >= 0 }.toMutableSet()

        while (uniqueSpeakers.size > maxAllowedSpeakers) {
            // 计算质心
            val centroids = HashMap<Int, FloatArray>()
            for (s in uniqueSpeakers) {
                val embs = mutVps.filter { it.speaker == s }.map { it.embedding }
                if (embs.isNotEmpty()) {
                    val dim = embs[0].size
                    val sum = FloatArray(dim)
                    for (emb in embs) {
                        for (j in 0 until dim) {
                            sum[j] += emb[j]
                        }
                    }
                    val n = embs.size.toFloat()
                    val c = FloatArray(dim) { idx -> sum[idx] / n }
                    SpeakerMatcher.l2Normalize(c)
                    centroids[s] = c
                }
            }

            var bestPair: Pair<Int, Int>? = null
            var bestSim = -2.0f

            val sortedSpeakers = uniqueSpeakers.sorted()
            for (k in 0 until sortedSpeakers.size - 1) {
                for (j in k + 1 until sortedSpeakers.size) {
                    val s1 = sortedSpeakers[k]
                    val s2 = sortedSpeakers[j]
                    val sim = if (centroids.containsKey(s1) && centroids.containsKey(s2)) {
                        VectorSearchService.cosineSimilarity(centroids[s1]!!, centroids[s2]!!)
                    } else {
                        // 距离兜底
                        val t1 = mutTurns.filter { it.speaker == s1 }
                        val t2 = mutTurns.filter { it.speaker == s2 }
                        var minGap = Double.MAX_VALUE
                        for (turn1 in t1) {
                            for (turn2 in t2) {
                                val gap = Math.max(0.0, Math.max(turn1.start, turn2.start) - Math.min(turn1.end, turn2.end))
                                if (gap < minGap) minGap = gap
                            }
                        }
                        (1.0 / (1.0 + minGap)).toFloat() * 0.5f
                    }
                    if (sim > bestSim) {
                        bestSim = sim
                        bestPair = Pair(s1, s2)
                    }
                }
            }

            if (bestPair != null) {
                val (s1, s2) = bestPair
                Log.d(TAG, "自动合并说话人: 将 $s2 合并入 $s1")
                // 重命名
                for (idx in mutTurns.indices) {
                    if (mutTurns[idx].speaker == s2) {
                        mutTurns[idx] = mutTurns[idx].copy(speaker = s1)
                    }
                }
                for (idx in mutVps.indices) {
                    if (mutVps[idx].speaker == s2) {
                        mutVps[idx] = mutVps[idx].copy(speaker = s1)
                    }
                }
                uniqueSpeakers.remove(s2)
            } else {
                break
            }
        }
        return Pair(mutTurns, mutVps)
    }

    private fun makeWindows(segs: List<CodableRawSeg>): List<TranscriptWindow> {
        return segs.mapIndexed { idx, s ->
            val end = if (s.end > s.start) s.end else s.start + 1.0
            val gap = if (idx + 1 < segs.size) Math.max(0.0, segs[idx + 1].start - end) else 0.0
            TranscriptWindow(s.text, s.start, end, gap)
        }
    }

    private fun toParas(
        ps: List<ComposedParagraph>,
        speaker: Int,
        fallbackEnd: Double
    ): List<Computed.Para> {
        return ps.mapIndexed { idx, p ->
            val end = if (idx + 1 < ps.size) ps[idx + 1].start else Math.max(p.start + 1.0, fallbackEnd)
            Computed.Para(speaker, p.text, p.start, end)
        }
    }

    private suspend fun fetchKnownProfiles(db: VoiceBridgeDatabase, restrictTo: List<String>): List<SpeakerProfileEntity> = withContext(Dispatchers.IO) {
        val all = db.speakerProfileDao().getAllProfiles().firstOrNull() ?: emptyList()
        val filtered = all.filter { it.voiceprint != null && it.voiceprint.isNotEmpty() }
        if (restrictTo.isEmpty()) return@withContext filtered
        val idSet = restrictTo.toSet()
        return@withContext filtered.filter { idSet.contains(it.id) }
    }

    // Flow 拓展取首个值
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrNull(): T? {
        var result: T? = null
        this.take(1).collect { result = it }
        return result
    }
}
