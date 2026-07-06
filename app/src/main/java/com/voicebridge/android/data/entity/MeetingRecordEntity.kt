package com.voicebridge.android.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * 会议记录持久化模型
 * 映射自 iOS 侧 MeetingRecord.swift
 */
@Entity(tableName = "meeting_records")
data class MeetingRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val title: String = "",
    
    @ColumnInfo(name = "start_time")
    val startTime: Date = Date(),
    
    @ColumnInfo(name = "end_time")
    val endTime: Date? = null,
    
    val duration: Double = 0.0,
    
    @ColumnInfo(name = "source_language_code")
    val sourceLanguageCode: String = "zh-Hans",
    
    @ColumnInfo(name = "target_language_code")
    val targetLanguageCode: String = "en",
    
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    
    @ColumnInfo(name = "import_progress")
    val importProgress: Double = 0.0,
    
    @ColumnInfo(name = "import_language_code")
    val importLanguageCode: String? = null,
    
    @ColumnInfo(name = "resume_chunk_index")
    val resumeChunkIndex: Int = -1,
    
    @ColumnInfo(name = "total_chunk_count")
    val totalChunkCount: Int = 0,
    
    @ColumnInfo(name = "audio_file_path")
    val audioFilePath: String? = null,
    
    @ColumnInfo(name = "expected_speaker_ids")
    val expectedSpeakerIds: List<String> = emptyList(),
    
    @ColumnInfo(name = "file_fingerprint")
    val fileFingerprint: String? = null,
    
    @ColumnInfo(name = "diarization_state")
    val diarizationState: Int = 0,
    
    @ColumnInfo(name = "pending_diarization_input_json")
    val pendingDiarizationInputJson: ByteArray? = null,
    
    @ColumnInfo(name = "ai_summary")
    val aiSummary: String? = null,
    
    @ColumnInfo(name = "ai_summary_snippet")
    val aiSummarySnippet: String? = null,
    
    @ColumnInfo(name = "ai_summary_updated_at")
    val aiSummaryUpdatedAt: Date? = null
)
