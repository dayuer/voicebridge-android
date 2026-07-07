package com.voicebridge.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicebridge.android.ui.theme.VoiceBridgeTheme

/**
 * AI 数据分享同意底部弹窗 — 对齐 iOS AIDataConsentView.swift
 *
 * 首次使用 AI 协作功能时弹出，明确披露：
 *   1. 发送什么数据（会议标题、时长、转录文本、说话人标签、词库术语）
 *   2. 发送给谁（用户选择的第三方 AI App）
 *   3. 用户如何控制（可在设置中随时撤销）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIDataConsentSheet(
    onAgree: () -> Unit,
    onDecline: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDecline,
        sheetState = sheetState,
        containerColor = VoiceBridgeTheme.bgSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部图标
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(VoiceBridgeTheme.accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = VoiceBridgeTheme.accent,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "AI 助手数据分享说明",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VoiceBridgeTheme.textPrimary
            )

            Text(
                text = "使用「AI 智能纪要」功能前，请了解以下信息：",
                fontSize = 14.sp,
                color = VoiceBridgeTheme.textSecondary,
                textAlign = TextAlign.Center
            )

            // 三项披露卡片
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DisclosureCard(
                    title = "发送的数据",
                    description = "会议标题、时长、转录文本、说话人标签，以及您自定义的术语词库内容"
                )
                DisclosureCard(
                    title = "数据接收方",
                    description = "您主动选择的第三方 AI 应用（如 ChatGPT、Claude、Gemini、DeepSeek、豆包、Kimi、通义千问、智谱清言），数据通过系统剪贴板传递"
                )
                DisclosureCard(
                    title = "您的控制权",
                    description = "畅译不直接向任何服务器发送网络请求。您可随时在「设置」中撤销授权，撤销后 AI 协作功能将不可用"
                )
            }

            // 第三方隐私提示
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = VoiceBridgeTheme.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "数据传递至第三方 AI 应用后，其处理方式受该应用各自的隐私政策约束。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = VoiceBridgeTheme.textTertiary
                )
            }

            // 操作按钮
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(containerColor = VoiceBridgeTheme.accent),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("我已了解，同意使用", fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            TextButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("暂不使用", color = VoiceBridgeTheme.textSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DisclosureCard(title: String, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(VoiceBridgeTheme.bgCanvas, RoundedCornerShape(12.dp))
            .border(0.5.dp, VoiceBridgeTheme.separator, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(VoiceBridgeTheme.accent.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = VoiceBridgeTheme.accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VoiceBridgeTheme.textPrimary
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = VoiceBridgeTheme.textSecondary
            )
        }
    }
}
