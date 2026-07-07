package com.kreadex.daymark.data.db

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class CalendarViewModel(
    private val dao: CalendarDao
) : ViewModel() {



    val calendars = dao.getAll()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun addCalendar(name: String, description: String?, settings: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val maxOrder = dao.getMaxOrder() ?: -1

            val newCalendar = CalendarEntity(
                name = name,
                description = description,
                settings = settings,
                orderIndex = maxOrder + 1,
                dateCreated = System.currentTimeMillis()
            )

            dao.insert(newCalendar)
        }
    }

    fun updateCalendar(calendar: CalendarEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(calendar)
        }
    }

    private var isMovingTaskActive = false

    fun moveStep(calendar: CalendarEntity, direction: Int) {
        viewModelScope.launch(Dispatchers.IO) { // Уходим в фоновый поток для БД
            if (isMovingTaskActive) return@launch
            isMovingTaskActive = true

            try {
                val currentList = calendars.value // Список должен быть отсортирован по order
                val currentIndex = currentList.indexOfFirst { it.id == calendar.id }
                val targetIndex = currentIndex + direction

                if (currentIndex != -1 && targetIndex in currentList.indices) {
                    val targetCalendar = currentList[targetIndex]

                    // Атомарно меняем order в одной транзакции (если DAO позволяет)
                    // Или просто два апдейта подряд:
                    val oldOrder = calendar.orderIndex
                    val newOrder = targetCalendar.orderIndex

                    dao.updateOrder(calendar.id, newOrder)
                    dao.updateOrder(targetCalendar.id, oldOrder)

                    // Важный затык для плавности Compose
                    delay(100)
                }
            } finally {
                isMovingTaskActive = false
            }
        }
    }

    fun deleteCalendar(calendar: CalendarEntity) {
        viewModelScope.launch {
            dao.delete(calendar)
        }
    }

}

