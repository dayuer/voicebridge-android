package com.voicebridge.android.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 线程安全的全局日志存储器 — 对齐 iOS LogStore.swift
 * 内存环形缓冲：达到 1000 条上限时批量移除最早的 200 条。
 */
object LogStore {

    data class LogEntry(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Date = Date(),
        val category: String,
        val icon: String,
        val message: String
    ) {
        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(timestamp)
    }

    private const val MAX_ENTRIES = 1000
    private const val TRIM_BATCH = 200

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    @Synchronized
    fun append(category: String, icon: String, message: String) {
        val current = _entries.value
        val next = if (current.size >= MAX_ENTRIES) {
            current.drop(TRIM_BATCH) + LogEntry(category = category, icon = icon, message = message)
        } else {
            current + LogEntry(category = category, icon = icon, message = message)
        }
        _entries.value = next
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }
}
