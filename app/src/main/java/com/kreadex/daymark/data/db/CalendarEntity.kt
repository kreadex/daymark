package com.kreadex.daymark.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kreadex.daymark.DayMark

@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val description: String? = null,

    val pinned: Boolean = false,

    val dateCreated: Long = System.currentTimeMillis(),
    val dateEdited: Long? = null,

    val settings: String? = null,
    val months: List<DayMark> = emptyList(),
    val orderIndex: Int = 0
)

