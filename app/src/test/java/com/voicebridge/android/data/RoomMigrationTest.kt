package com.voicebridge.android.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.data.entity.AISummaryItemEntity
import com.voicebridge.android.data.entity.GlossaryEntryEntity
import com.voicebridge.android.data.entity.MeetingRecordEntity
import com.voicebridge.android.data.entity.SpeakerProfileEntity
import com.voicebridge.android.data.entity.TranscriptSegmentEntity
import com.voicebridge.android.data.entity.VoiceSampleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * 针对 Room 持久层的单元测试
 * 运行在 Robolectric 模拟的 Android 环境中，重点验证：
 * 1. 会议删除时的级联删除 (CASCADE) 规则
 * 2. 发言人删除时对关联数据段落/声纹样本的置空 (SET_NULL) 规则
 * 3. 词库条目上限 (200条) 的业务约束逻辑
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomMigrationTest {

    private lateinit var db: VoiceBridgeDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VoiceBridgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testMeetingCascadeDelete() = runBlocking {
        val meetingId = "meeting_uuid_123"
        val meeting = MeetingRecordEntity(
            id = meetingId,
            title = "Android 迁移架构会",
            startTime = Date()
        )
        db.meetingRecordDao().insert(meeting)

        // 1. 插入关联的转录片段
        val segment = TranscriptSegmentEntity(
            id = "segment_1",
            meetingId = meetingId,
            text = "今天我们讨论 Android 迁移工作...",
            timestamp = 1.2
        )
        db.transcriptSegmentDao().insert(segment)

        // 2. 插入关联的声纹样本
        val voiceSample = VoiceSampleEntity(
            id = "sample_1",
            meetingId = meetingId,
            startTime = 1.0,
            endTime = 3.5,
            embedding = listOf(0.1f, -0.2f, 0.5f)
        )
        db.voiceSampleDao().insert(voiceSample)

        // 3. 插入关联的 AI 总结归档项
        val aiItem = AISummaryItemEntity(
            id = "ai_item_1",
            meetingId = meetingId,
            typeKey = "summary",
            content = "今天讨论了 Room 迁移与 Jetpack 映射"
        )
        db.aiSummaryItemDao().insert(aiItem)

        // 验证插入成功
        val savedMeeting = db.meetingRecordDao().getById(meetingId)
        assertNotNull(savedMeeting)
        assertEquals("Android 迁移架构会", savedMeeting?.title)

        var completeList = db.meetingRecordDao().getAllMeetingsComplete().first()
        assertEquals(1, completeList.size)
        assertEquals(1, completeList[0].segments.size)
        assertEquals(1, completeList[0].voiceSamples.size)
        assertEquals(1, completeList[0].aiItems.size)

        // 执行删除 Meeting
        db.meetingRecordDao().delete(meeting)

        // 验证级联删除生效：Meeting 消失，对应的 Segments、VoiceSamples、AIItems 均自动被级联删除
        val deletedMeeting = db.meetingRecordDao().getById(meetingId)
        assertNull(deletedMeeting)

        completeList = db.meetingRecordDao().getAllMeetingsComplete().first()
        assertTrue(completeList.isEmpty())

        val segmentList = db.transcriptSegmentDao().getSegmentsByMeetingId(meetingId).first()
        assertTrue(segmentList.isEmpty())

        val sampleList = db.voiceSampleDao().getSamplesByMeetingId(meetingId).first()
        assertTrue(sampleList.isEmpty())

        val aiList = db.aiSummaryItemDao().getItemsByMeetingId(meetingId).first()
        assertTrue(aiList.isEmpty())
    }

    @Test
    fun testSpeakerProfileNullify() = runBlocking {
        val meetingId = "meeting_uuid_456"
        val speakerId = "speaker_uuid_789"

        // 插入会议
        db.meetingRecordDao().insert(MeetingRecordEntity(id = meetingId, title = "测试声纹级联"))

        // 插入声纹 Profile
        val speaker = SpeakerProfileEntity(
            id = speakerId,
            name = "开发负责人",
            voiceprint = listOf(0.9f, 0.1f, -0.05f)
        )
        db.speakerProfileDao().insert(speaker)

        // 插入关联的转录片段与声纹样本
        val segment = TranscriptSegmentEntity(
            id = "segment_2",
            meetingId = meetingId,
            speakerProfileId = speakerId,
            text = "大家加油移植声纹引擎！"
        )
        db.transcriptSegmentDao().insert(segment)

        val voiceSample = VoiceSampleEntity(
            id = "sample_2",
            meetingId = meetingId,
            speakerProfileId = speakerId,
            startTime = 0.5,
            endTime = 2.0,
            embedding = listOf(0.85f, 0.12f, -0.04f)
        )
        db.voiceSampleDao().insert(voiceSample)

        // 验证关联数据存在并引用 speakerId
        val profileWithSegments = db.speakerProfileDao().getProfileWithSegments(speakerId).first()
        assertNotNull(profileWithSegments)
        assertEquals("开发负责人", profileWithSegments?.speakerProfile?.name)
        assertEquals(1, profileWithSegments?.segments?.size)

        // 执行删除 SpeakerProfile
        db.speakerProfileDao().delete(speaker)

        // 验证 SpeakerProfile 实体已删除
        assertNull(db.speakerProfileDao().getById(speakerId))

        // 验证转录片段与声纹样本依然保存在数据库中，但其外键关联 speakerProfileId 已被安全置空 (.nullify)
        val segmentList = db.transcriptSegmentDao().getSegmentsByMeetingId(meetingId).first()
        assertEquals(1, segmentList.size)
        assertNull(segmentList[0].speakerProfileId) // 外键已被置 null

        val sampleList = db.voiceSampleDao().getSamplesByMeetingId(meetingId).first()
        assertEquals(1, sampleList.size)
        assertNull(sampleList[0].speakerProfileId) // 外键已被置 null
    }

    @Test
    fun testGlossaryLimit() = runBlocking {
        // 批量插入 200 个词条，应该全部成功
        for (i in 1..200) {
            val entry = GlossaryEntryEntity(id = "term_$i", term = "专有名词$i")
            val success = db.glossaryEntryDao().insertWithLimit(entry)
            assertTrue(success)
        }

        assertEquals(200, db.glossaryEntryDao().getCount())

        // 尝试插入第 201 个，应该返回 false 且不能写入数据库
        val overflowEntry = GlossaryEntryEntity(id = "term_201", term = "超量词条")
        val success201 = db.glossaryEntryDao().insertWithLimit(overflowEntry)
        assertFalse(success201)

        // 最终条数依然是 200
        assertEquals(200, db.glossaryEntryDao().getCount())

        val list = db.glossaryEntryDao().getAllEntries().first()
        assertEquals(200, list.size)
    }
}
