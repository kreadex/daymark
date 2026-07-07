package com.kreadex.daymark.utils

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kreadex.daymark.DayMark

class DayMarkConverter {

    private val gson = Gson()

    @TypeConverter
    fun fromList(value: List<DayMark>): String =
        gson.toJson(value)

    @TypeConverter
    fun toList(value: String): List<DayMark> =
        gson.fromJson(value, object : TypeToken<List<DayMark>>() {}.type)
}
