package com.voicebridge.android.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 声纹指纹库模型 — 跨会议绑定真实发言人身份
 * 映射自 iOS 侧 SpeakerProfile.swift
 */
@Entity(tableName = "speaker_profiles")
data class SpeakerProfileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val name: String = "",
    
    val voiceprint: List<Float>? = null,
    
    @ColumnInfo(name = "sample_count")
    val sampleCount: Int = 0,
    
    @ColumnInfo(name = "total_duration")
    val totalDuration: Double = 0.0,
    
    @ColumnInfo(name = "embedding_variance")
    val embeddingVariance: Float = 0f
)
