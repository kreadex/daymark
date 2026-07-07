package com.kreadex.daymark.data

import android.content.Context
import android.content.res.Configuration

class AppPreferences(context: Context) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        private const val KEY_THEME = "theme_mode_int"
        private const val KEY_OLD_DARK_MODE = "dark_mode"
    }

    var themeMode: Int
        get() {
            if (prefs.contains(KEY_OLD_DARK_MODE)) {
                val oldDark = prefs.getBoolean(KEY_OLD_DARK_MODE, false)
                val newMode = if (oldDark) THEME_DARK else THEME_LIGHT
                prefs.edit().putInt(KEY_THEME, newMode).remove(KEY_OLD_DARK_MODE).apply()
                return newMode
            }
            return prefs.getInt(KEY_THEME, THEME_SYSTEM)
        }
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    fun isActuallyDark(context: Context): Boolean {
        return when (themeMode) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) = prefs.edit().putBoolean("sound_enabled", value).apply()

    var language: String
        get() = prefs.getString("lang", "en") ?: "en"
        set(value) = prefs.edit().putString("lang", value).apply()
}