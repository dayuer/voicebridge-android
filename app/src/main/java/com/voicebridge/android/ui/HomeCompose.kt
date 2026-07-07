package com.voicebridge.android.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
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
import com.voicebridge.android.data.relation.MeetingRecordComplete
import com.voicebridge.android.data.entity.MeetingRecordEntity
import com.voicebridge.android.service.ImportTaskQueue
import com.voicebridge.android.service.SherpaASRService
import com.voicebridge.android.service.DiarizationTaskQueue
import com.voicebridge.android.ui.theme.VoiceBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCompose(
    db: VoiceBridgeDatabase,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var records by remember { mutableStateOf<List<MeetingRecordComplete>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 从数据库实时加载所有会议
    LaunchedEffect(Unit) {
        db.meetingRecordDao().getAllMeetingsComplete().collectLatest { list ->
            records = list
        }
    }

    // ANE/ASR 引擎状态（检测本地离线模型文件包是否就绪）
    val isModelReady = remember {
        SherpaASRService.getInstance().isASRPackAvailable(context)
    }

    Scaffold(
        topBar = {
            // 顶栏：标题 + 引擎状态 + 设置按钮
            Surface(
                color = VoiceBridgeTheme.bgCanvas,
                modifier = Modifier.statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTab == 0) "录音库" else "会议纪要",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.textPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ANE 引擎状态灯
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(VoiceBridgeTheme.bgSurface, shape = CircleShape)
                                .border(0.5.dp, VoiceBridgeTheme.separator, shape = CircleShape)
                                .clickable { onNavigateToDiagnostics() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        color = if (isModelReady) VoiceBridgeTheme.success else Color.Red,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isModelReady) "离线引擎就绪" else "引擎未就绪",
                                fontSize = 11.sp,
                                color = VoiceBridgeTheme.textTertiary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "诊断",
                                tint = VoiceBridgeTheme.textDisabled,
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        // 设置占位图标
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(VoiceBridgeTheme.bgSurface, shape = CircleShape)
                                .clickable {
                                    onNavigateToSettings()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                                tint = VoiceBridgeTheme.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // 双 Tab 切换栏
            NavigationBar(
                containerColor = VoiceBridgeTheme.bgSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "录音",
                            tint = if (selectedTab == 0) VoiceBridgeTheme.accent else VoiceBridgeTheme.textDisabled
                        )
                    },
                    label = {
                        Text(
                            text = "录音",
                            color = if (selectedTab == 0) VoiceBridgeTheme.accent else VoiceBridgeTheme.textDisabled,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = VoiceBridgeTheme.accent.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "纪要",
                            tint = if (selectedTab == 1) VoiceBridgeTheme.accent else VoiceBridgeTheme.textDisabled
                        )
                    },
                    label = {
                        Text(
                            text = "纪要",
                            color = if (selectedTab == 1) VoiceBridgeTheme.accent else VoiceBridgeTheme.textDisabled,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = VoiceBridgeTheme.accent.copy(alpha = 0.12f)
                    )
                )
            }
        },
        containerColor = VoiceBridgeTheme.bgCanvas
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedTab == 0) {
                RecordingLibraryView(db, records, scope, onNavigateToDetail)
            } else {
                MeetingRecordsListView(records, onNavigateToDetail, db, scope)
            }
        }
    }
}

// ==========================================
// Tab 0: 录音库 (RecordingLibraryView)
// ==========================================
@Composable
fun RecordingLibraryView(
    db: VoiceBridgeDatabase,
    records: List<MeetingRecordComplete>,
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    var isImporting by remember { mutableStateOf(false) }

    // 筛选未完成/正在转写/失败的任务
    val meetings = records

    // 后台排队数
    val pendingCount by ImportTaskQueue.getInstance().pendingCount.collectAsState()

    // 调起系统音频文件选择器
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                var errMsg: String? = null
                val localPath = copyAudioFileToSandbox(context, uri) { err ->
                    errMsg = err
                }
                if (localPath != null) {
                    val meetingId = UUID.randomUUID().toString()
                    val fileName = File(localPath).name
                    val meeting = MeetingRecordEntity(
                        id = meetingId,
                        title = fileName,
                        startTime = Date(),
                        audioFilePath = localPath,
                        importProgress = 0.0,
                        isCompleted = false
                    )

                    withContext(Dispatchers.IO) {
                        db.meetingRecordDao().insert(meeting)
                    }

                    val queue = ImportTaskQueue.getInstance()
                    queue.configure(db)
                    queue.enqueue(
                        context,
                        ImportTaskQueue.ImportJob(meetingId, localPath, null)
                    )
                    Toast.makeText(context, "📥 音频已加入后台排队转译", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "❌ 复制文件到沙盒失败: ${errMsg ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
                isImporting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 导入语音文件主按钮 (全 App 唯一保留渐变的主 CTA)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(VoiceBridgeTheme.accent, VoiceBridgeTheme.accentGradientEnd)
                    )
                )
                .clickable(enabled = !isImporting) {
                    filePicker.launch("audio/*")
                }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "导入",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "导入语音文件",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (pendingCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "队列 $pendingCount",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "支持多选，导入后自动排队识别文字",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "增加",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 录音列表
        if (meetings.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "空状态",
                        tint = VoiceBridgeTheme.textDisabled,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "语音库还是空的",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = VoiceBridgeTheme.textSecondary
                    )
                    Text(
                        text = "导入手机里的录音/语音文件，\n畅译自动在后台排队完成文字识别",
                        fontSize = 12.sp,
                        color = VoiceBridgeTheme.textTertiary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(meetings, key = { it.meetingRecord.id }) { item ->
                    RecordingLibraryRow(item, db, scope, onNavigateToDetail)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingLibraryRow(
    complete: MeetingRecordComplete,
    db: VoiceBridgeDatabase,
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val record = complete.meetingRecord
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        color = VoiceBridgeTheme.bgSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (record.isCompleted) {
                        onNavigateToDetail(record.id)
                    } else if (record.importProgress < 0) {
                        // 失败重试
                        scope.launch(Dispatchers.IO) {
                            db.meetingRecordDao().update(record.copy(importProgress = 0.0))
                            val queue = ImportTaskQueue.getInstance()
                            queue.configure(db)
                            queue.enqueue(context, ImportTaskQueue.ImportJob(record.id, record.audioFilePath ?: "", null))
                        }
                    } else {
                        Toast.makeText(context, "📥 该录音正在转译中…", Toast.LENGTH_SHORT).show()
                    }
                },
                onLongClick = { showDeleteDialog = true }
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 状态指示圈
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = when {
                            record.isCompleted -> VoiceBridgeTheme.success.copy(alpha = 0.1f)
                            record.importProgress < 0 -> VoiceBridgeTheme.error.copy(alpha = 0.1f)
                            else -> VoiceBridgeTheme.accent.copy(alpha = 0.1f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    record.isCompleted -> {
                        Text(text = "✓", color = VoiceBridgeTheme.success, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    record.importProgress < 0 -> {
                        Text(text = "↺", color = VoiceBridgeTheme.error, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    else -> {
                        CircularProgressIndicator(
                            color = VoiceBridgeTheme.accent,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = record.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = VoiceBridgeTheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "时长",
                            tint = VoiceBridgeTheme.textTertiary,
                            modifier = Modifier.size(10.dp)
                        )
                        val min = (record.importProgress * 10).toInt() // 临时格式化
                        Text(
                            text = String.format("%02d:%02d", min, 0),
                            fontSize = 11.sp,
                            color = VoiceBridgeTheme.textTertiary
                        )
                    }
                    Text(
                        text = dateFormat.format(record.startTime),
                        fontSize = 11.sp,
                        color = VoiceBridgeTheme.textTertiary
                    )
                }
            }

            // 右侧状态 Badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (record.isCompleted) {
                    Surface(
                        shape = CircleShape,
                        color = VoiceBridgeTheme.success.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "已转写",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoiceBridgeTheme.success,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (record.diarizationState == 1 || record.diarizationState == 2) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .background(VoiceBridgeTheme.accent.copy(alpha = 0.1f), shape = CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            CircularProgressIndicator(color = VoiceBridgeTheme.accent, modifier = Modifier.size(8.dp), strokeWidth = 1.dp)
                            Text(text = "声纹分析中…", fontSize = 8.sp, fontWeight = FontWeight.Medium, color = VoiceBridgeTheme.accent)
                        }
                    } else if (record.diarizationState == 4) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier
                                .border(1.dp, VoiceBridgeTheme.error, shape = CircleShape)
                                .clickable {
                                    DiarizationTaskQueue
                                        .getInstance()
                                        .configure(db)
                                    DiarizationTaskQueue
                                        .getInstance()
                                        .enqueue(context, record.id, record.audioFilePath)
                                }
                        ) {
                            Text(
                                text = "↺ 声纹重试",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoiceBridgeTheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else if (record.importProgress < 0) {
                    Surface(
                        shape = CircleShape,
                        color = VoiceBridgeTheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "失败·点击重试",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoiceBridgeTheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = VoiceBridgeTheme.accent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${(record.importProgress * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoiceBridgeTheme.accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除会议?") },
            text = { Text("删除后，该会议纪要及所附声纹样本和转录段落将无法找回。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.meetingRecordDao().delete(record)
                            ImportTaskQueue.getInstance().removeJob(record.id)
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ==========================================
// Tab 1: 会议纪要 (MeetingRecordsListView)
// ==========================================
@Composable
fun MeetingRecordsListView(
    records: List<MeetingRecordComplete>,
    onNavigateToDetail: (String) -> Unit,
    db: VoiceBridgeDatabase,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var searchText by remember { mutableStateOf("") }
    val completedMeetings = records.filter { it.meetingRecord.isCompleted }

    val filteredMeetings = completedMeetings.filter { complete ->
        searchText.isEmpty() ||
                complete.meetingRecord.title.contains(searchText, ignoreCase = true) ||
                complete.segments.any { it.text.contains(searchText, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 搜索栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(VoiceBridgeTheme.bgSurface, shape = RoundedCornerShape(12.dp))
                .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = VoiceBridgeTheme.textTertiary,
                modifier = Modifier.size(16.dp)
            )

            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = {
                    Text(
                        "搜索会议纪要…",
                        color = VoiceBridgeTheme.textTertiary,
                        fontSize = 14.sp
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )

            if (searchText.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "清除",
                    tint = VoiceBridgeTheme.textDisabled,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { searchText = "" }
                )
            }
        }

        // 列表展示
        if (filteredMeetings.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "空状态",
                        tint = VoiceBridgeTheme.textDisabled,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = if (searchText.isEmpty()) "还没有会议纪要" else "未找到匹配内容",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = VoiceBridgeTheme.textSecondary
                    )
                    if (searchText.isEmpty()) {
                        Text(
                            text = "在「录音」导入语音文件，\n转写完成后纪要会出现在这里",
                            fontSize = 12.sp,
                            color = VoiceBridgeTheme.textTertiary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredMeetings, key = { it.meetingRecord.id }) { item ->
                    MeetingCard(item, onNavigateToDetail, db, scope)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeetingCard(
    complete: MeetingRecordComplete,
    onClick: (String) -> Unit,
    db: VoiceBridgeDatabase,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val record = complete.meetingRecord
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    var showDeleteDialog by remember { mutableStateOf(false) }

    val contentSnippet = when {
        !record.aiSummarySnippet.isNullOrEmpty() -> record.aiSummarySnippet!!
        complete.segments.isNotEmpty() -> {
            complete.segments.sortedBy { it.timestamp }.take(3).map { it.text.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        }
        else -> "无内容"
    }

    Surface(
        color = VoiceBridgeTheme.bgSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onClick(record.id) },
                onLongClick = { showDeleteDialog = true }
            )
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (record.title.isEmpty()) "未命名会议" else record.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoiceBridgeTheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (!complete.aiItems.isEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = VoiceBridgeTheme.accent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "AI 纪要",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoiceBridgeTheme.accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "完成",
                        tint = VoiceBridgeTheme.success,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = contentSnippet,
                fontSize = 14.sp,
                color = VoiceBridgeTheme.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "时间",
                        tint = VoiceBridgeTheme.textDisabled,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = dateFormat.format(record.startTime),
                        fontSize = 12.sp,
                        color = VoiceBridgeTheme.textTertiary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "时长",
                        tint = VoiceBridgeTheme.textDisabled,
                        modifier = Modifier.size(12.dp)
                    )
                    val min = (record.importProgress * 10).toInt() // 临时格式化
                    Text(
                        text = String.format("%02d:%02d", min, 0),
                        fontSize = 12.sp,
                        color = VoiceBridgeTheme.textTertiary
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除会议?") },
            text = { Text("删除后，该会议纪要及所附声纹样本和转录段落将无法找回。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.meetingRecordDao().delete(record)
                            ImportTaskQueue.getInstance().removeJob(record.id)
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// 物理拷贝 Uri 到沙盒目录
private suspend fun copyAudioFileToSandbox(
    context: Context,
    uri: Uri,
    onError: (String) -> Unit
): String? {
    val resolver = context.contentResolver
    
    // 1. 在主线程/发起线程尝试打开输入流，确保临时 URI 读取权限在 binder 调用中有效
    val inputStream = try {
        resolver.openInputStream(uri)
    } catch (e: Exception) {
        android.util.Log.e("HomeCompose", "Failed to open input stream: ${e.message}", e)
        onError("打开音频源失败: ${e.message}")
        return null
    }

    if (inputStream == null) {
        onError("音频源数据流为空")
        return null
    }

    // 2. 并在发起线程查询文件名
    val rawName = try {
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
    } catch (e: Exception) {
        null
    } ?: "imported_audio_${System.currentTimeMillis()}.m4a"

    // 净化文件名，提取纯文件名并移除非法字符，防止创建 FileOutputStream 时路径未找到
    val cleanName = rawName.substringAfterLast('/').substringAfterLast('\\')
    val displayName = if (cleanName.isBlank()) {
        "imported_audio_${System.currentTimeMillis()}.m4a"
    } else {
        cleanName.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
    }

    // 3. 在 IO 协程中进行文件物理读写
    return withContext(Dispatchers.IO) {
        val recordingsDir = File(context.filesDir, "Documents/Recordings")
        if (!recordingsDir.exists()) {
            val created = recordingsDir.mkdirs()
            if (!created) {
                try { inputStream.close() } catch (ex: Exception) {}
                onError("无法创建沙盒目录: ${recordingsDir.absolutePath}")
                return@withContext null
            }
        }

        val destFile = File(recordingsDir, displayName)
        try {
            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            "Documents/Recordings/$displayName"
        } catch (e: Exception) {
            android.util.Log.e("HomeCompose", "Failed to write file to sandbox: ${e.message}", e)
            onError("写入文件失败: ${e.message}")
            null
        }
    }
}
