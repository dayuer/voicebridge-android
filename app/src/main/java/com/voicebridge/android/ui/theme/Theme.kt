package com.voicebridge.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App 外观模式 — 对齐 iOS SettingsStore.AppAppearance
 */
enum class AppAppearance(val rawValue: String, val displayName: String) {
    SYSTEM("system", "自动"),
    LIGHT("light", "亮色"),
    DARK("dark", "暗色");

    companion object {
        fun fromRawValue(raw: String?): AppAppearance =
            entries.find { it.rawValue == raw } ?: SYSTEM
    }
}

/** 当前生效的暗色态（由 VoiceBridgeTheme 提供，未提供时跟随系统） */
val LocalAppDarkTheme = staticCompositionLocalOf<Boolean?> { null }

@Composable
@ReadOnlyComposable
private fun isAppDarkTheme(): Boolean = LocalAppDarkTheme.current ?: isSystemInDarkTheme()

object VoiceBridgeTheme {
    // 页面Canvas背景
    val bgCanvas: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFF0F0F17) else Color(0xFFF6F7F9)

    // 卡片 / 列表行背景
    val bgSurface: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFF191922) else Color(0xFFFFFFFF)

    // 浮层 / 底部面板背景
    val bgElevated: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFF22222E) else Color(0xFFFFFFFF)

    // 描边 / 分隔线
    val separator: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0x1AFFFFFF) else Color(0x14000000)

    // 文本颜色角色
    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFFF2F3F7) else Color(0xFF1A1A22)

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFFB9BBC7) else Color(0xFF4A4A56)

    val textTertiary: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFF8B8D9A) else Color(0xFF6C6C77)

    val textDisabled: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFF5A5C68) else Color(0xFFA0A0AA)

    val textOnAccent = Color.White

    // 品牌强调色 (单强调色，靛蓝)
    val accent: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFF7A7AF0) else Color(0xFF5B5BD6)

    val accentGradientEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFF6E6AE8) else Color(0xFF4C46C8)

    // 语义状态色
    val success: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFF10B981) else Color(0xFF047857)

    val error: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFFEF4444) else Color(0xFFDC2626)

    val warning: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isAppDarkTheme()) Color(0xFFF59E0B) else Color(0xFFB45309)

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

// 品牌靛蓝 Material 3 配色 — 语义映射对齐 iOS Theme.swift
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E3FA),
    onPrimaryContainer = Color(0xFF31319B),
    secondary = Color(0xFF5C5C72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF44445A),
    tertiary = Color(0xFF00829B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5EBF4),
    onTertiaryContainer = Color(0xFF004E5D),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF1A1A22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A22),
    surfaceVariant = Color(0xFFEDEDF4),
    onSurfaceVariant = Color(0xFF4A4A56),
    outline = Color(0xFF9A9AAA),
    outlineVariant = Color(0xFFE0E0E8),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFBE2E2),
    onErrorContainer = Color(0xFF8C1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7A7AF0),
    onPrimary = Color(0xFF1E1E52),
    primaryContainer = Color(0xFF3A3AA8),
    onPrimaryContainer = Color(0xFFE3E3FA),
    secondary = Color(0xFFC5C4DD),
    onSecondary = Color(0xFF2E2E43),
    secondaryContainer = Color(0xFF44445A),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFF56D6EE),
    onTertiary = Color(0xFF003641),
    tertiaryContainer = Color(0xFF004E5D),
    onTertiaryContainer = Color(0xFFC5EBF4),
    background = Color(0xFF0F0F17),
    onBackground = Color(0xFFF2F3F7),
    surface = Color(0xFF191922),
    onSurface = Color(0xFFF2F3F7),
    surfaceVariant = Color(0xFF22222E),
    onSurfaceVariant = Color(0xFFB9BBC7),
    outline = Color(0xFF8B8D9A),
    outlineVariant = Color(0xFF2E2E3C),
    error = Color(0xFFEF4444),
    onError = Color(0xFF4A0A0A),
    errorContainer = Color(0xFF7A1B1B),
    onErrorContainer = Color(0xFFFBE2E2)
)

@Composable
fun VoiceBridgeTheme(
    appearance: AppAppearance = AppAppearance.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }
    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
