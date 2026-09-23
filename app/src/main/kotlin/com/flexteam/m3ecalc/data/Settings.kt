package com.flexteam.m3ecalc.data

import android.content.Context
import com.flexteam.m3ecalc.calc.AngleUnit
import com.flexteam.m3ecalc.theme.ThemeMode
import com.flexteam.m3ecalc.theme.ThemeSource

/**
 * App settings, persisted with SharedPreferences so they survive restarts.
 * Every value the settings screen can change lives here.
 */
class Settings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("m3e-settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = enum("theme_mode", ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString("theme_mode", value.name).apply()

    var themeSource: ThemeSource
        get() = enum("theme_source", ThemeSource.DYNAMIC)
        set(value) = prefs.edit().putString("theme_source", value.name).apply()

    /** Press-effect strength, 0..1. Default 40%. */
    var pressEffect: Float
        get() = prefs.getFloat("press_effect", 0.4f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat("press_effect", value.coerceIn(0f, 1f)).apply()

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()

    var angleUnit: AngleUnit
        get() = enum("angle_unit", AngleUnit.DEG)
        set(value) = prefs.edit().putString("angle_unit", value.name).apply()

    var precision: Int
        get() = prefs.getInt("precision", 10).coerceIn(0, 15)
        set(value) = prefs.edit().putInt("precision", value.coerceIn(0, 15)).apply()

    var thousandsSeparator: Boolean
        get() = prefs.getBoolean("thousands", true)
        set(value) = prefs.edit().putBoolean("thousands", value).apply()

    var historyLimit: Int
        get() = prefs.getInt("history_limit", 200).coerceIn(20, 1000)
        set(value) = prefs.edit().putInt("history_limit", value.coerceIn(20, 1000)).apply()

    /** Last converter category the user was in. */
    var converterCategory: String
        get() = prefs.getString("converter_category", Categories.LENGTH) ?: Categories.LENGTH
        set(value) = prefs.edit().putString("converter_category", value).apply()

    /** Remembered from/to unit per category, stored as "from|to". */
    fun units(category: String): Pair<String, String>? =
        prefs.getString("units_$category", null)?.split("|")?.takeIf { it.size == 2 }
            ?.let { it[0] to it[1] }

    fun setUnits(category: String, from: String, to: String) =
        prefs.edit().putString("units_$category", "$from|$to").apply()

    /** Highest version name the update check has seen ("" when never checked). */
    var lastKnownVersion: String
        get() = prefs.getString("last_known_version", "") ?: ""
        set(value) = prefs.edit().putString("last_known_version", value).apply()

    private inline fun <reified T : Enum<T>> enum(key: String, default: T): T {
        val name = prefs.getString(key, null) ?: return default
        return try {
            enumValueOf<T>(name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }
}
