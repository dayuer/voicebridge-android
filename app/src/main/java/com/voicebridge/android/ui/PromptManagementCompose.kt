package com.voicebridge.android.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voicebridge.android.data.entity.AIPromptTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptManagementCompose(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    var prompts by remember { mutableStateOf(AIPromptTemplate.getPresets(context)) }
    var editingTemplate by remember { mutableStateOf<AIPromptTemplate?>(null) }

    fun refreshPrompts() {
        prompts = AIPromptTemplate.getPresets(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 提示词管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = "系统内置了 5 套专业的会议场景提示词模版。点击可自定义编辑。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            items(prompts) { template ->
                PromptListItem(
                    template = template,
                    onClick = { editingTemplate = template }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }

    editingTemplate?.let { template ->
        PromptEditorDialog(
            template = template,
            context = context,
            onDismiss = { editingTemplate = null },
            onSaved = {
                refreshPrompts()
                editingTemplate = null
            }
        )
    }
}

@Composable
fun PromptListItem(template: AIPromptTemplate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon: ImageVector = when (template.iconName) {
            "checklist" -> Icons.Default.CheckCircle
            "flag" -> Icons.Default.Place
            "lightbulb" -> Icons.Default.Info
            "mail" -> Icons.Default.Email
            else -> Icons.Default.List
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = template.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorDialog(
    template: AIPromptTemplate,
    context: Context,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var editedPrompt by remember { mutableStateOf(template.prompt) }
    
    // 检查当前 prompt 是否与默认的系统 prompt 一致
    val defaultTemplate = AIPromptTemplate.defaults.find { it.typeKey == template.typeKey }
    val isModified = defaultTemplate != null && editedPrompt != defaultTemplate.prompt

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp), // 全屏但留点顶部间隙
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("编辑：${template.label}") },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            AIPromptTemplate.saveCustomPrompt(context, template.typeKey, editedPrompt)
                            onSaved()
                        }) {
                            Text("保存", fontWeight = FontWeight.Bold)
                        }
                    }
                )

                if (isModified) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                AIPromptTemplate.resetToDefault(context, template.typeKey)
                                defaultTemplate?.let {
                                    editedPrompt = it.prompt
                                }
                                onSaved() // 恢复默认后立即保存退出
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("恢复系统默认")
                        }
                    }
                }

                OutlinedTextField(
                    value = editedPrompt,
                    onValueChange = { editedPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = { Text("系统级提示词 (System Prompt)") }
                )
                
                Text(
                    text = "提示词会配合发言原文一同输入给 AI，我们建议您保留关于 XML 结构的格式要求，以确保提取的结果兼容。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}
