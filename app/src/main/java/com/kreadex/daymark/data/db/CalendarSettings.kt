package com.kreadex.daymark.data.db

import com.google.gson.Gson

data class CalendarSettings(
    val dayColors: List<String> = listOf(
        "0x79988888", "0xFFEB3434", "0xFFFF6822",
        "0xFFFFC107", "0xFFA2E356", "0xFF34D6EB", "0xFF3449eb"
    ),
    val iconIndex: Int = 1
)

object SettingsManager {
    private val gson = Gson()

    // Превращаем JSON строку из БД в объект настроек
    fun fromJson(json: String?): CalendarSettings {
        if (json.isNullOrBlank()) return CalendarSettings()
        return try {
            gson.fromJson(json, CalendarSettings::class.java)
        } catch (e: Exception) {
            CalendarSettings()
        }
    }

    // Превращаем объект настроек в JSON для сохранения в БД
    fun toJson(settings: CalendarSettings): String {
        return gson.toJson(settings)
    }
}