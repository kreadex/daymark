package com.kreadex.daymark.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kreadex.daymark.data.db.CalendarDao
import com.kreadex.daymark.data.db.CalendarEntity
import java.io.File

class BackupManager(private val dao: CalendarDao) {

    suspend fun exportToJson(): String {
        val items = dao.getAllList()
        return Gson().toJson(items)
    }

    suspend fun importFromJson(json: String) {
        val type = object : TypeToken<List<CalendarEntity>>() {}.type
        val items: List<CalendarEntity> = Gson().fromJson(json, type)

        dao.clearAll()
        dao.insertAll(items)
    }

    fun exportFile(context: Context, json: String) {
        val tempFile = File(context.cacheDir, "backup.json")
        tempFile.writeText(json)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться бэкапом"))
    }


}