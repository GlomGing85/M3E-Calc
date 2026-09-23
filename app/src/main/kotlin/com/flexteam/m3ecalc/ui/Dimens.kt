package com.flexteam.m3ecalc.ui

import kotlin.math.roundToInt

import android.view.ViewGroup

/** Values captured once in [com.flexteam.m3ecalc.MainActivity]. */
object UI {
    var density: Float = 1f
    var fontScale: Float = 1f

    /** Haptic feedback setting, refreshed with the theme. */
    var haptics: Boolean = true

    /** Root the snack bar floats in, set to the activity's screen container. */
    var overlayHost: ViewGroup? = null
}

val Int.dp: Int get() = (this * UI.density).roundToInt()
val Float.dp: Float get() = this * UI.density
fun dp(value: Float): Int = (value * UI.density).roundToInt()
