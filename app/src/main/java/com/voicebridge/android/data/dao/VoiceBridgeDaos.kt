package com.voicebridge.android.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.voicebridge.android.data.entity.AISummaryItemEntity
import com.voicebridge.android.data.entity.GlossaryEntryEntity
import com.voicebridge.android.data.entity.MeetingRecordEntity
import com.voicebridge.android.data.entity.SpeakerProfileEntity
import com.voicebridge.android.data.entity.TranscriptSegmentEntity
import com.voicebridge.android.data.entity.VoiceSampleEntity
import com.voicebridge.android.data.relation.MeetingRecordComplete
import com.voicebridge.android.data.relation.MeetingRecordWithSegments
import com.voicebridge.android.data.relation.SpeakerProfileWithSegments
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meeting: MeetingRecordEntity)

    @Update
    suspend fun update(meeting: MeetingRecordEntity)

    @Delete
    suspend fun delete(meeting: MeetingRecordEntity)

    @Query("SELECT * FROM meeting_records WHERE id = :id")
    suspend fun getById(id: String): MeetingRecordEntity?

    @Transaction
    @Query("SELECT * FROM meeting_records ORDER BY start_time DESC")
    fun getAllMeetingsComplete(): Flow<List<MeetingRecordComplete>>

    @Transaction
    @Query("SELECT * FROM meeting_records WHERE id = :id")
    fun getMeetingCompleteById(id: String): Flow<MeetingRecordComplete?>

    @Transaction
    @Query("SELECT * FROM meeting_records WHERE id = :id")
    fun getMeetingWithSegmentsById(id: String): Flow<MeetingRecordWithSegments?>
}

@Dao
interface TranscriptSegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: TranscriptSegmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<TranscriptSegmentEntity>)

    @Update
    suspend fun update(segment: TranscriptSegmentEntity)

    @Delete
    suspend fun delete(segment: TranscriptSegmentEntity)

    @Query("DELETE FROM transcript_segments WHERE meeting_id = :meetingId")
    suspend fun deleteByMeetingId(meetingId: String)

    @Query("SELECT * FROM transcript_segments WHERE meeting_id = :meetingId ORDER BY timestamp ASC")
    fun getSegmentsByMeetingId(meetingId: String): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE meeting_id = :meetingId ORDER BY timestamp ASC")
    suspend fun getSegmentsByMeetingIdOnce(meetingId: String): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM transcript_segments WHERE speaker_profile_id = :profileId")
    suspend fun getSegmentsByProfileIdOnce(profileId: String): List<TranscriptSegmentEntity>
}

@Dao
interface SpeakerProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: SpeakerProfileEntity)

    @Update
    suspend fun update(profile: SpeakerProfileEntity)

    @Delete
    suspend fun delete(profile: SpeakerProfileEntity)

    @Query("SELECT * FROM speaker_profiles WHERE id = :id")
    suspend fun getById(id: String): SpeakerProfileEntity?

    @Query("SELECT * FROM speaker_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<SpeakerProfileEntity>>

    @Transaction
    @Query("SELECT * FROM speaker_profiles WHERE id = :id")
    fun getProfileWithSegments(id: String): Flow<SpeakerProfileWithSegments?>
}

@Dao
interface VoiceSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: VoiceSampleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<VoiceSampleEntity>)

    @Update
    suspend fun update(sample: VoiceSampleEntity)

    @Delete
    suspend fun delete(sample: VoiceSampleEntity)

    @Query("SELECT * FROM voice_samples WHERE meeting_id = :meetingId ORDER BY start_time ASC")
    fun getSamplesByMeetingId(meetingId: String): Flow<List<VoiceSampleEntity>>

    @Query("SELECT * FROM voice_samples WHERE speaker_profile_id IS NULL ORDER BY start_time DESC")
    suspend fun getUnassignedSamplesOnce(): List<VoiceSampleEntity>

    @Query("SELECT * FROM voice_samples WHERE speaker_profile_id = :profileId")
    suspend fun getSamplesByProfileIdOnce(profileId: String): List<VoiceSampleEntity>
}

@Dao
interface AISummaryItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AISummaryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AISummaryItemEntity>)

    @Update
    suspend fun update(item: AISummaryItemEntity)

    @Delete
    suspend fun delete(item: AISummaryItemEntity)

    @Query("SELECT * FROM ai_summary_items WHERE meeting_id = :meetingId ORDER BY updated_at DESC")
    fun getItemsByMeetingId(meetingId: String): Flow<List<AISummaryItemEntity>>
}

@Dao
interface GlossaryEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(entry: GlossaryEntryEntity)

    @Update
    suspend fun update(entry: GlossaryEntryEntity)

    @Delete
    suspend fun delete(entry: GlossaryEntryEntity)

    @Query("SELECT COUNT(*) FROM glossary_entries")
    suspend fun getCount(): Int

    @Query("SELECT * FROM glossary_entries ORDER BY created_at DESC")
    fun getAllEntries(): Flow<List<GlossaryEntryEntity>>

    @Transaction
    suspend fun insertWithLimit(entry: GlossaryEntryEntity): Boolean {
        return if (getCount() >= GlossaryEntryEntity.MAX_ENTRY_COUNT) {
            false
        } else {
            insertRaw(entry)
            true
        }
    }
}
