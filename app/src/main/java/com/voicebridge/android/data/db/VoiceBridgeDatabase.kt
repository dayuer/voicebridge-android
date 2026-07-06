package com.voicebridge.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.voicebridge.android.data.converter.RoomConverters
import com.voicebridge.android.data.dao.AISummaryItemDao
import com.voicebridge.android.data.dao.GlossaryEntryDao
import com.voicebridge.android.data.dao.MeetingRecordDao
import com.voicebridge.android.data.dao.SpeakerProfileDao
import com.voicebridge.android.data.dao.TranscriptSegmentDao
import com.voicebridge.android.data.dao.VoiceSampleDao
import com.voicebridge.android.data.entity.AISummaryItemEntity
import com.voicebridge.android.data.entity.GlossaryEntryEntity
import com.voicebridge.android.data.entity.MeetingRecordEntity
import com.voicebridge.android.data.entity.SpeakerProfileEntity
import com.voicebridge.android.data.entity.TranscriptSegmentEntity
import com.voicebridge.android.data.entity.VoiceSampleEntity

/**
 * VoiceBridge Room 数据库定义
 */
@Database(
    entities = [
        MeetingRecordEntity::class,
        TranscriptSegmentEntity::class,
        SpeakerProfileEntity::class,
        VoiceSampleEntity::class,
        AISummaryItemEntity::class,
        GlossaryEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class VoiceBridgeDatabase : RoomDatabase() {
    
    abstract fun meetingRecordDao(): MeetingRecordDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun speakerProfileDao(): SpeakerProfileDao
    abstract fun voiceSampleDao(): VoiceSampleDao
    abstract fun aiSummaryItemDao(): AISummaryItemDao
    abstract fun glossaryEntryDao(): GlossaryEntryDao
}
