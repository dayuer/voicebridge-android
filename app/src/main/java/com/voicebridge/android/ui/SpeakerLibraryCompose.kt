package com.voicebridge.android.ui

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.SpeakerProfileEntity
import com.voicebridge.android.data.entity.VoiceSampleEntity
import com.voicebridge.android.service.SpeakerMatcher
import com.voicebridge.android.ui.theme.VoiceBridgeTheme
import com.voicebridge.android.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * 声纹指纹库管理页 — Faces 全局聚类范式，对齐 iOS SpeakerLibraryView + SpeakerLibraryViewModel
 *
 * Section 1：已登记的发言人（重命名 / 删除并重置关联段落）
 * Section 2：待标定的未知声音簇（游离 VoiceSample 全局 AHC 聚类，试听 / 标定 / 拆分 / 忽略）
 */

/** 一个未知声音候选簇（全局聚类结果） */
data class SpeakerClusterGroup(
    val samples: List<VoiceSampleEntity>,
    val meetingCount: Int,
    val representativeText: String,
    /** 代表样本所属会议音频的绝对路径（用于试听） */
    val representativeAudioPath: String?
) {
    val representativeSample: VoiceSampleEntity? get() = samples.firstOrNull()

    val totalDuration: Double get() = samples.sumOf { it.endTime - it.startTime }

    /** 簇中心向量（均值 + L2 归一化） */
    val centroid: List<Float>?
        get() {
            val valid = samples.map { it.embedding }.filter { it.isNotEmpty() }
            if (valid.isEmpty()) return null
            val dim = valid[0].size
            val sum = FloatArray(dim)
            for (emb in valid) {
                if (emb.size != dim) continue
                for (i in 0 until dim) sum[i] += emb[i]
            }
            val avg = FloatArray(dim) { sum[it] / valid.size }
            SpeakerMatcher.l2Normalize(avg)
            return avg.toList()
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerLibraryCompose(
    db: VoiceBridgeDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profiles by remember { mutableStateOf<List<SpeakerProfileEntity>>(emptyList()) }
    var clusters by remember { mutableStateOf<List<SpeakerClusterGroup>>(emptyList()) }
    var isClustering by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 试听播放
    var playingSampleId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackMonitor by remember { mutableStateOf<Job?>(null) }

    fun stopPlayback() {
        playbackMonitor?.cancel()
        playbackMonitor = null
        mediaPlayer?.release()
        mediaPlayer = null
        playingSampleId = null
    }

    DisposableEffect(Unit) {
        onDispose {
            playbackMonitor?.cancel()
            mediaPlayer?.release()
        }
    }

    // 加载并全局聚类
    LaunchedEffect(refreshKey) {
        isClustering = true
        val result = withContext(Dispatchers.IO) {
            val allProfiles = db.speakerProfileDao().getAllProfiles().first()

            // 游离样本：会议已完成且音频文件仍在
            val unassigned = db.voiceSampleDao().getUnassignedSamplesOnce()
            val meetingCache = mutableMapOf<String, Pair<Boolean, String?>>() // meetingId -> (isCompleted, absPath)
            val valid = unassigned.filter { sample ->
                val meetingId = sample.meetingId ?: return@filter false
                if (sample.embedding.isEmpty()) return@filter false
                val (completed, absPath) = meetingCache.getOrPut(meetingId) {
                    val meeting = db.meetingRecordDao().getById(meetingId)
                    val path = meeting?.audioFilePath?.let { File(context.filesDir, it).absolutePath }
                    Pair(meeting?.isCompleted == true, path)
                }
                completed && absPath != null && File(absPath).exists()
            }

            if (valid.isEmpty()) return@withContext Pair(allProfiles.toList(), emptyList<SpeakerClusterGroup>())

            // AHC 全局聚类（与 iOS 一致：阈值 0.18，上限 50 簇）
            val embeddings = valid.map { s -> FloatArray(s.embedding.size) { s.embedding[it] } }
            val labels = SpeakerMatcher.cluster(embeddings, distanceThreshold = 0.18f, maxSpeakers = 50)

            val grouped = mutableMapOf<Int, MutableList<VoiceSampleEntity>>()
            valid.forEachIndexed { i, sample ->
                if (i < labels.size) grouped.getOrPut(labels[i]) { mutableListOf() }.add(sample)
            }

            val clusterGroups = grouped.values
                .sortedByDescending { it.size }
                .take(15)
                .map { samples -> buildClusterGroup(db, context.filesDir, samples, meetingCache) }

            Pair(allProfiles.toList(), clusterGroups)
        }
        profiles = result.first
        clusters = result.second
        isClustering = false
    }

    fun playCluster(cluster: SpeakerClusterGroup) {
        val sample = cluster.representativeSample ?: return
        if (playingSampleId == sample.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        val path = cluster.representativeAudioPath ?: return
        try {
            val player = MediaPlayer()
            player.setDataSource(path)
            player.prepare()
            player.seekTo((sample.startTime * 1000).toInt())
            player.start()
            mediaPlayer = player
            playingSampleId = sample.id
            playbackMonitor = scope.launch {
                while (isActive) {
                    delay(100)
                    val p = mediaPlayer ?: break
                    if (!p.isPlaying || p.currentPosition >= (sample.endTime * 1000).toInt()) {
                        stopPlayback()
                        break
                    }
                }
            }
            AppLog.voiceprint(
                "播放声纹切片 [%.1fs ~ %.1fs]".format(Locale.US, sample.startTime, sample.endTime)
            )
        } catch (e: Exception) {
            AppLog.error("播放局部音频失败: ${e.message}")
            stopPlayback()
        }
    }

    // 标定弹窗状态
    var assigningCluster by remember { mutableStateOf<SpeakerClusterGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("声纹指纹库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { stopPlayback(); refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新聚类")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "⚙️ 声纹特征是本地 Diarization 时提取的 512 维向量。下方未知声音由全库游离样本自动聚类，试听后可标定归属。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Section 1: 已登记的发言人
            item {
                Text(
                    text = "已登记的发言人",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (profiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "暂无声纹登记，请在下方试听未知声音并标定身份",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(profiles.size) { idx ->
                    val profile = profiles[idx]
                    RegisteredSpeakerCard(
                        profile = profile,
                        onRename = { newName ->
                            scope.launch(Dispatchers.IO) {
                                db.speakerProfileDao().update(profile.copy(name = newName))
                                // 同步刷新关联段落的显示标签
                                db.transcriptSegmentDao().getSegmentsByProfileIdOnce(profile.id).forEach { seg ->
                                    db.transcriptSegmentDao().update(seg.copy(speakerLabel = newName))
                                }
                                AppLog.voiceprint("发言人重命名: ${profile.name} → $newName")
                                refreshKey++
                            }
                        },
                        onDelete = {
                            scope.launch(Dispatchers.IO) {
                                // 关联段落重置为未知发言人（样本 FK 为 SET_NULL 自动游离化）
                                db.transcriptSegmentDao().getSegmentsByProfileIdOnce(profile.id).forEach { seg ->
                                    db.transcriptSegmentDao().update(
                                        seg.copy(speakerProfileId = null, speakerLabel = "未知发言人", speakerColorIndex = 0)
                                    )
                                }
                                db.speakerProfileDao().delete(profile)
                                AppLog.voiceprint("已删除发言人: ${profile.name}，其声纹样本已重置为游离状态")
                                refreshKey++
                            }
                        }
                    )
                }
            }

            // Section 2: 待标定的未知声音
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "待标定的未知声音",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isClustering) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("正在进行全局声纹聚类分析...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (clusters.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VoiceBridgeTheme.success)
                            Text(
                                "全库声音已标定完毕，没有新的未知声音",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(clusters.size) { idx ->
                    val cluster = clusters[idx]
                    CandidateClusterCard(
                        cluster = cluster,
                        index = idx,
                        isPlaying = playingSampleId != null && playingSampleId == cluster.representativeSample?.id,
                        onTogglePlay = { playCluster(cluster) },
                        onAssign = { stopPlayback(); assigningCluster = cluster },
                        onSplit = {
                            stopPlayback()
                            val position = clusters.indexOf(cluster)
                            if (position >= 0 && cluster.samples.size > 1) {
                                val singles = cluster.samples.map { s ->
                                    cluster.copy(samples = listOf(s), meetingCount = 1)
                                }
                                clusters = clusters.toMutableList().apply {
                                    removeAt(position)
                                    addAll(position, singles)
                                }
                                AppLog.voiceprint("已将一个包含 ${cluster.samples.size} 个样本的簇拆分为单个声纹候选")
                            }
                        },
                        onIgnore = {
                            stopPlayback()
                            scope.launch(Dispatchers.IO) {
                                cluster.samples.forEach { db.voiceSampleDao().delete(it) }
                                AppLog.voiceprint("已忽略并删除 ${cluster.samples.size} 个游离声纹样本")
                                refreshKey++
                            }
                        }
                    )
                }
            }
        }
    }

    // 标定身份弹窗
    assigningCluster?.let { cluster ->
        AssignIdentitySheet(
            cluster = cluster,
            profiles = profiles,
            onDismiss = { assigningCluster = null },
            onAssignToExisting = { profile ->
                assigningCluster = null
                scope.launch(Dispatchers.IO) {
                    assignClusterToProfile(db, cluster, profile)
                    refreshKey++
                }
            },
            onCreateNew = { name ->
                assigningCluster = null
                scope.launch(Dispatchers.IO) {
                    createProfileFromCluster(db, cluster, name)
                    refreshKey++
                }
            }
        )
    }
}

// MARK: - 数据操作

/** 构建候选簇的展示信息：代表文本（时间重叠最大的转录段）、会议数、试听音频路径 */
private suspend fun buildClusterGroup(
    db: VoiceBridgeDatabase,
    filesDir: File,
    samples: List<VoiceSampleEntity>,
    meetingCache: Map<String, Pair<Boolean, String?>>
): SpeakerClusterGroup {
    val meetingCount = samples.mapNotNull { it.meetingId }.toSet().size
    val rep = samples.firstOrNull()
    var repText = "语音片段"
    var repAudio: String? = null
    if (rep?.meetingId != null) {
        repAudio = meetingCache[rep.meetingId]?.second
        val segments = db.transcriptSegmentDao().getSegmentsByMeetingIdOnce(rep.meetingId)
        var bestOverlap = 0.0
        for (seg in segments) {
            val segEnd = if (seg.endTimestamp > 0) seg.endTimestamp else seg.timestamp + 30.0
            val overlap = minOf(segEnd, rep.endTime) - maxOf(seg.timestamp, rep.startTime)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                repText = seg.text
            }
        }
    }
    return SpeakerClusterGroup(
        samples = samples,
        meetingCount = meetingCount,
        representativeText = repText,
        representativeAudioPath = repAudio
    )
}

/** 将整簇样本批量绑定至已有发言人，并做特征均值加权融合（对齐 iOS assignSpeaker） */
private suspend fun assignClusterToProfile(
    db: VoiceBridgeDatabase,
    cluster: SpeakerClusterGroup,
    profile: SpeakerProfileEntity
) {
    cluster.samples.forEach { sample ->
        db.voiceSampleDao().update(sample.copy(speakerProfileId = profile.id))
    }

    // 增量融合特征向量
    val clusterCentroid = cluster.centroid
    val existingCount = profile.sampleCount.toFloat()
    val clusterCount = cluster.samples.size.toFloat()
    var newVoiceprint = profile.voiceprint
    if (clusterCentroid != null) {
        val current = profile.voiceprint
        newVoiceprint = if (!current.isNullOrEmpty()) {
            val total = maxOf(existingCount + clusterCount, 1f)
            val merged = FloatArray(current.size) { i ->
                val c = if (i < clusterCentroid.size) clusterCentroid[i] else 0f
                (current[i] * existingCount + c * clusterCount) / total
            }
            SpeakerMatcher.l2Normalize(merged)
            merged.toList()
        } else {
            clusterCentroid
        }
    }

    // 重新计算注册质量方差
    val allSamples = db.voiceSampleDao().getSamplesByProfileIdOnce(profile.id)
    val allEmbeddings = allSamples.map { it.embedding }.filter { it.isNotEmpty() }
        .map { list -> FloatArray(list.size) { list[it] } }
    val (variance, isValid) = SpeakerMatcher.validateEnrollmentQuality(allEmbeddings)

    db.speakerProfileDao().update(
        profile.copy(
            voiceprint = newVoiceprint,
            sampleCount = profile.sampleCount + cluster.samples.size,
            totalDuration = profile.totalDuration + cluster.totalDuration,
            embeddingVariance = variance
        )
    )

    if (!isValid) {
        AppLog.voiceprint("${profile.name} 的声纹样本方差过大 (%.4f > 0.15)，可能混入了他人的声纹".format(Locale.US, variance))
    }
    AppLog.voiceprint("已将未知声音簇 (${cluster.samples.size} 个样本) 批量绑定至: ${profile.name}")
}

/** 创建全新发言人并录入整簇特征（对齐 iOS createAndAssignSpeaker） */
private suspend fun createProfileFromCluster(
    db: VoiceBridgeDatabase,
    cluster: SpeakerClusterGroup,
    name: String
) {
    val embeddings = cluster.samples.map { it.embedding }.filter { it.isNotEmpty() }
        .map { list -> FloatArray(list.size) { list[it] } }
    val (variance, _) = SpeakerMatcher.validateEnrollmentQuality(embeddings)

    val newProfile = SpeakerProfileEntity(
        name = name,
        voiceprint = cluster.centroid,
        sampleCount = cluster.samples.size,
        totalDuration = cluster.totalDuration,
        embeddingVariance = variance
    )
    db.speakerProfileDao().insert(newProfile)
    cluster.samples.forEach { sample ->
        db.voiceSampleDao().update(sample.copy(speakerProfileId = newProfile.id))
    }
    AppLog.voiceprint("创建并录入了全新声纹成员: $name (${cluster.samples.size} 个样本，方差: %.4f)".format(Locale.US, variance))
}

// MARK: - 子组件

@Composable
private fun RegisteredSpeakerCard(
    profile: SpeakerProfileEntity,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(profile.name) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val colorIndex = abs(profile.name.hashCode()) % VoiceBridgeTheme.speakerPalette.size
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(VoiceBridgeTheme.speakerPalette[colorIndex], CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(profile.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "已收录 ${profile.sampleCount} 个声纹样本 · 累计 %.0f 秒".format(Locale.US, profile.totalDuration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { renameInput = profile.name; showRenameDialog = true }) {
                Icon(Icons.Default.Edit, contentDescription = "重命名", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名发言人") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    placeholder = { Text("输入发言人姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameInput.trim()
                    if (trimmed.isNotEmpty() && trimmed != profile.name) onRename(trimmed)
                    showRenameDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除发言人 ${profile.name}？") },
            text = { Text("删除后，该发言人关联的 ${profile.sampleCount} 个声纹样本将重置为游离状态并重新聚类，既往纪要中的名字将改为「未知发言人」。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CandidateClusterCard(
    cluster: SpeakerClusterGroup,
    index: Int,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onAssign: () -> Unit,
    onSplit: () -> Unit,
    onIgnore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 播放按钮
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isPlaying) VoiceBridgeTheme.accent else VoiceBridgeTheme.accent.copy(alpha = 0.12f),
                            CircleShape
                        )
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "停止" else "试听",
                        tint = if (isPlaying) Color.White else VoiceBridgeTheme.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("未知声音 ${index + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${cluster.samples.size} 个样本 • %.1f 秒 • 来自 ${cluster.meetingCount} 场会议"
                                .format(Locale.US, cluster.totalDuration),
                            fontSize = 10.sp,
                            color = VoiceBridgeTheme.accent
                        )
                    }
                    Text(
                        "“${cluster.representativeText}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAssign,
                    colors = ButtonDefaults.buttonColors(containerColor = VoiceBridgeTheme.accent),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) { Text("标定", fontSize = 12.sp, color = Color.White) }

                if (cluster.samples.size > 1) {
                    TextButton(onClick = onSplit, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("拆分", fontSize = 12.sp)
                    }
                }

                TextButton(onClick = onIgnore, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("忽略", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignIdentitySheet(
    cluster: SpeakerClusterGroup,
    profiles: List<SpeakerProfileEntity>,
    onDismiss: () -> Unit,
    onAssignToExisting: (SpeakerProfileEntity) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(profiles.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf<SpeakerProfileEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("标记这组声音属于谁？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text(
                "包含 ${cluster.samples.size} 个声纹样本 · “${cluster.representativeText}”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 分支选择
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isCreatingNew,
                    onClick = { isCreatingNew = false },
                    label = { Text("合并到已有成员") },
                    enabled = profiles.isNotEmpty()
                )
                FilterChip(
                    selected = isCreatingNew,
                    onClick = { isCreatingNew = true },
                    label = { Text("登记全新成员") }
                )
            }

            if (isCreatingNew) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("输入发言人姓名，如：李总") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onCreateNew(newName.trim()) },
                    enabled = newName.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = VoiceBridgeTheme.accent),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("创建并批量绑定", color = Color.White) }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = selectedProfile?.id == profile.id,
                            onClick = { selectedProfile = profile },
                            label = { Text(profile.name) },
                            leadingIcon = {
                                if (selectedProfile?.id == profile.id) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
                Button(
                    onClick = { selectedProfile?.let(onAssignToExisting) },
                    enabled = selectedProfile != null,
                    colors = ButtonDefaults.buttonColors(containerColor = VoiceBridgeTheme.accent),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("批量归并到该成员", color = Color.White) }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
