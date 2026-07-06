package com.voicebridge.android.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 声纹样本独立实体 — 从音频中由 VAD 切分出的独立语音片段实体
 * 映射自 iOS 侧 VoiceSample.swift
 */
@Entity(
    tableName = "voice_samples",
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
data class VoiceSampleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val embedding: List<Float> = emptyList(),
    
    @ColumnInfo(name = "start_time")
    val startTime: Double = 0.0,
    
    @ColumnInfo(name = "end_time")
    val endTime: Double = 0.0,
    
    @ColumnInfo(name = "meeting_id")
    val meetingId: String? = null,
    
    @ColumnInfo(name = "speaker_profile_id")
    val speakerProfileId: String? = null
)
