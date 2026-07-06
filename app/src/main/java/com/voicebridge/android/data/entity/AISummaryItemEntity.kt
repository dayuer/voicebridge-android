package com.voicebridge.android.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * AI 助手生成的多维度归档条目
 * 映射自 iOS 侧 AISummaryItem (包含在 MeetingRecord.swift 中)
 */
@Entity(
    tableName = "ai_summary_items",
    foreignKeys = [
        ForeignKey(
            entity = MeetingRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["meeting_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("meeting_id")
    ]
)
data class AISummaryItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "type_key")
    val typeKey: String = "",
    
    val content: String = "",
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date(),
    
    @ColumnInfo(name = "meeting_id")
    val meetingId: String? = null
)
