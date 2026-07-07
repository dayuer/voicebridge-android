package com.voicebridge.android.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.List
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
import com.voicebridge.android.ui.theme.VoiceBridgeTheme

@Composable
fun PrivacyConsentCompose(
    onAgree: () -> Unit
) {
    val context = LocalContext.current
    var hasCheckedConsent by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val checkedColor by animateColorAsState(
        targetValue = if (hasCheckedConsent) VoiceBridgeTheme.accent else VoiceBridgeTheme.textDisabled,
        label = "checkedColor"
    )

    Scaffold(
        containerColor = VoiceBridgeTheme.bgCanvas
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部安全盾牌图标与主标题
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(VoiceBridgeTheme.accent.copy(alpha = 0.12f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = VoiceBridgeTheme.accent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "畅译服务与数据隐私政策",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoiceBridgeTheme.textPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "在您开始体验畅译前，请仔细阅读以下数据处理说明：",
                    fontSize = 11.sp,
                    color = VoiceBridgeTheme.textTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }

            // 滚动条款内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 板块 1: 本地离线识别
                PrivacyCard(
                    icon = Icons.Default.List,
                    title = "1. 离线语音识别政策",
                    description = "畅译的核心语音转文字完全在您的设备本地完成。我们的内置 SenseVoice AI 引擎不收集、不传输、不存储您的任何个人信息。您的录音音频与转录文本 100% 留存在您的设备本地，绝不上传至任何服务器。"
                )

                // 板块 2: AI 协作共享
                PrivacyCard(
                    icon = Icons.Default.Create,
                    title = "2. AI 智能纪要共享政策",
                    description = "当您主动使用「AI 智能纪要」整理会议时：\n• 发送的数据：会议标题、时长、转录文本、说话人标签，及您定义的术语词条。\n• 数据接收方：您主动选择的第三方 AI 应用（包括 ChatGPT、Claude、Gemini、DeepSeek、豆包、Kimi、通义千问、智谱清言）。\n• 传递方式：通过系统剪贴板传递，由您手动粘贴至目标 App. 畅译不向任何第三方 AI 服务器直接发送网络请求。"
                )

                // 板块 3: 用户控制与随时撤销
                PrivacyCard(
                    icon = Icons.Default.Info,
                    title = "3. 授权确认与随时撤销",
                    description = "授权同意后您方可进入 App。您随时可以在 App 内的「设置」中撤销此隐私协议授权，撤销后 AI 协作功能将锁定且不可使用。"
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 底部操作与选项确认（吸底固定区域）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoiceBridgeTheme.bgSurface)
                    .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Checkbox 确认行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hasCheckedConsent = !hasCheckedConsent },
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.5.dp, checkedColor, shape = RoundedCornerShape(4.dp))
                            .background(
                                color = if (hasCheckedConsent) VoiceBridgeTheme.accent else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCheckedConsent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = "我已明确知晓并同意以上隐私政策与 AI 数据分享条款。",
                        fontSize = 12.sp,
                        color = VoiceBridgeTheme.textSecondary,
                        lineHeight = 18.sp
                    )
                }

                // 按钮区
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            if (hasCheckedConsent) {
                                val prefs = context.getSharedPreferences("voicebridge_settings", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("has_agreed_privacy", true).apply()
                                onAgree()
                            }
                        },
                        enabled = hasCheckedConsent,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoiceBridgeTheme.accent,
                            disabledContainerColor = VoiceBridgeTheme.separator
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "我已了解，同意并开启",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCheckedConsent) Color.White else VoiceBridgeTheme.textDisabled
                        )
                    }

                    TextButton(
                        onClick = {
                            (context as? Activity)?.finishAffinity()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(
                            text = "暂不同意",
                            fontSize = 13.sp,
                            color = VoiceBridgeTheme.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoiceBridgeTheme.bgSurface, shape = RoundedCornerShape(12.dp))
            .border(0.5.dp, VoiceBridgeTheme.separator, shape = RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(VoiceBridgeTheme.accent.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VoiceBridgeTheme.accent,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = VoiceBridgeTheme.textPrimary
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = VoiceBridgeTheme.textSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
