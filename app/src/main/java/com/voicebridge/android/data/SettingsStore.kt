package com.voicebridge.android.data

import android.content.Context
import android.content.SharedPreferences
import com.voicebridge.android.data.entity.SupportedLanguage
import com.voicebridge.android.ui.theme.AppAppearance

/**
 * 全局设置存储 — SharedPreferences 持久化
 * 对齐 iOS SettingsStore.swift，是所有用户可配置项的唯一来源。
 */
object SettingsStore {

    const val PREFS_NAME = "voicebridge_settings"

    // Keys（has_agreed_privacy 为历史键名，保持兼容）
    private const val KEY_PRIVACY_CONSENT = "has_agreed_privacy"
    private const val KEY_AI_DATA_SHARING = "ai_data_sharing_consent"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_DEFAULT_LANGUAGE = "default_language"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // MARK: - 外观

    fun getAppearance(context: Context): AppAppearance =
        AppAppearance.fromRawValue(prefs(context).getString(KEY_APPEARANCE, null))

    fun setAppearance(context: Context, appearance: AppAppearance) {
        prefs(context).edit().putString(KEY_APPEARANCE, appearance.rawValue).apply()
    }

    // MARK: - 默认识别语言（null = 自动检测）

    fun getDefaultLanguage(context: Context): SupportedLanguage? =
        prefs(context).getString(KEY_DEFAULT_LANGUAGE, null)
            ?.let { SupportedLanguage.fromRawValue(it) }

    fun setDefaultLanguage(context: Context, language: SupportedLanguage?) {
        prefs(context).edit().apply {
            if (language == null) remove(KEY_DEFAULT_LANGUAGE)
            else putString(KEY_DEFAULT_LANGUAGE, language.rawValue)
        }.apply()
    }

    // MARK: - 隐私协议确认

    fun hasAgreedToPrivacyConsent(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRIVACY_CONSENT, false)

    /**
     * 同步行为对齐 iOS：撤销隐私同意时同时撤销 AI 数据分享授权；
     * 同意隐私时默认将 AI 数据分享置为 true。
     */
    fun setPrivacyConsent(context: Context, agreed: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_PRIVACY_CONSENT, agreed)
            .putBoolean(KEY_AI_DATA_SHARING, agreed)
            .apply()
    }

    // MARK: - AI 数据分享同意

    fun hasAgreedToAIDataSharing(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AI_DATA_SHARING, false)

    fun setAIDataSharingConsent(context: Context, agreed: Boolean) {
        prefs(context).edit().putBoolean(KEY_AI_DATA_SHARING, agreed).apply()
    }
}
