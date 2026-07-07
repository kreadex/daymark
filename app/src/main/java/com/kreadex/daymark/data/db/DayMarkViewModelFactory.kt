package com.kreadex.daymark.data.db

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DayMarkViewModelFactory(
    private val dao: CalendarDao,
    private val calendarId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DayMarkViewModel(dao, calendarId) as T
    }
}



