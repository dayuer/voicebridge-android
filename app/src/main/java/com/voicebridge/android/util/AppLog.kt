package com.voicebridge.android.util

import android.util.Log

/**
 * 统一日志工具 — 对齐 iOS AppLog.swift
 * 全局开关控制，按模块分类，同步写入 Logcat 与内存日志收集中心 LogStore。
 */
object AppLog {

    private const val TAG = "VoiceBridge"

    /** 全局日志开关 — 设为 false 一键关闭所有日志（错误除外） */
    @Volatile
    var isEnabled = true

    private fun log(category: String, icon: String, msg: String) {
        if (!isEnabled) return
        Log.i(TAG, "$icon [$category] $msg")
        LogStore.append(category, icon, msg)
    }

    /** 导入模块 */
    fun fileImport(msg: String) = log("导入", "📥", msg)

    /** 语音识别模块 */
    fun speech(msg: String) = log("识别", "🎙️", msg)

    /** 转写管线模块（后台队列调度、chunk 续传等） */
    fun pipeline(msg: String) = log("管线", "🔄", msg)

    /** 声纹提取模块 */
    fun voiceprint(msg: String) = log("声纹", "🎤", msg)

    /** 标点恢复模块 */
    fun punctuation(msg: String) = log("标点", "✏️", msg)

    /** 引擎编排 */
    fun engine(msg: String) = log("引擎", "⚙️", msg)

    /** 音频解码 */
    fun audio(msg: String) = log("音频", "🎧", msg)

    /** 错误（始终输出，不受开关控制） */
    fun error(msg: String) {
        Log.e(TAG, "❌ [错误] $msg")
        LogStore.append("错误", "❌", msg)
    }
}
