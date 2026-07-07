package com.voicebridge.android.ui

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.AISummaryItemEntity
import com.voicebridge.android.data.entity.TranscriptSegmentEntity
import com.voicebridge.android.data.relation.MeetingRecordComplete
import com.voicebridge.android.service.PDFExportService
import com.voicebridge.android.ui.theme.VoiceBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailCompose(
    meetingId: String,
    db: VoiceBridgeDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var completeRecord by remember { mutableStateOf<MeetingRecordComplete?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // Segment tab: 0 = 智能纪要, 1 = 会议逐字稿
    var selectedSegmentTab by remember { mutableStateOf(0) }

    // AI category tag index: 0 = 智能总结, 1 = 会议速览, 2 = 发言人占比, 3 = 待办事项
    var selectedAICategoryIndex by remember { mutableStateOf(0) }

    // 轮询播放进度
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer != null) {
                currentPosMs = mediaPlayer?.currentPosition ?: 0
                delay(250)
            }
        }
    }

    // 从 Room 加载数据
    LaunchedEffect(meetingId) {
        db.meetingRecordDao().getMeetingCompleteById(meetingId).collect { record ->
            completeRecord = record
            if (record != null && mediaPlayer == null) {
                val path = record.meetingRecord.audioFilePath
                if (!path.isNullOrEmpty()) {
                    val audioFile = File(path)
                    val absolutePath = if (audioFile.isAbsolute) {
                        audioFile.absolutePath
                    } else {
                        File(context.filesDir, path).absolutePath
                    }
                    if (File(absolutePath).exists()) {
                        try {
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(absolutePath)
                                prepare()
                                durationMs = duration
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPosMs = 0
                                    seekTo(0)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MeetingDetail", "播放器初始化异常: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    // 释放资源
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val record = completeRecord?.meetingRecord
    val segments = completeRecord?.segments ?: emptyList()
    val sortedSegments = segments.sortedBy { it.timestamp }
    val aiItems = completeRecord?.aiItems ?: emptyList()

    Scaffold(
        topBar = {
            Surface(
                color = VoiceBridgeTheme.bgCanvas,
                modifier = Modifier.statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = VoiceBridgeTheme.textPrimary)
                    }

                    Text(
                        text = record?.title ?: "会议纪要",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = {
                            val items = sortedSegments.map { Pair(it.speakerLabel, it.text) }
                            PDFExportService.export(context, record?.title ?: "会议纪要", items)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = VoiceBridgeTheme.textPrimary)
                    }
                }
            }
        },
        containerColor = VoiceBridgeTheme.bgCanvas
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Audio Player Card (iOS frosted-glass style card)
            Surface(
                color = VoiceBridgeTheme.bgSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Playback progress slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val curSec = currentPosMs / 1000
                        val durSec = durationMs / 1000
                        Text(
                            text = String.format("%02d:%02d", curSec / 60, curSec % 60),
                            fontSize = 11.sp,
                            color = VoiceBridgeTheme.textTertiary,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = currentPosMs.toFloat(),
                            onValueChange = {
                                mediaPlayer?.seekTo(it.toInt())
                                currentPosMs = it.toInt()
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = VoiceBridgeTheme.accent,
                                activeTrackColor = VoiceBridgeTheme.accent,
                                inactiveTrackColor = VoiceBridgeTheme.separator
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Text(
                            text = String.format("%02d:%02d", durSec / 60, durSec % 60),
                            fontSize = 11.sp,
                            color = VoiceBridgeTheme.textTertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Playback controls row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 15s
                        IconButton(
                            onClick = {
                                mediaPlayer?.let {
                                    val newPos = (it.currentPosition - 15000).coerceAtLeast(0)
                                    it.seekTo(newPos)
                                    currentPosMs = newPos
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "快退15秒",
                                tint = VoiceBridgeTheme.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play/Pause Big Button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(VoiceBridgeTheme.accent, shape = CircleShape)
                                .clickable {
                                    mediaPlayer?.let { mp ->
                                        if (isPlaying) {
                                            mp.pause()
                                            isPlaying = false
                                        } else {
                                            // Apply speed
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                try {
                                                    val params = mp.playbackParams
                                                    params.speed = playbackSpeed
                                                    mp.playbackParams = params
                                                } catch (e: Exception) {
                                                    // ignore
                                                }
                                            }
                                            mp.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Share else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Forward 15s
                        IconButton(
                            onClick = {
                                mediaPlayer?.let {
                                    val newPos = (it.currentPosition + 15000).coerceAtMost(durationMs)
                                    it.seekTo(newPos)
                                    currentPosMs = newPos
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "快进15秒",
                                tint = VoiceBridgeTheme.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Playback Speed Selector (Cycles speeds: 1.0 -> 1.25 -> 1.5 -> 2.0 -> 1.0)
                        Surface(
                            shape = CircleShape,
                            color = VoiceBridgeTheme.bgElevated,
                            modifier = Modifier
                                .border(0.5.dp, VoiceBridgeTheme.separator, shape = CircleShape)
                                .clickable {
                                    playbackSpeed = when (playbackSpeed) {
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        else -> 1.0f
                                    }
                                    mediaPlayer?.let { mp ->
                                        if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            try {
                                                val params = mp.playbackParams
                                                params.speed = playbackSpeed
                                                mp.playbackParams = params
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoiceBridgeTheme.textSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // 2. Segmented Tab Picker: "智能纪要" vs "会议逐字稿"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoiceBridgeTheme.bgSurface, shape = RoundedCornerShape(12.dp))
                    .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedSegmentTab == 0) VoiceBridgeTheme.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { selectedSegmentTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "智能纪要",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedSegmentTab == 0) VoiceBridgeTheme.accent else VoiceBridgeTheme.textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedSegmentTab == 1) VoiceBridgeTheme.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { selectedSegmentTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "会议逐字稿",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedSegmentTab == 1) VoiceBridgeTheme.accent else VoiceBridgeTheme.textSecondary
                    )
                }
            }

            // 3. Tab contents
            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (selectedSegmentTab == 0) {
                    // TAB 0: 智能纪要 (AISummaryView)
                    if (aiItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "暂无 AI 智能纪要",
                                    fontSize = 14.sp,
                                    color = VoiceBridgeTheme.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val generated = generateLocalSummary(meetingId, sortedSegments)
                                            withContext(Dispatchers.IO) {
                                                db.aiSummaryItemDao().insertAll(generated)
                                                // 更新会议主表的摘要片段
                                                record?.let {
                                                    val snippet = generated.firstOrNull { it.typeKey == "summary" }?.content ?: ""
                                                    val cleanSnippet = snippet.replace(Regex("###|\\*\\*|\\*|-|#|\\n"), "").take(120)
                                                    db.meetingRecordDao().update(it.copy(aiSummarySnippet = cleanSnippet))
                                                }
                                            }
                                            Toast.makeText(context, "✨ 本地 AI 纪要生成成功", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VoiceBridgeTheme.accent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("一键提取 AI 纪要", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Category Tags Horizontal Carousel
                            val categories = listOf("智能总结", "会议速览", "发言人占比", "待办事项")
                            val keys = listOf("summary", "overview", "speaker_stats", "todos")

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(categories) { index, label ->
                                    val isSelected = selectedAICategoryIndex == index
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) VoiceBridgeTheme.accent else VoiceBridgeTheme.bgSurface,
                                        modifier = Modifier
                                            .border(0.5.dp, VoiceBridgeTheme.separator, shape = CircleShape)
                                            .clickable { selectedAICategoryIndex = index }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else VoiceBridgeTheme.textSecondary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            // Show category content
                            val activeKey = keys.getOrNull(selectedAICategoryIndex) ?: "summary"
                            val activeContent = aiItems.find { it.typeKey == activeKey }?.content ?: "暂无此分类内容"

                            Surface(
                                color = VoiceBridgeTheme.bgSurface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        RenderMarkdownText(activeContent)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: 会议逐字稿 (TranscriptView)
                    if (sortedSegments.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "未生成段落文本", color = VoiceBridgeTheme.textTertiary)
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val currentSeconds = currentPosMs.toDouble() / 1000.0

                        var activeIndex = -1
                        for (idx in sortedSegments.indices) {
                            val seg = sortedSegments[idx]
                            if (currentSeconds >= seg.timestamp && currentSeconds <= seg.endTimestamp) {
                                activeIndex = idx
                                break
                            }
                        }

                        LaunchedEffect(activeIndex) {
                            if (activeIndex >= 0 && isPlaying) {
                                listState.animateScrollToItem(activeIndex)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(sortedSegments, key = { _, item -> item.id }) { idx, item ->
                                val isActive = idx == activeIndex
                                TranscriptSegmentRow(
                                    segment = item,
                                    isActive = isActive,
                                    onClick = {
                                        mediaPlayer?.seekTo((item.timestamp * 1000).toInt())
                                        currentPosMs = (item.timestamp * 1000).toInt()
                                        if (mediaPlayer != null && !isPlaying) {
                                            mediaPlayer?.start()
                                            isPlaying = true
                                        }
                                    },
                                    onRenameSpeaker = { newName ->
                                        scope.launch(Dispatchers.IO) {
                                            val profileId = item.speakerProfileId
                                            if (profileId != null) {
                                                val profile = db.speakerProfileDao().getById(profileId)
                                                if (profile != null) {
                                                    db.speakerProfileDao().update(profile.copy(name = newName))
                                                }
                                            }
                                            db.transcriptSegmentDao().update(
                                                item.copy(speakerLabel = newName)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TranscriptSegmentRow(
    segment: TranscriptSegmentEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onRenameSpeaker: (String) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(segment.speakerLabel) }

    val rowBgColor by animateColorAsState(
        targetValue = if (isActive) {
            VoiceBridgeTheme.accent.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        }
    )

    // Using the matching Okabe-Ito palette colors
    val palette = VoiceBridgeTheme.speakerPalette
    val colorIdx = segment.speakerColorIndex % palette.size
    val speakerColor = palette[colorIdx]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showRenameDialog = true }
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Speaker icon bubble
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(speakerColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (segment.speakerLabel.isNotEmpty()) segment.speakerLabel.take(1) else "?",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = segment.speakerLabel.ifEmpty { "未命名发言人" },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = speakerColor,
                modifier = Modifier.clickable { showRenameDialog = true }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = String.format("%02d:%02d", segment.timestamp.toInt() / 60, segment.timestamp.toInt() % 60),
                fontSize = 11.sp,
                color = VoiceBridgeTheme.textTertiary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = segment.text,
            fontSize = 14.sp,
            color = if (isActive) VoiceBridgeTheme.accent else VoiceBridgeTheme.textPrimary,
            lineHeight = 22.sp
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名发言人", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("发言人姓名") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.trim().isNotEmpty()) {
                            onRenameSpeaker(renameInput.trim())
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("重命名", color = VoiceBridgeTheme.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消", color = VoiceBridgeTheme.textSecondary)
                }
            }
        )
    }
}

// Simple local Markdown formatter to display AI content elegantly
@Composable
fun RenderMarkdownText(markdown: String) {
    val lines = markdown.split("\n")
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("###") -> {
                    val text = trimmed.replace("###", "").trim()
                    Text(
                        text = text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.textPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("##") -> {
                    val text = trimmed.replace("##", "").trim()
                    Text(
                        text = text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.textPrimary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("-") -> {
                    // Check if it's todo checkbox
                    val isChecked = trimmed.contains("- [x]")
                    val isUnchecked = trimmed.contains("- [ ]")
                    if (isChecked || isUnchecked) {
                        val clean = trimmed.replace("- [x]", "").replace("- [ ]", "").trim()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isChecked) "☑" else "☐",
                                fontSize = 14.sp,
                                color = if (isChecked) VoiceBridgeTheme.success else VoiceBridgeTheme.textTertiary
                            )
                            Text(
                                text = clean,
                                fontSize = 13.sp,
                                color = VoiceBridgeTheme.textSecondary
                            )
                        }
                    } else {
                        val clean = trimmed.replace("-", "").trim()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "•", fontSize = 14.sp, color = VoiceBridgeTheme.accent)
                            Text(text = clean, fontSize = 13.sp, color = VoiceBridgeTheme.textSecondary)
                        }
                    }
                }
                trimmed.startsWith("---") -> {
                    HorizontalDivider(
                        color = VoiceBridgeTheme.separator.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                trimmed.isNotEmpty() -> {
                    Text(
                        text = trimmed,
                        fontSize = 13.sp,
                        color = VoiceBridgeTheme.textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// Generate local AI summaries on demand
private fun generateLocalSummary(meetingId: String, segments: List<TranscriptSegmentEntity>): List<AISummaryItemEntity> {
    if (segments.isEmpty()) return emptyList()

    val totalChars = segments.sumOf { it.text.length }
    val speakerLengths = segments.groupBy { it.speakerLabel }
        .mapValues { (_, segs) -> segs.sumOf { it.text.length } }
    val totalSpeakerChars = speakerLengths.values.sum().toFloat().coerceAtLeast(1f)

    val speakerPercent = speakerLengths.entries.sortedByDescending { it.value }
        .joinToString("\n") { (speaker, count) ->
            val percent = (count / totalSpeakerChars * 100).toInt()
            "- **$speaker**: 占比约 **$percent%** (${count} 字)"
        }

    val actionKeywords = listOf("需要", "去做", "安排", "记得", "对接", "跟进", "完成", "负责", "计划", "必须", "todo", "优化", "重构")
    val todoLines = segments.filter { seg ->
        actionKeywords.any { kw -> seg.text.contains(kw) }
    }.take(8).map { seg ->
        "- [ ] **[${seg.speakerLabel}]**: ${seg.text.trim()}"
    }.joinToString("\n")

    val finalTodos = if (todoLines.isNotEmpty()) todoLines else "- [ ] **[无待办]**: 本次会议未提及明确的任务分配。"

    val firstText = segments.firstOrNull()?.text ?: ""
    val overview = """
        ### 📅 会议基础概览
        - **总发言段落**: ${segments.size} 段
        - **总文本长度**: ${totalChars} 字
        - **第一句话**: "$firstText"
        
        ### 💡 核心议题简述
        会议围绕上述段落展开，主要发言人包括: ${speakerLengths.keys.joinToString(", ")}。
    """.trimIndent()

    val summary = """
        ### 🔍 会议总结
        本次会议讨论了多个主题，转录段落的整体摘要如下：
        
        ${segments.take(5).mapIndexed { i, seg -> "${i+1}. **[${seg.speakerLabel}]**: ${seg.text.trim()}" }.joinToString("\n")}
        
        ---
        *注：以上总结由 VoiceBridge 本地离线智能引擎提取生成。*
    """.trimIndent()

    return listOf(
        AISummaryItemEntity(id = UUID.randomUUID().toString(), typeKey = "summary", content = summary, updatedAt = Date(), meetingId = meetingId),
        AISummaryItemEntity(id = UUID.randomUUID().toString(), typeKey = "overview", content = overview, updatedAt = Date(), meetingId = meetingId),
        AISummaryItemEntity(id = UUID.randomUUID().toString(), typeKey = "speaker_stats", content = speakerPercent, updatedAt = Date(), meetingId = meetingId),
        AISummaryItemEntity(id = UUID.randomUUID().toString(), typeKey = "todos", content = finalTodos, updatedAt = Date(), meetingId = meetingId)
    )
}
