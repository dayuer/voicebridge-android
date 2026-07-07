package com.voicebridge.android.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.GlossaryEntryEntity
import com.voicebridge.android.ui.theme.VoiceBridgeTheme
import com.voicebridge.android.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

/**
 * 自定义词库管理页 — 对齐 iOS GlossaryView.swift
 * 功能：内联添加、查重、上限提示（n/200）、搜索过滤（>10 条）、批量导入、文本导出
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryCompose(
    db: VoiceBridgeDatabase,
    list: List<GlossaryEntryEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newTerm by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var showBatchImport by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val isAtLimit = list.size >= GlossaryEntryEntity.MAX_ENTRY_COUNT
    val filtered = remember(list, searchText) {
        val q = searchText.trim()
        if (q.isEmpty()) list else list.filter { it.term.contains(q, ignoreCase = true) }
    }

    fun addTerm() {
        val trimmed = newTerm.trim()
        if (trimmed.isEmpty()) return
        if (isAtLimit) {
            Toast.makeText(context, "已达上限 ${GlossaryEntryEntity.MAX_ENTRY_COUNT} 条", Toast.LENGTH_SHORT).show()
            return
        }
        if (list.any { it.term == trimmed }) {
            Toast.makeText(context, "「$trimmed」已存在", Toast.LENGTH_SHORT).show()
            newTerm = ""
            return
        }
        scope.launch(Dispatchers.IO) {
            db.glossaryEntryDao().insertRaw(
                GlossaryEntryEntity(id = UUID.randomUUID().toString(), term = trimmed, createdAt = Date())
            )
        }
        newTerm = ""
        Toast.makeText(context, "已添加「$trimmed」", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义词库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 词条计数胶囊
                    Text(
                        text = "${list.size}/${GlossaryEntryEntity.MAX_ENTRY_COUNT}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isAtLimit) VoiceBridgeTheme.warning else MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("批量导入") },
                            onClick = { showMenu = false; showBatchImport = true }
                        )
                        if (list.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("导出自定义词库") },
                                onClick = {
                                    showMenu = false
                                    val exportText = list.joinToString("\n") { it.term }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, exportText)
                                        putExtra(Intent.EXTRA_SUBJECT, "导出的词条")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "导出自定义词库"))
                                }
                            )
                        }
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
                    text = "添加人名、专有名词和缩写等术语。生成 AI 纪要时会自动附带词库，帮助 AI 修正转录中的错别字与谐音错误。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 内联添加词条
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newTerm,
                        onValueChange = { newTerm = it },
                        singleLine = true,
                        placeholder = { Text("输入术语（如 张三、Kubernetes）", fontSize = 14.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { addTerm() },
                        enabled = newTerm.trim().isNotEmpty() && !isAtLimit
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加",
                            tint = if (newTerm.trim().isNotEmpty() && !isAtLimit) VoiceBridgeTheme.accent
                            else VoiceBridgeTheme.textDisabled
                        )
                    }
                }
            }

            // 搜索栏（词条 > 10 条时显示）
            if (list.size > 10) {
                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        placeholder = { Text("搜索自定义词库", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (searchText.isEmpty()) "自定义词库为空" else "无匹配结果",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (searchText.isEmpty()) {
                                Text(
                                    "添加人名和术语后，AI 纪要会更准确",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { item ->
                    val index = filtered.indexOf(item)
                    ListItem(
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(VoiceBridgeTheme.accent.copy(alpha = 0.08f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        headlineContent = { Text(item.term, fontSize = 14.sp) },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) { db.glossaryEntryDao().delete(item) }
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    // 批量导入弹窗
    if (showBatchImport) {
        var batchText by remember { mutableStateOf("") }
        val terms = remember(batchText) { parseBatchTerms(batchText) }
        val available = GlossaryEntryEntity.MAX_ENTRY_COUNT - list.size
        val willImport = minOf(terms.size, available)

        AlertDialog(
            onDismissRequest = { showBatchImport = false },
            title = { Text("批量导入") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("每行一个术语，或用逗号、顿号分隔", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = batchText,
                        onValueChange = { batchText = it },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (terms.isNotEmpty()) {
                        Text(
                            text = "将导入 $willImport 个术语" +
                                if (terms.size > available) "（超出上限 ${terms.size - available} 个将忽略）" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (terms.size > available) VoiceBridgeTheme.warning
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = terms.isNotEmpty(),
                    onClick = {
                        val existing = list.map { it.term }.toSet()
                        var imported = 0
                        scope.launch(Dispatchers.IO) {
                            for (term in terms) {
                                if (imported >= available) break
                                if (term in existing) continue
                                db.glossaryEntryDao().insertRaw(
                                    GlossaryEntryEntity(id = UUID.randomUUID().toString(), term = term, createdAt = Date())
                                )
                                imported++
                            }
                            AppLog.fileImport("词库批量导入 $imported 个术语")
                        }
                        showBatchImport = false
                        Toast.makeText(context, "已导入词条", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchImport = false }) { Text("取消") }
            }
        )
    }
}

/** 解析批量导入文本：按换行、逗号、顿号、分号分割，去重去空（保留顺序） */
private fun parseBatchTerms(text: String): List<String> {
    val raw = text.split('\n', '\r', ',', '，', '、', ';', '；')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val seen = LinkedHashSet<String>()
    raw.forEach { seen.add(it) }
    return seen.toList()
}
