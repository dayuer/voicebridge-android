package com.voicebridge.android.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 转录片段 — 单个发言人的一段话
 * 映射自 iOS 侧 TranscriptSegment (包含在 MeetingRecord.swift 中)
 */
@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = MeetingRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["meeting_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SpeakerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["speaker_profile_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("meeting_id"),
        Index("speaker_profile_id")
    ]
)
data class TranscriptSegmentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "meeting_id")
    val meetingId: String,
    
    @ColumnInfo(name = "speaker_profile_id")
    val speakerProfileId: String? = null,
    
    @ColumnInfo(name = "speaker_label")
    val speakerLabel: String = "",
    
    @ColumnInfo(name = "speaker_color_index")
    val speakerColorIndex: Int = 0,
    
    val text: String = "",
    
    @ColumnInfo(name = "translated_text")
    val translatedText: String? = null,
    
    val timestamp: Double = 0.0,
    
    @ColumnInfo(name = "end_timestamp")
    val endTimestamp: Double = 0.0,
    
    @ColumnInfo(name = "is_final")
    val isFinal: Boolean = true,
    
    val embedding: List<Float>? = null,
    
    @ColumnInfo(name = "voiceprint_embedding")
    val voiceprintEmbedding: List<Float>? = null
)
