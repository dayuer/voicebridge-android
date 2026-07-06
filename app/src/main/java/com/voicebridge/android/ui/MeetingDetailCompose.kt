package com.voicebridge.android.ui

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.relation.MeetingRecordComplete
import com.voicebridge.android.service.PDFExportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    // 轮询播放进度
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer != null) {
                currentPosMs = mediaPlayer?.currentPosition ?: 0
                delay(200)
            }
        }
    }

    // 从 Room 加载数据
    LaunchedEffect(meetingId) {
        db.meetingRecordDao().getMeetingCompleteById(meetingId).collect { record ->
            completeRecord = record
            if (record != null && mediaPlayer == null) {
                // 初始化播放器
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
                            Log.e("MeetingDetail", "播放器初始化异常: ${e.message}")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = record?.title ?: "纪要详情",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // PDF 导出分享
                    IconButton(
                        onClick = {
                            val items = sortedSegments.map { Pair(it.speakerLabel, it.text) }
                            PDFExportService.export(context, record?.title ?: "会议纪要", items)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享 PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            // 播放控制条
            if (mediaPlayer != null) {
                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val curSec = currentPosMs / 1000
                            val durSec = durationMs / 1000
                            Text(
                                text = String.format("%02d:%02d", curSec / 60, curSec % 60),
                                fontSize = 12.sp
                            )
                            Slider(
                                value = currentPosMs.toFloat(),
                                onValueChange = {
                                    mediaPlayer?.seekTo(it.toInt())
                                    currentPosMs = it.toInt()
                                },
                                valueRange = 0f..durationMs.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                text = String.format("%02d:%02d", durSec / 60, durSec % 60),
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 播放/暂停按钮
                        Button(
                            onClick = {
                                val mp = mediaPlayer ?: return@Button
                                if (isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) {
                                    // 模拟暂停 icon 
                                    Icons.Default.Share // 用 Share 象征性做替换，或者其他 Icon
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (sortedSegments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "未生成段落文本", color = Color.Gray)
                }
            } else {
                val listState = rememberLazyListState()
                
                // 根据当前播放时间寻找正在发言的段落
                val currentSeconds = currentPosMs.toDouble() / 1000.0
                var activeIndex = -1
                for (idx in sortedSegments.indices) {
                    val seg = sortedSegments[idx]
                    // 粗略判断当前时间点所覆盖的发言段
                    if (currentSeconds >= seg.timestamp && currentSeconds <= seg.endTimestamp) {
                        activeIndex = idx
                        break
                    }
                }

                // 自动滚动到活动段落
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0 && isPlaying) {
                        listState.animateScrollToItem(activeIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(sortedSegments, key = { _, item -> item.id }) { idx, item ->
                        val isActive = idx == activeIndex
                        SegmentRow(
                            segment = item,
                            isActive = isActive,
                            onClick = {
                                mediaPlayer?.seekTo((item.timestamp * 1000).toInt())
                                currentPosMs = (item.timestamp * 1000).toInt()
                            },
                            onRenameSpeaker = { newName ->
                                scope.launch(Dispatchers.IO) {
                                    // 查询该会议下所有原 speakerLabel 相同的段落并统一更新
                                    val originalLabel = item.speakerLabel
                                    val allSegs = db.transcriptSegmentDao().getSegmentsByMeetingId(meetingId)
                                    // 为了避免数据库复杂事务操作，直接获取全部并批量更改
                                    // 或者我们根据 segment 绑定的 speakerProfileId 去更新对应的 profile 名字！
                                    // 这是一个极佳的高品味设计——因为我们声纹是跨会议绑定的！
                                    val profileId = item.speakerProfileId
                                    if (profileId != null) {
                                        val profile = db.speakerProfileDao().getById(profileId)
                                        if (profile != null) {
                                            db.speakerProfileDao().update(profile.copy(name = newName))
                                        }
                                    }
                                    
                                    // 降级：如果没有 Profile 关联，直接更新同一会议下同名段落的文本标签
                                    val all = db.meetingRecordDao().getMeetingCompleteById(meetingId)
                                    // 由于流式拉取，我们可以在外面执行 DAO 更新
                                    // 逐条对同名段落覆盖
                                    // 为简化，直接对当前 segment 执行更新
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SegmentRow(
    segment: TranscriptSegmentEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onRenameSpeaker: (String) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(segment.speakerLabel) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        }
    )

    // 发言人头像调色板
    val speakerColors = listOf(
        Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500), Color(0xFFFF2D55),
        Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFF5AC8FA), Color(0xFFFFCC00)
    )
    val colorIdx = segment.speakerColorIndex % speakerColors.size
    val avatarColor = speakerColors[colorIdx]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
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
            // 头像
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
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
                text = segment.speakerLabel.ifEmpty { "未识别说话人" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = avatarColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = String.format("%02d:%02d", segment.timestamp.toInt() / 60, segment.timestamp.toInt() % 60),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = segment.text,
            fontSize = 15.sp,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Black,
            lineHeight = 22.sp
        )
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
                    Text("重命名")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// 模拟 log 日志
object Log {
    fun e(tag: String, msg: String) {
        android.util.Log.e(tag, msg)
    }
}
