package com.kreadex.daymark.data.db

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreadex.daymark.DayMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class DayMarkViewModel(
    private val calendarDao: CalendarDao,
    private val calendarId: Long
) : ViewModel() {

    private val _calendar = MutableStateFlow<CalendarEntity?>(null)
    val calendar = _calendar.asStateFlow()

    init {
        viewModelScope.launch {
            _calendar.value = calendarDao.getById(calendarId)
        }
    }


    fun setDay(
        date: LocalDate,
        colorIndex: Int,
        note: String?,
        event: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cal = _calendar.value ?: return@launch

            val dateStr = date.toString()
            val updated = cal.months.toMutableList()
            updated.removeAll { it.date == dateStr }

            if (colorIndex != 0 || !note.isNullOrBlank() || event != null) {
                updated += DayMark(dateStr, colorIndex, note, event)
            }

            val newCalendar = cal.copy(months = updated)

            calendarDao.update(newCalendar)

            _calendar.value = newCalendar
        }
    }
}
