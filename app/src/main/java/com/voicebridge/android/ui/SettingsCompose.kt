package com.voicebridge.android.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.GlossaryEntryEntity
import com.voicebridge.android.data.entity.SpeakerProfileEntity
import com.voicebridge.android.ui.theme.VoiceBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCompose(
    db: VoiceBridgeDatabase,
    initialSubView: String? = null,
    onRevokePrivacy: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentSubView by remember { mutableStateOf<String?>(if (initialSubView.isNullOrEmpty()) null else initialSubView) }
    var glossaryList by remember { mutableStateOf<List<GlossaryEntryEntity>>(emptyList()) }
    var speakerList by remember { mutableStateOf<List<SpeakerProfileEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.glossaryEntryDao().getAllEntries().collect { list -> glossaryList = list }
    }
    LaunchedEffect(Unit) {
        db.speakerProfileDao().getAllProfiles().collect { list -> speakerList = list }
    }

    if (currentSubView == "glossary") {
        GlossarySubView(db = db, list = glossaryList, onBack = { currentSubView = null })
    } else if (currentSubView == "speaker") {
        SpeakerSubView(db = db, list = speakerList, onBack = { currentSubView = null })
    } else if (currentSubView == "diagnostics") {
        DiagnosticsSubView(context = context, onBack = { currentSubView = null })
    } else if (currentSubView == "prompt") {
        PromptManagementCompose(onNavigateUp = { currentSubView = null })
    } else {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text("设置", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Section: 会议与识别
                item { SettingsSectionHeader("会议与识别") }
                
                item {
                    ListItem(
                        headlineContent = { Text("默认识别语言") },
                        supportingContent = { Text("导入音频时的默认语种") },
                        trailingContent = { Text("自动检测", color = MaterialTheme.colorScheme.primary) },
                        leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "已默认为自动语种识别", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item {
                    ListItem(
                        headlineContent = { Text("AI 提示词") },
                        supportingContent = { Text("管理智能纪要的 5 套默认模板") },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                        modifier = Modifier.clickable { currentSubView = "prompt" }
                    )
                }

                item {
                    ListItem(
                        headlineContent = { Text("自定义词库") },
                        supportingContent = { Text("添加专有名词提升纪要准确度") },
                        trailingContent = { Text("${glossaryList.size} 词条") },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                        modifier = Modifier.clickable { currentSubView = "glossary" }
                    )
                }

                item {
                    ListItem(
                        headlineContent = { Text("声纹管理") },
                        supportingContent = { Text("管理离线声纹特征与发言人") },
                        trailingContent = { Text("${speakerList.size} 个发言人") },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                        modifier = Modifier.clickable { currentSubView = "speaker" }
                    )
                }

                // Section: 系统与诊断
                item { SettingsSectionHeader("系统与诊断") }

                item {
                    ListItem(
                        headlineContent = { Text("离线引擎自检与诊断") },
                        supportingContent = { Text("检查 6 个核心离线模型就绪情况") },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { currentSubView = "diagnostics" }
                    )
                }

                item {
                    var showRevokeConfirm by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("服务与隐私授权") },
                        supportingContent = { Text("已授权服务与隐私协议") },
                        trailingContent = { Text("管理", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { showRevokeConfirm = true }
                    )
                    
                    if (showRevokeConfirm) {
                        AlertDialog(
                            onDismissRequest = { showRevokeConfirm = false },
                            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                            title = { Text("撤销授权？") },
                            text = { Text("撤销后 AI 智能纪要功能将锁定不可用，下次启动时将重新征求同意。") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        context.getSharedPreferences("voicebridge_settings", Context.MODE_PRIVATE)
                                            .edit().putBoolean("has_agreed_privacy", false).apply()
                                        showRevokeConfirm = false
                                        onRevokePrivacy()
                                    }
                                ) { Text("确认撤销", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRevokeConfirm = false }) { Text("取消") }
                            }
                        )
                    }
                }

                // Section: 关于
                item { SettingsSectionHeader("关于") }

                item {
                    ListItem(
                        headlineContent = { Text("软件版本") },
                        trailingContent = { Text("1.0.0.2 (Build 11)") }
                    )
                    ListItem(
                        headlineContent = { Text("识别引擎") },
                        trailingContent = { Text("SenseVoice + Silero VAD") }
                    )
                    ListItem(
                        headlineContent = { Text("隐私合规") },
                        trailingContent = { Text("100% 本地运行·无数据上传") }
                    )
                }

                // Section: 调试
                item { SettingsSectionHeader("调试") }

                item {
                    var showClearConfirm by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("清空所有历史声纹库", color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("擦除所有已存声纹特征与归属，用于开发调试") },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { showClearConfirm = true }
                    )

                    if (showClearConfirm) {
                        AlertDialog(
                            onDismissRequest = { showClearConfirm = false },
                            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            title = { Text("清空声纹数据？") },
                            text = { Text("将永久注销所有已存声纹身份卡，并重置所有既往会议纪要的角色。") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            db.speakerProfileDao().getAllProfiles().take(1).collect { list ->
                                                list.forEach { db.speakerProfileDao().delete(it) }
                                            }
                                            db.meetingRecordDao().getAllMeetingsComplete().take(1).collect { records ->
                                                records.forEach { complete ->
                                                    complete.segments.forEach { segment ->
                                                        db.transcriptSegmentDao().update(
                                                            segment.copy(
                                                                speakerProfileId = null,
                                                                speakerLabel = "未知发言人",
                                                                speakerColorIndex = 0
                                                            )
                                                        )
                                                    }
                                                    complete.voiceSamples.forEach {
                                                        db.voiceSampleDao().delete(it)
                                                    }
                                                }
                                            }
                                        }
                                        Toast.makeText(context, "声纹已重置", Toast.LENGTH_SHORT).show()
                                        showClearConfirm = false
                                    }
                                ) { Text("一键清空", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

// --------------------------------------------------
// 以下为复用原来的逻辑，仅做少量 Material 3 UI 调整以适配
// --------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossarySubView(db: VoiceBridgeDatabase, list: List<GlossaryEntryEntity>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var newTermInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义词库") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = {
                        if (list.size >= GlossaryEntryEntity.MAX_ENTRY_COUNT) {
                            Toast.makeText(context, "最多只能添加 ${GlossaryEntryEntity.MAX_ENTRY_COUNT} 条", Toast.LENGTH_SHORT).show()
                        } else {
                            newTermInput = ""
                            showAddDialog = true
                        }
                    }) { Text("添加") }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = "💡 可用于添加特定的人名或专业名词。在流式转译和 AI 归纳中优先匹配本词库，提高准确度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (list.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("词库内没有词条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(list, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.term) },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) { db.glossaryEntryDao().delete(item) }
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增词库词条") },
            text = {
                OutlinedTextField(
                    value = newTermInput,
                    onValueChange = { newTermInput = it },
                    singleLine = true,
                    placeholder = { Text("请输入专业词汇") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val input = newTermInput.trim()
                    if (input.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            db.glossaryEntryDao().insertRaw(GlossaryEntryEntity(
                                id = UUID.randomUUID().toString(), term = input, createdAt = Date()
                            ))
                        }
                    }
                    showAddDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerSubView(db: VoiceBridgeDatabase, list: List<SpeakerProfileEntity>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("声纹指纹库") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = "⚙️ 声纹特征是在本地进行 Diarization(话轮识别) 时提取的 512 维向量，用于识别同一发言人。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (list.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("尚未登记任何本地离线声纹角色", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(list, key = { it.id }) { item ->
                    ListItem(
                        leadingContent = {
                            val colorIndex = abs(item.name.hashCode()) % VoiceBridgeTheme.speakerPalette.size
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(VoiceBridgeTheme.speakerPalette[colorIndex], CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        headlineContent = { Text(item.name) },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) { db.speakerProfileDao().delete(item) }
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "注销", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

data class ModelDiagnostics(val name: String, val desc: String, val exists: Boolean, val sizeKb: Long)

fun getModelDiagnostics(context: Context): List<ModelDiagnostics> {
    val models = listOf(
        "model.int8.onnx" to "SenseVoice ASR 声学模型",
        "sense-voice-tokens.txt" to "SenseVoice 词表",
        "silero_vad.onnx" to "Silero VAD 语音活动检测",
        "punct.int8.onnx" to "CT-Transformer 标点恢复模型",
        "campplus.onnx" to "CAM++ 512维声纹提取模型",
        "pyannote_segmentation.onnx" to "Pyannote 话轮切分模型"
    )
    val destDir = File(context.filesDir, "Models")
    return models.map { (name, desc) ->
        val file = File(destDir, name)
        ModelDiagnostics(name, desc, file.exists() && file.length() > 0, file.length() / 1024)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSubView(context: Context, onBack: () -> Unit) {
    val filesList = remember { getModelDiagnostics(context) }
    val readyCount = filesList.count { it.exists }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线引擎自检") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (readyCount == 6) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (readyCount == 6) "系统就绪，一切正常" else "模型缺失",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("当前可用: $readyCount / 6")
                    }
                }
            }
            
            items(filesList) { model ->
                ListItem(
                    headlineContent = { Text(model.name) },
                    supportingContent = { Text(model.desc) },
                    trailingContent = {
                        if (model.exists) {
                            Text("${model.sizeKb} KB", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("未找到", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    leadingContent = {
                        Icon(
                            if (model.exists) Icons.Default.CheckCircle else Icons.Default.Clear,
                            contentDescription = null,
                            tint = if (model.exists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
