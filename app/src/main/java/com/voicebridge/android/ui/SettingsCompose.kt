package com.voicebridge.android.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    // 状态定义
    var currentSubView by remember { mutableStateOf<String?>(if (initialSubView.isNullOrEmpty()) null else initialSubView) }
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
                            fontSize = 20.sp,
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. iOS 样式卡片列表：主要设置选项
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
                            PremiumSettingsRow(
                                title = "默认识别语言",
                                subtitle = "导入音频时的默认语种",
                                trailingText = "自动检测",
                                icon = Icons.Default.Info,
                                iconBgColor = Color(0xFF007AFF), // 经典 iOS 蓝色
                                onClick = {
                                    Toast.makeText(context, "已默认为自动语种识别", Toast.LENGTH_SHORT).show()
                                }
                            )

                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))

                            // 声纹管理入口
                            PremiumSettingsRow(
                                title = "声纹管理",
                                subtitle = "管理离线声纹特征与发言人",
                                trailingText = "${speakerList.size} 个发言人",
                                icon = Icons.Default.CheckCircle,
                                iconBgColor = Color(0xFF5856D6), // 经典 iOS 紫色
                                onClick = { currentSubView = "speaker" }
                            )

                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))

                            // 自定义词库入口
                            PremiumSettingsRow(
                                title = "自定义词库",
                                subtitle = "添加专有名词提升纪要准确度",
                                trailingText = "${glossaryList.size} 词条",
                                icon = Icons.Default.Create,
                                iconBgColor = Color(0xFFFF9500), // 经典 iOS 橙色
                                onClick = { currentSubView = "glossary" }
                            )

                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))

                            // 离线引擎自检
                            PremiumSettingsRow(
                                title = "离线引擎自检与诊断",
                                subtitle = "检查 6 个核心离线模型就绪情况",
                                trailingText = "检查",
                                icon = Icons.Default.Settings,
                                iconBgColor = Color(0xFF34C759), // 经典 iOS 绿色
                                onClick = { currentSubView = "diagnostics" }
                            )

                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))

                            // 服务与隐私授权
                            var showRevokeConfirm by remember { mutableStateOf(false) }

                            PremiumSettingsRow(
                                title = "服务与隐私授权",
                                subtitle = "已授权服务与隐私协议·可撤销",
                                trailingText = "管理",
                                icon = Icons.Default.Info,
                                iconBgColor = Color(0xFFFF9500), // 经典 iOS 橙色
                                onClick = { showRevokeConfirm = true }
                            )

                            if (showRevokeConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showRevokeConfirm = false },
                                    title = { Text("撤销服务与隐私授权？", fontWeight = FontWeight.Bold, color = VoiceBridgeTheme.textPrimary) },
                                    text = { Text("撤销服务与隐私授权后，AI 智能纪要功能将锁定不可用，且下次启动 App 时将重新征求同意。") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                val prefs = context.getSharedPreferences("voicebridge_settings", Context.MODE_PRIVATE)
                                                prefs.edit().putBoolean("has_agreed_privacy", false).apply()
                                                showRevokeConfirm = false
                                                onRevokePrivacy()
                                            }
                                        ) {
                                            Text("确认撤销", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showRevokeConfirm = false }) {
                                            Text("取消", color = VoiceBridgeTheme.textSecondary)
                                        }
                                    }
                                )
                            }
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
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            InfoRowItem(label = "软件版本", value = "1.0.0.2 (Build 10)")
                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))
                            InfoRowItem(label = "识别引擎", value = "SenseVoice + Silero VAD")
                            HorizontalDivider(color = VoiceBridgeTheme.separator.copy(alpha = 0.5f))
                            InfoRowItem(label = "隐私合规", value = "100% 本地运行·无数据上传")
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
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFFF3B30), shape = RoundedCornerShape(8.dp)), // 经典 iOS 红色
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "☠️", fontSize = 14.sp)
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "清空所有历史声纹库",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF3B30)
                                )
                                Text(
                                    text = "擦除所有已存声纹特征与归属，用于开发调试",
                                    fontSize = 11.sp,
                                    color = VoiceBridgeTheme.textTertiary
                                )
                            }
                        }
                    }

                    if (showClearConfirm) {
                        AlertDialog(
                            onDismissRequest = { showClearConfirm = false },
                            title = { Text("确定清空所有声纹数据？", fontWeight = FontWeight.Bold, color = VoiceBridgeTheme.textPrimary) },
                            text = { Text("此操作将永久注销所有已存声纹身份卡，并重置所有既往会议纪要的段落角色。") },
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
                                        Toast.makeText(context, "🧹 声纹指纹及段落已重置", Toast.LENGTH_SHORT).show()
                                        showClearConfirm = false
                                    }
                                ) {
                                    Text("一键清空", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
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

// 重新设计的 iOS 风格卡片行样式
@Composable
fun PremiumSettingsRow(
    title: String,
    subtitle: String,
    trailingText: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆角高对比彩色图标框
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(iconBgColor, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

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
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "进入",
                tint = VoiceBridgeTheme.textDisabled,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun InfoRowItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
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
                        text = "+ 添加",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceBridgeTheme.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                if (list.size >= GlossaryEntryEntity.MAX_ENTRY_COUNT) {
                                    Toast.makeText(context, "最多只能添加 ${GlossaryEntryEntity.MAX_ENTRY_COUNT} 条词条", Toast.LENGTH_SHORT).show()
                                } else {
                                    newTermInput = ""
                                    showAddDialog = true
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = VoiceBridgeTheme.bgSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "💡 可用于添加特定的人名、公司品牌或专业学术名词。在流式转译和 AI 归纳中，识别引擎将优先匹配本词库中的词条纠错，提高转写准确度。",
                    fontSize = 11.sp,
                    color = VoiceBridgeTheme.textSecondary,
                    lineHeight = 16.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "词条列表", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VoiceBridgeTheme.textPrimary)
                Text(text = "${list.size} / ${GlossaryEntryEntity.MAX_ENTRY_COUNT}", fontSize = 12.sp, color = VoiceBridgeTheme.textTertiary)
            }

            if (list.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = "词库内没有任何词条", color = VoiceBridgeTheme.textDisabled, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { item ->
                        Surface(
                            color = VoiceBridgeTheme.bgSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(text = item.term, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VoiceBridgeTheme.textPrimary)
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "删除",
                                    tint = VoiceBridgeTheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            scope.launch(Dispatchers.IO) {
                                                db.glossaryEntryDao().delete(item)
                                            }
                                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
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
            title = { Text("新增词库词条", fontWeight = FontWeight.Bold, color = VoiceBridgeTheme.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newTermInput,
                    onValueChange = { newTermInput = it },
                    singleLine = true,
                    placeholder = { Text("请输入专业词汇，如「语音电桥」") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = VoiceBridgeTheme.accent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
                        text = "声纹指纹库",
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = VoiceBridgeTheme.bgSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "⚙️ 声纹特征是在本地进行 Diarization(话轮识别) 时提取的 512 维向量特征，用于在跨会议中识别同一发言人。在此注销声纹后，段落名称将重置为「未知发言人」。",
                    fontSize = 11.sp,
                    color = VoiceBridgeTheme.textSecondary,
                    lineHeight = 16.sp
                )
            }

            Text(text = "已登记发言人 (${list.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VoiceBridgeTheme.textPrimary)

            if (list.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = "尚未登记任何本地离线声纹角色", color = VoiceBridgeTheme.textDisabled, fontSize = 13.sp)
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
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 动态计算发言人的 Okabe-Ito 调色板映射头像颜色
                                    val colorIndex = abs(item.name.hashCode()) % VoiceBridgeTheme.speakerPalette.size
                                    val avatarBgColor = VoiceBridgeTheme.speakerPalette[colorIndex]

                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(avatarBgColor, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(text = item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VoiceBridgeTheme.textPrimary)
                                }

                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "注销",
                                    tint = VoiceBridgeTheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            scope.launch(Dispatchers.IO) {
                                                db.speakerProfileDao().delete(item)
                                            }
                                            Toast.makeText(context, "声纹角色已注销", Toast.LENGTH_SHORT).show()
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
@Composable
fun DiagnosticsSubView(
    context: Context,
    onBack: () -> Unit
) {
    val filesList = remember { getModelDiagnostics(context) }
    val readyCount = filesList.count { it.exists }

    // 脉冲呼吸动画，用于就绪图标的流动感
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

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
                        text = "离线引擎自检",
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 就绪度卡片：带脉冲呼吸灯效果
            Surface(
                color = if (readyCount == 6) VoiceBridgeTheme.success.copy(alpha = 0.1f) else VoiceBridgeTheme.warning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 0.5.dp,
                        color = if (readyCount == 6) VoiceBridgeTheme.success.copy(alpha = 0.3f) else VoiceBridgeTheme.warning.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 呼吸脉冲指示灯
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = (if (readyCount == 6) VoiceBridgeTheme.success else VoiceBridgeTheme.warning).copy(alpha = pulseAlpha),
                                shape = CircleShape
                            )
                    )

                    Text(
                        text = if (readyCount == 6) "离线引擎已完全就绪 (6/6 模型)" else "离线引擎文件缺失 ($readyCount/6 已部署)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (readyCount == 6) VoiceBridgeTheme.success else VoiceBridgeTheme.warning
                    )
                }
            }

            Text(text = "离线模型列表", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VoiceBridgeTheme.textPrimary)

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
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VoiceBridgeTheme.textPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (item.exists) VoiceBridgeTheme.success.copy(alpha = 0.12f) else VoiceBridgeTheme.error.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = if (item.exists) "已部署" else "缺失",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.exists) VoiceBridgeTheme.success else VoiceBridgeTheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(text = item.desc, fontSize = 11.sp, color = VoiceBridgeTheme.textSecondary)

                            if (item.exists) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "文件大小: ${item.size}",
                                        fontSize = 10.sp,
                                        color = VoiceBridgeTheme.textTertiary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Text(
                                text = "沙盒路径: ${item.path}",
                                fontSize = 8.sp,
                                color = VoiceBridgeTheme.textDisabled,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

data class FileDiagnostic(
    val name: String,
    val desc: String,
    val exists: Boolean,
    val size: String,
    val path: String
)

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
