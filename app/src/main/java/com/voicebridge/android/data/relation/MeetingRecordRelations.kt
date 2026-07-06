package com.voicebridge.android.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.voicebridge.android.data.entity.AISummaryItemEntity
import com.voicebridge.android.data.entity.MeetingRecordEntity
import com.voicebridge.android.data.entity.SpeakerProfileEntity
import com.voicebridge.android.data.entity.TranscriptSegmentEntity
import com.voicebridge.android.data.entity.VoiceSampleEntity

/**
 * 封装会议记录与其关联的转录段落
 */
data class MeetingRecordWithSegments(
    @Embedded 
    val meetingRecord: MeetingRecordEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "meeting_id"
    )
    val segments: List<TranscriptSegmentEntity>
)

/**
 * 封装发言人声纹与其关联的所有转录段落
 */
data class SpeakerProfileWithSegments(
    @Embedded 
    val speakerProfile: SpeakerProfileEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "speaker_profile_id"
    )
    val segments: List<TranscriptSegmentEntity>
)

/**
 * 完整的会议记录视图，包含段落、声纹样本、以及 AI 归档项
 */
data class MeetingRecordComplete(
    @Embedded 
    val meetingRecord: MeetingRecordEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "meeting_id"
    )
    val segments: List<TranscriptSegmentEntity>,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "meeting_id"
    )
    val voiceSamples: List<VoiceSampleEntity>,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "meeting_id"
    )
    val aiItems: List<AISummaryItemEntity>
)
