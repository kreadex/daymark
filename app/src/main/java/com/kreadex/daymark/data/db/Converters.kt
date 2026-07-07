package com.kreadex.daymark.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kreadex.daymark.DayMark
import java.time.LocalDate

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromDayMarkList(value: List<DayMark>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDayMarkList(value: String): List<DayMark> {
        val type = object : TypeToken<List<DayMark>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? =
        date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it) }


}
