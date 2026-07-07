package com.voicebridge.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import java.util.UUID

enum class AIApp(val packageName: String) {
    CHATGPT("com.openai.chatgpt"),
    CLAUDE("com.anthropic.claude"),
    GEMINI("com.google.android.apps.bard"),
    DEEPSEEK("com.deepseek.chat"),
    DOUBAO("com.bytedance.mira.macaron"),
    KIMI("com.moonshot.kimi"),
    TONGYI("com.alibaba.tongyi"),
    ZHIPU("cn.zhipu.chatglm");

    val displayName: String
        get() = when (this) {
            CHATGPT -> "ChatGPT"
            CLAUDE -> "Claude"
            GEMINI -> "Gemini"
            DEEPSEEK -> "DeepSeek"
            DOUBAO -> "豆包"
            KIMI -> "Kimi"
            TONGYI -> "通义千问"
            ZHIPU -> "智谱清言"
        }
}

object AIAppLauncher {
    private const val TAG = "AIAppLauncher"
    
    // keys for SharedPreferences to track waiting state
    private const val PREFS_NAME = "ai_clipboard_prefs"
    private const val KEY_AWAITING_RESULT = "ai.awaitingResult"
    private const val KEY_PROMPT_HASH = "ai.promptHash"
    private const val KEY_VERIFICATION_KEY = "ai.verificationKey"
    private const val KEY_VERIFICATION_TIME = "ai.verificationKeyTimestamp"

    fun isInstalled(context: Context, app: AIApp): Boolean {
        return try {
            context.packageManager.getPackageInfo(app.packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun installedApps(context: Context): List<AIApp> {
        return AIApp.values().filter { isInstalled(context, it) }
    }

    fun writeToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Prompt", text)
        clipboard.setPrimaryClip(clip)
    }

    fun launch(context: Context, app: AIApp, text: String) {
        // Always write to clipboard
        writeToClipboard(context, text)
        markAwaitingResult(context, text)

        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Log.e(TAG, "Failed to launch ${app.displayName}: Intent is null")
        }
    }

    fun clipboardText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0) {
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                return text
            }
        }
        return null
    }

    // MARK: - Waiting state logic
    
    fun markAwaitingResult(context: Context, promptText: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AWAITING_RESULT, true)
            .putInt(KEY_PROMPT_HASH, promptText.hashCode())
            .apply()
    }

    fun clearAwaitingResult(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AWAITING_RESULT, false).apply()
    }

    fun hasPendingResultCandidate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_AWAITING_RESULT, false)) return false
        
        val currentText = clipboardText(context) ?: return false
        val savedHash = prefs.getInt(KEY_PROMPT_HASH, 0)
        
        // If the current clipboard hash is different from the prompt hash, 
        // it means the user has copied something new (likely the AI result).
        return currentText.hashCode() != savedHash
    }

    // MARK: - Verification Key Management

    fun generateVerificationKey(context: Context): String {
        val uuid = UUID.randomUUID().toString().substring(0, 8).lowercase()
        val key = "voicebridge-key-${uuid}"
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_VERIFICATION_KEY, key)
            .putLong(KEY_VERIFICATION_TIME, System.currentTimeMillis())
            .apply()
            
        return key
    }

    fun currentValidKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_VERIFICATION_KEY, null) ?: return null
        val timestamp = prefs.getLong(KEY_VERIFICATION_TIME, 0)
        
        // 15 minutes timeout (15 * 60 * 1000 = 900000 ms)
        if (System.currentTimeMillis() - timestamp > 900000) {
            clearVerificationKey(context)
            return null
        }
        
        return key
    }

    fun clearVerificationKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_VERIFICATION_KEY)
            .remove(KEY_VERIFICATION_TIME)
            .apply()
    }
}
