package com.kreadex.daymark.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendars ORDER BY `orderIndex` ASC")
    fun getAll(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars")
    suspend fun getAllDirectly(): List<CalendarEntity>

    @Query("SELECT * FROM calendars")
    suspend fun getAllList(): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CalendarEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) // Не перезаписывать, если ID есть
    suspend fun insert(calendar: CalendarEntity)

    @Update
    suspend fun update(calendar: CalendarEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calendars: List<CalendarEntity>)

    @Query("DELETE FROM calendars")
    suspend fun clearAll()

    @Delete
    suspend fun delete(calendar: CalendarEntity)

    @Query("UPDATE calendars SET `orderIndex` = :newOrder WHERE id = :id")
    suspend fun updateOrder(id: Long, newOrder: Int)

    @Query("SELECT MAX(`orderIndex`) FROM calendars")
    suspend fun getMaxOrder(): Int?

}

