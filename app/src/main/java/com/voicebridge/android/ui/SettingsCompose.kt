package com.voicebridge.android.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.GlossaryEntryEntity
import com.voicebridge.android.data.entity.SpeakerProfileEntity
import com.voicebridge.android.ui.theme.VoiceBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCompose(
    db: VoiceBridgeDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状态定义
    var currentSubView by remember { mutableStateOf<String?>(null) } // "glossary" or "speaker" or "diagnostics"
    var glossaryList by remember { mutableStateOf<List<GlossaryEntryEntity>>(emptyList()) }
    var speakerList by remember { mutableStateOf<List<SpeakerProfileEntity>>(emptyList()) }

    // 从 Room 实时读取数据
    LaunchedEffect(Unit) {
        db.glossaryEntryDao().getAllEntries().collect { list ->
            glossaryList = list
        }
    }
    LaunchedEffect(Unit) {
        db.speakerProfileDao().getAllProfiles().collect { list ->
            speakerList = list
        }
    }

    if (currentSubView == "glossary") {
        GlossarySubView(db = db, list = glossaryList, onBack = { currentSubView = null })
    } else if (currentSubView == "speaker") {
        SpeakerSubView(db = db, list = speakerList, onBack = { currentSubView = null })
    } else if (currentSubView == "diagnostics") {
        DiagnosticsSubView(context = context, onBack = { currentSubView = null })
    } else {
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = VoiceBridgeTheme.textPrimary)
                        }

                        Text(
                            text = "设置",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoiceBridgeTheme.textPrimary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(48.dp)) // 对齐占位
                    }
                }
            },
            containerColor = VoiceBridgeTheme.bgCanvas
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 主要设置选项合并卡片
                item {
                    Surface(
                        color = VoiceBridgeTheme.bgSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            // 默认语种菜单
                            SettingsRow(
                                title = "默认识别语言",
                                subtitle = "导入音频时的默认语种",
                                trailingText = "自动检测",
                                onClick = {
                                    Toast.makeText(context, "自动语言检测已生效", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))

                            // 声纹管理入口
                            SettingsRow(
                                title = "声纹管理",
                                subtitle = "管理离线声纹特征与发言人",
                                trailingText = "${speakerList.size} 已保存",
                                onClick = { currentSubView = "speaker" }
                            )

                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))

                            // 自定义词库入口
                            SettingsRow(
                                title = "自定义词库",
                                subtitle = "添加专有名词提升纪要准确度",
                                trailingText = "${glossaryList.size} 词条",
                                onClick = { currentSubView = "glossary" }
                            )

                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))

                            // 离线引擎自检
                            SettingsRow(
                                title = "离线引擎自检与诊断",
                                subtitle = "检查 6 个核心离线模型就绪情况",
                                trailingText = "开始自检",
                                onClick = { currentSubView = "diagnostics" }
                            )
                        }
                    }
                }

                // 2. 关于与产品信息卡片
                item {
                    Surface(
                        color = VoiceBridgeTheme.bgSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InfoRow(label = "软件版本", value = "1.0.0.2 Build 10")
                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))
                            InfoRow(label = "识别引擎", value = "SenseVoice + Silero VAD")
                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))
                            InfoRow(label = "隐私合规", value = "所有处理均在设备本地完成")
                        }
                    }
                }

                // 3. 调试与测试选项：清空历史声纹库
                item {
                    var showClearConfirm by remember { mutableStateOf(false) }

                    Surface(
                        color = VoiceBridgeTheme.bgSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(16.dp))
                            .clickable { showClearConfirm = true }
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(VoiceBridgeTheme.error.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "⚠️", fontSize = 14.sp)
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "清空所有历史声纹库",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoiceBridgeTheme.error
                                )
                                Text(
                                    text = "擦除所有已存声纹特征与归属，用于开发测试",
                                    fontSize = 11.sp,
                                    color = VoiceBridgeTheme.textTertiary
                                )
                            }
                        }
                    }

                    if (showClearConfirm) {
                        AlertDialog(
                            onDismissRequest = { showClearConfirm = false },
                            title = { Text("确定清空所有声纹数据？", fontWeight = FontWeight.Bold) },
                            text = { Text("将删除所有已登记的发言人，并擦除旧音频的声纹特征。重新提取时将采用最新的声纹提取器。") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            // 删除声纹配置
                                            db.speakerProfileDao().getAllProfiles().take(1).collect { list ->
                                                list.forEach { db.speakerProfileDao().delete(it) }
                                            }
                                            // 重置已存在段落声纹标签
                                            // 直接修改全部 segments
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
                                                    // 删除 MeetingRecordComplete 中的 voiceSamples
                                                    complete.voiceSamples.forEach {
                                                        db.voiceSampleDao().delete(it)
                                                    }
                                                }
                                            }
                                        }
                                        Toast.makeText(context, "🧹 声纹库已清空并重置", Toast.LENGTH_SHORT).show()
                                        showClearConfirm = false
                                    }
                                ) {
                                    Text("一键清空", color = VoiceBridgeTheme.error, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearConfirm = false }) {
                                    Text("取消", color = VoiceBridgeTheme.textSecondary)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// 设置每一行样式
@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    trailingText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VoiceBridgeTheme.textPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = VoiceBridgeTheme.textTertiary)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = trailingText, fontSize = 12.sp, color = VoiceBridgeTheme.textSecondary)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "进入", tint = VoiceBridgeTheme.textDisabled, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = VoiceBridgeTheme.textSecondary)
        Text(text = value, fontSize = 13.sp, color = VoiceBridgeTheme.textTertiary, fontWeight = FontWeight.Medium)
    }
}

// ==========================================
// 1. 自定义词库子视图 (GlossarySubView)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossarySubView(
    db: VoiceBridgeDatabase,
    list: List<GlossaryEntryEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var newTermInput by remember { mutableStateOf("") }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = VoiceBridgeTheme.textPrimary)
                    }

                    Text(
                        text = "自定义词库",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.textPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "新增",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.accent,
                        modifier = Modifier.clickable {
                            if (list.size >= GlossaryEntryEntity.MAX_ENTRY_COUNT) {
                                Toast.makeText(context, "最多只能添加 ${GlossaryEntryEntity.MAX_ENTRY_COUNT} 条词条", Toast.LENGTH_SHORT).show()
                            } else {
                                newTermInput = ""
                                showAddDialog = true
                            }
                        }
                    )
                }
            }
        },
        containerColor = VoiceBridgeTheme.bgCanvas
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "可用于添加特定的人名、公司品牌、专业术语等，有助于语音识别引擎在进行流式转录或 AI 总结时，自动锁定并纠正发音相近的常见错字。",
                fontSize = 12.sp,
                color = VoiceBridgeTheme.textTertiary,
                lineHeight = 18.sp
            )

            if (list.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = "词库内没有任何词条", color = VoiceBridgeTheme.textDisabled, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { item ->
                        Surface(
                            color = VoiceBridgeTheme.bgSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = item.term, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VoiceBridgeTheme.textPrimary)
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "删除",
                                    tint = VoiceBridgeTheme.error,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            scope.launch(Dispatchers.IO) {
                                                db.glossaryEntryDao().delete(item)
                                            }
                                            Toast.makeText(context, "已删除词条", Toast.LENGTH_SHORT).show()
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增词库词条", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTermInput,
                    onValueChange = { newTermInput = it },
                    singleLine = true,
                    placeholder = { Text("请输入专业词汇，如「畅译」") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = newTermInput.trim()
                        if (input.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                val entry = GlossaryEntryEntity(
                                    id = UUID.randomUUID().toString(),
                                    term = input,
                                    createdAt = Date()
                                )
                                db.glossaryEntryDao().insertRaw(entry)
                            }
                            Toast.makeText(context, "词条添加成功", Toast.LENGTH_SHORT).show()
                        }
                        showAddDialog = false
                    }
                ) {
                    Text("保存", color = VoiceBridgeTheme.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消", color = VoiceBridgeTheme.textSecondary)
                }
            }
        )
    }
}

// ==========================================
// 2. 声纹管理子视图 (SpeakerSubView)
// ==========================================
@Composable
fun SpeakerSubView(
    db: VoiceBridgeDatabase,
    list: List<SpeakerProfileEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = VoiceBridgeTheme.textPrimary)
                    }

                    Text(
                        text = "声纹库管理",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.textPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        },
        containerColor = VoiceBridgeTheme.bgCanvas
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "应用在本地离线自动匹配发言人声纹。每个发言人绑定一个512维数学指纹特征。在此处删除发言人将导致对应纪要的段落归属重置。",
                fontSize = 12.sp,
                color = VoiceBridgeTheme.textTertiary,
                lineHeight = 18.sp
            )

            if (list.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = "尚未登记任何离线声纹角色", color = VoiceBridgeTheme.textDisabled, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.id }) { item ->
                        Surface(
                            color = VoiceBridgeTheme.bgSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(VoiceBridgeTheme.accent, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = item.name.take(1), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(text = item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VoiceBridgeTheme.textPrimary)
                                }

                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "删除",
                                    tint = VoiceBridgeTheme.error,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            scope.launch(Dispatchers.IO) {
                                                db.speakerProfileDao().delete(item)
                                            }
                                            Toast.makeText(context, "已注销声纹角色", Toast.LENGTH_SHORT).show()
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

// ==========================================
// 3. 离线引擎自检视图 (DiagnosticsSubView)
// ==========================================
data class FileDiagnostic(
    val name: String,
    val desc: String,
    val exists: Boolean,
    val size: String,
    val path: String
)

@Composable
fun DiagnosticsSubView(
    context: Context,
    onBack: () -> Unit
) {
    val filesList = remember { getModelDiagnostics(context) }
    val readyCount = filesList.count { it.exists }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = VoiceBridgeTheme.textPrimary)
                    }

                    Text(
                        text = "离线引擎自检与诊断",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.textPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        },
        containerColor = VoiceBridgeTheme.bgCanvas
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 就绪度总结
            Surface(
                color = if (readyCount == 6) VoiceBridgeTheme.success.copy(alpha = 0.1f) else VoiceBridgeTheme.warning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (readyCount == 6) "✓" else "⚠",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (readyCount == 6) VoiceBridgeTheme.success else VoiceBridgeTheme.warning
                    )
                    Text(
                        text = if (readyCount == 6) "离线引擎完整就绪 (6/6 模型就位)" else "离线引擎不完整 ($readyCount/6 缺失文件)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (readyCount == 6) VoiceBridgeTheme.success else VoiceBridgeTheme.warning
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filesList) { item ->
                    Surface(
                        color = VoiceBridgeTheme.bgSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = item.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VoiceBridgeTheme.textPrimary)
                                Surface(
                                    shape = CircleShape,
                                    color = if (item.exists) VoiceBridgeTheme.success.copy(alpha = 0.15f) else VoiceBridgeTheme.error.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (item.exists) "已就绪" else "缺失",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.exists) VoiceBridgeTheme.success else VoiceBridgeTheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(text = item.desc, fontSize = 11.sp, color = VoiceBridgeTheme.textSecondary)
                            if (item.exists) {
                                Text(text = "大小: ${item.size}", fontSize = 10.sp, color = VoiceBridgeTheme.textTertiary)
                            }
                            Text(
                                text = "物理路径: ${item.path}",
                                fontSize = 8.sp,
                                color = VoiceBridgeTheme.textDisabled,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// 帮助函数，提取模型存在性诊断数据
fun getModelDiagnostics(context: Context): List<FileDiagnostic> {
    val modelsDir = File(context.filesDir, "Models")
    val files = listOf(
        Pair("model.int8.onnx", "SenseVoice 识别模型"),
        Pair("sense-voice-tokens.txt", "SenseVoice 分词表"),
        Pair("silero_vad.onnx", "Silero 语音活动检测(VAD)"),
        Pair("punct.int8.onnx", "CT-Transformer 标点恢复"),
        Pair("3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx", "CAM++ 声纹特征提取"),
        Pair("sherpa-onnx-pyannote-segmentation-3-0/model.onnx", "Pyannote 话轮分割模型")
    )

    return files.map { (relPath, desc) ->
        val file = File(modelsDir, relPath)
        val exists = file.exists() && file.length() > 0
        val sizeStr = if (exists) {
            val len = file.length().toDouble()
            when {
                len > 1024 * 1024 -> String.format("%.1f MB", len / (1024 * 1024))
                len > 1024 -> String.format("%.1f KB", len / 1024)
                else -> "$len B"
            }
        } else {
            "0 B"
        }
        FileDiagnostic(
            name = file.name,
            desc = desc,
            exists = exists,
            size = sizeStr,
            path = file.absolutePath
        )
    }
}
