package com.kreadex.daymark.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kreadex.daymark.utils.DayMarkConverter

@Database(
    entities = [CalendarEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DayMarkConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun calendarDao(): CalendarDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                CREATE TABLE IF NOT EXISTS `calendars_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `dateCreated` INTEGER NOT NULL, 
                    `dateEdited` INTEGER, 
                    `description` TEXT, 
                    `months` TEXT NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `orderIndex` INTEGER NOT NULL DEFAULT 0, 
                    `pinned` INTEGER NOT NULL, 
                    `settings` TEXT
                )
            """)

                val cursor = db.query("PRAGMA table_info(calendars)")
                var hasOrderIndex = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "orderIndex") {
                        hasOrderIndex = true
                        break
                    }
                }
                cursor.close()

                val sourceColumn = if (hasOrderIndex) "`orderIndex`" else "0"

                db.execSQL("""
                INSERT INTO `calendars_new` (id, dateCreated, dateEdited, description, months, name, orderIndex, pinned, settings)
                SELECT id, dateCreated, dateEdited, description, months, name, $sourceColumn, pinned, settings 
                FROM `calendars`
            """)

                db.execSQL("DROP TABLE `calendars`")
                db.execSQL("ALTER TABLE `calendars_new` RENAME TO `calendars`")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }

    }
}