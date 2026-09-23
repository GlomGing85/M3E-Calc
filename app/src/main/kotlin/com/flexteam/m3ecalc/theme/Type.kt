package com.flexteam.m3ecalc.theme

import android.graphics.Typeface
import android.os.Build
import android.widget.TextView

/** A Material 3 type style: size, line height, weight and tracking. */
data class TextStyle(
    val sizeSp: Float,
    val lineSp: Float,
    val weight: Int = 400,
    val trackingSp: Float = 0f
)

/**
 * The standard Material 3 type scale, rendered in Roboto (the platform default
 * family, so `Typeface.DEFAULT` is already Roboto on Android).
 */
object Type {
    val displayLarge = TextStyle(57f, 64f)
    val displayMedium = TextStyle(45f, 52f)
    val displaySmall = TextStyle(36f, 44f)
    val headlineLarge = TextStyle(32f, 40f)
    val headlineMedium = TextStyle(28f, 36f)
    val headlineSmall = TextStyle(24f, 32f)
    val titleLarge = TextStyle(22f, 28f, weight = 500)
    val titleMedium = TextStyle(16f, 24f, weight = 500, trackingSp = 0.15f)
    val titleSmall = TextStyle(14f, 20f, weight = 500, trackingSp = 0.1f)
    val bodyLarge = TextStyle(16f, 24f, trackingSp = 0.5f)
    val bodyMedium = TextStyle(14f, 20f, trackingSp = 0.25f)
    val bodySmall = TextStyle(12f, 16f, trackingSp = 0.4f)
    val labelLarge = TextStyle(14f, 20f, weight = 500, trackingSp = 0.1f)
    val labelMedium = TextStyle(12f, 16f, weight = 500, trackingSp = 0.5f)
    val labelSmall = TextStyle(11f, 16f, weight = 500, trackingSp = 0.5f)
}

/** Roboto at a Material weight; falls back below API 28 where weights are unavailable. */
fun typefaceFor(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
    else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

/** Applies a type style to a TextView (size, line height, weight, tracking). */
fun TextView.applyStyle(style: TextStyle) {
    textSize = style.sizeSp
    if (Build.VERSION.SDK_INT >= 28) {
        setLineHeight(
            Math.round(style.lineSp * resources.displayMetrics.scaledDensity)
        )
    } else {
        val extra = (style.lineSp - style.sizeSp) * resources.displayMetrics.scaledDensity
        setLineSpacing(extra, 1f)
    }
    typeface = typefaceFor(style.weight)
    letterSpacing = if (style.sizeSp > 0f) style.trackingSp / style.sizeSp else 0f
}

/** The bold "section header" style used by the settings screen (22sp bold). */
val SectionHeader = TextStyle(22f, 30f, weight = 700)

/** The oversized display style used by the theme / button previews (45sp bold). */
val DisplayBold = TextStyle(45f, 54f, weight = 700)
