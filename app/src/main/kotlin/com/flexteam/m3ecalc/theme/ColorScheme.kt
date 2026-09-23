package com.flexteam.m3ecalc.theme

/**
 * A Material 3 colour scheme.
 *
 * Every colour the app paints is reached through one of these roles; nothing in
 * the UI layer hard-codes a hex value. Two schemes are built in (the Purple
 * fallback below) and a third is derived from the system palette on Android 12+.
 */
class ColorScheme(
    val isDark: Boolean,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val surfaceDim: Int,
    val surfaceBright: Int,
    val outline: Int,
    val outlineVariant: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val inversePrimary: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val scrim: Int = 0xFF000000.toInt()
) {
    /** 8% / 12% emphasis overlays used by ripple states. */
    fun stateLayer(base: Int, alpha: Int): Int =
        ((alpha shl 24) or (base and 0x00FFFFFF))
}

private fun rgb(hex: String): Int = (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()

/** The Purple fallback scheme, exactly as specified for the app. */
object FallbackSchemes {

    val light = ColorScheme(
        isDark = false,
        primary = rgb("#6750A4"),
        onPrimary = rgb("#FFFFFF"),
        primaryContainer = rgb("#EADDFF"),
        onPrimaryContainer = rgb("#21005D"),
        secondary = rgb("#635A75"),
        onSecondary = rgb("#FFFFFF"),
        secondaryContainer = rgb("#E8DEF8"),
        onSecondaryContainer = rgb("#1D192B"),
        tertiary = rgb("#7D5260"),
        onTertiary = rgb("#FFFFFF"),
        tertiaryContainer = rgb("#FFD8E4"),
        onTertiaryContainer = rgb("#31111D"),
        surface = rgb("#FEF7FF"),
        onSurface = rgb("#1D1B20"),
        onSurfaceVariant = rgb("#49454F"),
        surfaceContainerLowest = rgb("#FFFFFF"),
        surfaceContainerLow = rgb("#F7F2FA"),
        surfaceContainer = rgb("#F3EDF7"),
        surfaceContainerHigh = rgb("#ECE6F0"),
        surfaceContainerHighest = rgb("#E6E0E9"),
        surfaceDim = rgb("#DED8E1"),
        surfaceBright = rgb("#FEF7FF"),
        outline = rgb("#79747E"),
        outlineVariant = rgb("#CAC4D0"),
        inverseSurface = rgb("#322F35"),
        inverseOnSurface = rgb("#F5EFF7"),
        inversePrimary = rgb("#D0BCFF"),
        error = rgb("#B3261E"),
        onError = rgb("#FFFFFF"),
        errorContainer = rgb("#F9DEDC"),
        onErrorContainer = rgb("#410E0B")
    )

    val dark = ColorScheme(
        isDark = true,
        primary = rgb("#D2BCFC"),
        onPrimary = rgb("#32226F"),
        primaryContainer = rgb("#4C3889"),
        onPrimaryContainer = rgb("#E9DDFF"),
        secondary = rgb("#CDC1E1"),
        onSecondary = rgb("#332D41"),
        secondaryContainer = rgb("#4B425D"),
        onSecondaryContainer = rgb("#E9DDFD"),
        tertiary = rgb("#EFB8C8"),
        onTertiary = rgb("#4F2632"),
        tertiaryContainer = rgb("#6C3644"),
        onTertiaryContainer = rgb("#FDDAE1"),
        surface = rgb("#141317"),
        onSurface = rgb("#E4E1E7"),
        onSurfaceVariant = rgb("#C9C4D1"),
        surfaceContainerLowest = rgb("#0F0E13"),
        surfaceContainerLow = rgb("#1C1B1F"),
        surfaceContainer = rgb("#201F23"),
        surfaceContainerHigh = rgb("#2B292D"),
        surfaceContainerHighest = rgb("#363438"),
        surfaceDim = rgb("#141317"),
        surfaceBright = rgb("#3B383E"),
        outline = rgb("#938F9B"),
        outlineVariant = rgb("#494550"),
        inverseSurface = rgb("#E4E1E7"),
        inverseOnSurface = rgb("#313034"),
        inversePrimary = rgb("#6750A4"),
        error = rgb("#F2B8B5"),
        onError = rgb("#601410"),
        errorContainer = rgb("#8C1D18"),
        onErrorContainer = rgb("#F9DEDC")
    )
}
