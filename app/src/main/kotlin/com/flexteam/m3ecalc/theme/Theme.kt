package com.flexteam.m3ecalc.theme

import android.content.Context
import android.content.res.Configuration
import com.flexteam.m3ecalc.data.Settings

/** Theme mode chosen in Settings ("Appearance"). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colour source chosen in Settings ("Theme"). */
enum class ThemeSource { DYNAMIC, PURPLE }

/**
 * The single place the resolved scheme lives. Screens read [Theme.colors]
 * while building their views; changing a setting re-resolves the scheme and
 * the activity is recreated so every view is rebuilt with the new roles.
 */
object Theme {

    var colors: ColorScheme = FallbackSchemes.light
        private set

    /** True when the resolved colours came from the wallpaper palette. */
    var isDynamic: Boolean = false
        private set

    /** Press-effect strength from Settings, 0..1 (default 40%). */
    var pressScale: Float = 0.4f

    fun resolve(context: Context, settings: Settings): ColorScheme {
        val systemDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val dark = when (settings.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val dynamic = if (settings.themeSource == ThemeSource.DYNAMIC) {
            DynamicColor.scheme(context, dark)
        } else {
            null
        }
        val scheme = dynamic ?: if (dark) FallbackSchemes.dark else FallbackSchemes.light
        colors = scheme
        isDynamic = dynamic != null
        pressScale = settings.pressEffect
        com.flexteam.m3ecalc.ui.UI.haptics = settings.haptics
        return scheme
    }
}
