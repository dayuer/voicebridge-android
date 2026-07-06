package com.voicebridge.android.data.entity

import java.util.Locale

/**
 * 应用支持的语言定义
 * 迁移自 iOS 侧 SupportedLanguage.swift
 */
enum class SupportedLanguage(
    val rawValue: String,
    val displayName: String,
    val flag: String,
    val speechLocaleCode: String,
    val isSenseVoiceSupported: Boolean
) {
    CHINESE("zh-Hans", "简体中文", "🇨🇳", "zh-CN", true),
    ENGLISH("en", "English", "🇺🇸", "en-US", true),
    JAPANESE("ja", "日本語", "🇯🇵", "ja-JP", true),
    KOREAN("ko", "한국어", "🇰🇷", "ko-KR", true),
    CANTONESE("yue", "粤语", "🇭🇰", "zh-HK", true),
    INDONESIAN("id", "Bahasa Indonesia", "🇮🇩", "id-ID", false),
    FRENCH("fr", "Français", "🇫🇷", "fr-FR", false),
    GERMAN("de", "Deutsch", "🇩🇪", "de-DE", false),
    SPANISH("es", "Español", "🇪🇸", "es-ES", false),
    THAI("th", "ภาษาไทย", "🇹🇭", "th-TH", false),
    VIETNAMESE("vi", "Tiếng Việt", "🇻🇳", "vi-VN", false),
    MALAY("ms", "Bahasa Melayu", "🇲🇾", "ms-MY", false);

    val id: String get() = rawValue
    val locale: Locale get() = Locale.forLanguageTag(rawValue)
    val speechLocale: Locale get() = Locale.forLanguageTag(speechLocaleCode)

    companion object {
        fun fromRawValue(rawValue: String): SupportedLanguage? {
            return entries.find { it.rawValue == rawValue }
        }

        val asrLanguages: List<SupportedLanguage> get() = entries
    }
}
