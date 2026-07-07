package com.voicebridge.android.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.AIPromptTemplate
import com.voicebridge.android.data.entity.GlossaryEntryEntity
import com.voicebridge.android.data.entity.TranscriptSegmentEntity
import com.voicebridge.android.service.AIApp
import com.voicebridge.android.service.AIAppLauncher
import com.voicebridge.android.ui.theme.VoiceBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAppLaunchSheet(
    segments: List<TranscriptSegmentEntity>,
    db: VoiceBridgeDatabase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val allApps = AIApp.values().toList()
    val installedApps = remember { AIAppLauncher.installedApps(context) }
    
    val prompts = remember { AIPromptTemplate.getPresets(context) }
    var selectedPrompt by remember { mutableStateOf(prompts.first()) }
    
    var glossaryList by remember { mutableStateOf<List<GlossaryEntryEntity>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        db.glossaryEntryDao().getAllEntries().collect { list ->
            glossaryList = list
        }
    }

    fun composeAIPromptText(
        context: Context,
        template: AIPromptTemplate,
        segments: List<TranscriptSegmentEntity>,
        glossary: List<GlossaryEntryEntity>
    ): String {
        val verificationKey = AIAppLauncher.generateVerificationKey(context)
        
        val mergedSegments = mutableListOf<Pair<String, String>>()
        var currentSpeaker = ""
        var currentText = StringBuilder()
        
        for (seg in segments) {
            val speaker = seg.speakerLabel.ifEmpty { "未知" }
            if (speaker == currentSpeaker) {
                currentText.append(" ").append(seg.text)
            } else {
                if (currentSpeaker.isNotEmpty()) {
                    mergedSegments.add(Pair(currentSpeaker, currentText.toString()))
                }
                currentSpeaker = speaker
                currentText = StringBuilder(seg.text)
            }
        }
        if (currentSpeaker.isNotEmpty()) {
            mergedSegments.add(Pair(currentSpeaker, currentText.toString()))
        }
        
        val transcriptBody = mergedSegments.joinToString("\n\n") { "【${it.first}】：${it.second}" }
        
        val glossaryText = if (glossary.isNotEmpty()) {
            "【专有名词/术语表 (供参考)】\n" + glossary.joinToString("\n") { "- ${it.term}" } + "\n\n"
        } else ""
        
        return """
            ${template.prompt}
            
            为了进行调用链验证，请在输出的所有内容的最后，务必单独换行附上以下验证码（不要修改验证码的任何字符）：
            <verification_key>$verificationKey</verification_key>
            
            $glossaryText
            【转录原文开始】
            $transcriptBody
            【转录原文结束】
        """.trimIndent()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp), // extra padding for bottom nav bar area
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "使用第三方 AI 提炼",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 提示词选择区
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "选择要提取的内容",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(prompts) { template ->
                        val isSelected = selectedPrompt.typeKey == template.typeKey
                        val icon: ImageVector = when (template.iconName) {
                            "checklist" -> Icons.Default.CheckCircle
                            "flag" -> Icons.Default.Flag
                            "lightbulb" -> Icons.Default.Lightbulb
                            "mail" -> Icons.Default.Mail
                            else -> Icons.Default.Article
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPrompt = template }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = template.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // AI App 启动区
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "检测到本机已安装的 AI 助手",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (installedApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未检测到支持一键拉起的 AI App，请使用底部按钮复制纯文本后手动打开您的 AI 工具。",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(allApps) { app ->
                            val isInstalled = installedApps.contains(app)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable(enabled = isInstalled) {
                                        scope.launch(Dispatchers.IO) {
                                            val promptText = composeAIPromptText(context, selectedPrompt, segments, glossaryList)
                                            withContext(Dispatchers.Main) {
                                                AIAppLauncher.launch(context, app, promptText)
                                                Toast.makeText(context, "已复制 Prompt，正在拉起 ${app.displayName}", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            if (isInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            if (isInstalled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.displayName.take(1),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                Text(
                                    text = app.displayName,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    color = if (isInstalled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
            
            // 兜底操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val promptText = composeAIPromptText(context, selectedPrompt, segments, glossaryList)
                            withContext(Dispatchers.Main) {
                                AIAppLauncher.writeToClipboard(context, promptText)
                                Toast.makeText(context, "✅ 提示词及长文本已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("仅复制内容")
                }
            }
        }
    }
}
