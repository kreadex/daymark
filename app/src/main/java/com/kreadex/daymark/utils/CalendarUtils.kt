package com.kreadex.daymark.utils

import java.time.DayOfWeek
import java.time.YearMonth

fun buildMonthGrid(month: YearMonth): List<Int?> {
    val firstDay = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()

    val startOffset = firstDay.dayOfWeek.value - 1

    val result = mutableListOf<Int?>()

    repeat(startOffset) {
        result.add(null)
    }

    for (day in 1..daysInMonth) {
        result.add(day)
    }

    return result
}

