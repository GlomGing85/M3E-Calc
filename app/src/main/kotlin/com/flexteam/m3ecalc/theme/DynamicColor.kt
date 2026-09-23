package com.flexteam.m3ecalc.theme

import android.content.Context
import android.content.res.Resources
import android.os.Build

/**
 * Dynamic colour ("Material You").
 *
 * On Android 12+ the framework publishes the wallpaper-derived palette as
 * `@android:color/system_accent{1,2,3}_*` and `system_neutral{1,2}_*`. Those
 * ramps are indexed by *lightness step* (0 = black, 1000 = white), whereas the
 * Material 3 roles are indexed by *tone* (0..100, higher = lighter), so the
 * two are mapped onto each other below. When the palette is unavailable the
 * caller falls back to the built-in Purple scheme.
 */
object DynamicColor {

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 31

    /** Material 3 tone -> framework palette index. */
    private fun toneToIndex(tone: Int): Int = when {
        tone <= 4 -> 1000
        tone <= 14 -> 900
        tone <= 24 -> 800
        tone <= 34 -> 700
        tone <= 44 -> 600
        tone <= 54 -> 500
        tone <= 64 -> 400
        tone <= 74 -> 300
        tone <= 84 -> 200
        tone <= 92 -> 100
        tone <= 96 -> 50
        else -> 10
    }

    private fun palette(context: Context, family: String, tone: Int): Int? {
        if (!supported) return null
        val name = "system_${family}_${toneToIndex(tone)}"
        val id = Resources.getSystem().getIdentifier(name, "color", "android")
        if (id == 0) return null
        return try {
            context.getColor(id)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    /** Builds a full scheme from the system palette, or null if unavailable. */
    fun scheme(context: Context, dark: Boolean): ColorScheme? {
        if (!supported) return null
        fun c(family: String, tone: Int): Int? = palette(context, family, tone)

        val required = listOf(
            c("accent1", 40), c("accent1", 90), c("accent1", 10),
            c("accent2", 40), c("accent2", 90), c("accent2", 10),
            c("accent3", 90), c("accent3", 10),
            c("neutral1", 98), c("neutral1", 10),
            c("neutral2", 30), c("neutral2", 50), c("neutral2", 80)
        )
        if (required.any { it == null }) return null

        val fallback = if (dark) FallbackSchemes.dark else FallbackSchemes.light

        fun pick(family: String, tone: Int): Int = palette(context, family, tone)
            ?: (if (dark) FallbackSchemes.dark else FallbackSchemes.light).let { it.surface }

        return if (!dark) ColorScheme(
            isDark = false,
            primary = pick("accent1", 40),
            onPrimary = pick("accent1", 100),
            primaryContainer = pick("accent1", 90),
            onPrimaryContainer = pick("accent1", 10),
            secondary = pick("accent2", 40),
            onSecondary = pick("accent2", 100),
            secondaryContainer = pick("accent2", 90),
            onSecondaryContainer = pick("accent2", 10),
            tertiary = pick("accent3", 40),
            onTertiary = pick("accent3", 100),
            tertiaryContainer = pick("accent3", 90),
            onTertiaryContainer = pick("accent3", 10),
            surface = pick("neutral1", 98),
            onSurface = pick("neutral1", 10),
            onSurfaceVariant = pick("neutral2", 30),
            surfaceContainerLowest = pick("neutral1", 100),
            surfaceContainerLow = pick("neutral1", 96),
            surfaceContainer = pick("neutral1", 94),
            surfaceContainerHigh = pick("neutral1", 92),
            surfaceContainerHighest = pick("neutral1", 90),
            surfaceDim = pick("neutral1", 87),
            surfaceBright = pick("neutral1", 98),
            outline = pick("neutral2", 50),
            outlineVariant = pick("neutral2", 80),
            inverseSurface = pick("neutral1", 20),
            inverseOnSurface = pick("neutral1", 95),
            inversePrimary = pick("accent1", 80),
            error = fallback.error,
            onError = fallback.onError,
            errorContainer = fallback.errorContainer,
            onErrorContainer = fallback.onErrorContainer
        ) else ColorScheme(
            isDark = true,
            primary = pick("accent1", 80),
            onPrimary = pick("accent1", 20),
            primaryContainer = pick("accent1", 30),
            onPrimaryContainer = pick("accent1", 90),
            secondary = pick("accent2", 80),
            onSecondary = pick("accent2", 20),
            secondaryContainer = pick("accent2", 30),
            onSecondaryContainer = pick("accent2", 90),
            tertiary = pick("accent3", 80),
            onTertiary = pick("accent3", 20),
            tertiaryContainer = pick("accent3", 30),
            onTertiaryContainer = pick("accent3", 90),
            surface = pick("neutral1", 6),
            onSurface = pick("neutral1", 90),
            onSurfaceVariant = pick("neutral2", 80),
            surfaceContainerLowest = pick("neutral1", 4),
            surfaceContainerLow = pick("neutral1", 10),
            surfaceContainer = pick("neutral1", 12),
            surfaceContainerHigh = pick("neutral1", 17),
            surfaceContainerHighest = pick("neutral1", 22),
            surfaceDim = pick("neutral1", 6),
            surfaceBright = pick("neutral1", 24),
            outline = pick("neutral2", 60),
            outlineVariant = pick("neutral2", 30),
            inverseSurface = pick("neutral1", 90),
            inverseOnSurface = pick("neutral1", 20),
            inversePrimary = pick("accent1", 40),
            error = fallback.error,
            onError = fallback.onError,
            errorContainer = fallback.errorContainer,
            onErrorContainer = fallback.onErrorContainer
        )
    }
}
