package com.voicebridge.android.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * 自定义词库条目 — 用于 AI Prompt 术语注入，帮助大模型纠正转录中的专有名词
 * 映射自 iOS 侧 GlossaryEntry.swift
 */
@Entity(tableName = "glossary_entries")
data class GlossaryEntryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val term: String = "",
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date()
) {
    companion object {
        const val MAX_ENTRY_COUNT = 200
    }
}
