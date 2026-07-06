package com.voicebridge.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.MeetingRecordEntity
import com.voicebridge.android.data.relation.MeetingRecordComplete
import com.voicebridge.android.service.ImportTaskQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCompose(
    db: VoiceBridgeDatabase,
    onNavigateToDetail: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "VoiceBridge",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "纪要") },
                    label = { Text("纪要列表") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Add, contentDescription = "录音") },
                    label = { Text("导入录音") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            if (selectedTab == 0) {
                MeetingRecordsListView(db, onNavigateToDetail)
            } else {
                RecordingLibraryView(db)
            }
        }
    }
}

@Composable
fun MeetingRecordsListView(
    db: VoiceBridgeDatabase,
    onNavigateToDetail: (String) -> Unit
) {
    var records by remember { mutableStateOf<List<MeetingRecordComplete>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        db.meetingRecordDao().getAllMeetingsComplete().collectLatest { list ->
            records = list
        }
    }

    val filteredRecords = records.filter {
        it.meetingRecord.title.contains(searchQuery, ignoreCase = true) ||
                (it.meetingRecord.aiSummary?.contains(searchQuery, ignoreCase = true) == true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索会议纪要或 AI 总结") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp)
        )

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无相关会议纪要",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredRecords, key = { it.meetingRecord.id }) { item ->
                    MeetingRecordCard(item, onNavigateToDetail, db)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeetingRecordCard(
    complete: MeetingRecordComplete,
    onClick: (String) -> Unit,
    db: VoiceBridgeDatabase
) {
    val record = complete.meetingRecord
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(record.id) },
                onLongClick = { showDeleteDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // 声纹分离状态指示器
                val stateText = when (record.diarizationState) {
                    0 -> "ASR 就绪"
                    1 -> "等候声纹"
                    2 -> "声纹分析中"
                    3 -> "声纹完毕"
                    else -> "分析失败"
                }
                val stateColor = when (record.diarizationState) {
                    3 -> Color(0xFF4CAF50)
                    2 -> Color(0xFFFF9800)
                    else -> Color.Gray
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = stateColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = stateText,
                        color = stateColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateFormat.format(record.startTime),
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))
            val summarySnippet = record.aiSummarySnippet ?: record.aiSummary ?: "无 AI 多维度分析"
            Text(
                text = summarySnippet,
                fontSize = 14.sp,
                color = Color.DarkGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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

@Composable
fun RecordingLibraryView(db: VoiceBridgeDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<MeetingRecordComplete>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.meetingRecordDao().getAllMeetingsComplete().collectLatest { list ->
            records = list
        }
    }

    val incompleteRecords = records.filter { !it.meetingRecord.isCompleted }

    // 调用多媒体文件选择器
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val localPath = copyAudioFileToSandbox(context, uri)
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
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { filePicker.launch("audio/*") },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "导入")
            Spacer(modifier = Modifier.width(8.dp))
            Text("多选或导入音频文件", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Divider()

        Text(
            text = "后台导入转写队列 (${incompleteRecords.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        if (incompleteRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无处理中的队列", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(incompleteRecords) { item ->
                    QueueProgressCard(item)
                }
            }
        }
    }
}

@Composable
fun QueueProgressCard(complete: MeetingRecordComplete) {
    val record = complete.meetingRecord
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = record.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = record.importProgress.toFloat().coerceIn(0f, 1f),
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${(record.importProgress * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private suspend fun copyAudioFileToSandbox(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val recordingsDir = File(context.filesDir, "Documents/Recordings")
    if (!recordingsDir.exists()) recordingsDir.mkdirs()

    val cursor = resolver.query(uri, null, null, null, null)
    val displayName = cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) it.getString(idx) else null
        } else null
    } ?: "imported_audio_${System.currentTimeMillis()}.m4a"

    val destFile = File(recordingsDir.absolutePath, displayName)
    try {
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        "Documents/Recordings/$displayName"
    } catch (e: Exception) {
        null
    }
}
