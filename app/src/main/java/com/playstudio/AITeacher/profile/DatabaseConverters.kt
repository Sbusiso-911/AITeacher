package com.playstudio.aiteacher.profile

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class DatabaseConverters {
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return Gson().toJson(value)
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }
    
    @TypeConverter
    fun fromStringMap(value: Map<String, Any>?): String {
        return Gson().toJson(value)
    }
    
    @TypeConverter
    fun toStringMap(value: String): Map<String, Any>? {
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        return Gson().fromJson(value, mapType)
    }
    
    @TypeConverter
    fun fromStringIntMap(value: Map<String, Int>?): String {
        return Gson().toJson(value)
    }
    
    @TypeConverter
    fun toStringIntMap(value: String): Map<String, Int> {
        val mapType = object : TypeToken<Map<String, Int>>() {}.type
        return Gson().fromJson(value, mapType) ?: emptyMap()
    }
}