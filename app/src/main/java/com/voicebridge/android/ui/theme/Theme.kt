package com.voicebridge.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

object VoiceBridgeTheme {
    // 页面Canvas背景
    val bgCanvas: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF0F0F17) else Color(0xFFF6F7F9)

    // 卡片 / 列表行背景
    val bgSurface: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF191922) else Color(0xFFFFFFFF)

    // 浮层 / 底部面板背景
    val bgElevated: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF22222E) else Color(0xFFFFFFFF)

    // 描边 / 分隔线
    val separator: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0x1AFFFFFF) else Color(0x14000000)

    // 文本颜色角色
    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFFF2F3F7) else Color(0xFF1A1A22)

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFFB9BBC7) else Color(0xFF4A4A56)

    val textTertiary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF8B8D9A) else Color(0xFF6C6C77)

    val textDisabled: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF5A5C68) else Color(0xFFA0A0AA)

    val textOnAccent = Color.White

    // 品牌强调色 (单强调色，靛蓝)
    val accent: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF7A7AF0) else Color(0xFF5B5BD6)

    val accentGradientEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF6E6AE8) else Color(0xFF4C46C8)

    // 语义状态色
    val success: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFF10B981) else Color(0xFF047857)

    val error: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFFEF4444) else Color(0xFFDC2626)

    val warning: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) Color(0xFFF59E0B) else Color(0xFFB45309)

    // Okabe–Ito 8 色色盲安全调色板
    val speakerPalette = listOf(
        Color(0xFFE69F00), // 橙
        Color(0xFF56B4E9), // 天蓝
        Color(0xFF009E73), // 蓝绿
        Color(0xFFD55E00), // 朱红
        Color(0xFFCC79A7), // 紫粉
        Color(0xFF0072B2), // 蓝
        Color(0xFFF0E442), // 黄
        Color(0xFF44AA99)  // 灰青
    )
}

@Composable
fun VoiceBridgeTheme(content: @Composable () -> Unit) {
    content()
}
