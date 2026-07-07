package com.kreadex.daymark.data.db

import com.kreadex.daymark.DayMark

class CalendarRepository(
    private val dao: CalendarDao
) {

    suspend fun getCalendar(id: Long): CalendarEntity? =
        dao.getById(id)

    suspend fun addOrUpdateDay(
        calendarId: Long,
        dayMark: DayMark
    ) {
        val calendar = dao.getById(calendarId) ?: return

        val updatedMonths = calendar.months
            .filterNot { it.date == dayMark.date } +
                dayMark

        dao.insert(calendar.copy(months = updatedMonths))
    }

    suspend fun removeDay(
        calendarId: Long,
        date: String
    ) {
        val calendar = dao.getById(calendarId) ?: return

        dao.insert(
            calendar.copy(
                months = calendar.months.filterNot {
                    it.date == date
                }
            )
        )
    }

    fun getMarksForMonth(
        calendar: CalendarEntity,
        yearMonth: String // "2025-06"
    ): List<DayMark> =
        calendar.months.filter {
            it.date.startsWith(yearMonth)
        }
}
