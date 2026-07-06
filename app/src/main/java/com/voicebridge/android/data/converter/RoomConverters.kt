package com.voicebridge.android.data.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date

/**
 * Room 数据持久化类型转换器
 * 提供 Date, List<Float>, List<String> 与 SQLite 支持类型的转换
 */
class RoomConverters {
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromFloatList(value: List<Float>?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toFloatList(value: String?): List<Float>? {
        return value?.let { Json.decodeFromString<List<Float>>(it) }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { Json.decodeFromString<List<String>>(it) }
    }
}
