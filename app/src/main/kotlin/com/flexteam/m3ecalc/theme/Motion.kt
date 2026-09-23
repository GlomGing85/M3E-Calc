package com.flexteam.m3ecalc.theme

import android.animation.TimeInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A damped-spring curve expressed as an [Interpolator] - the View-world
 * equivalent of the spring specs `MotionScheme.expressive()` hands out in
 * Compose: motion overshoots slightly and settles instead of easing out flat.
 */
class SpringInterpolator(
    dampingRatio: Float = 0.7f,
    stiffness: Float = 380f
) : Interpolator {

    private val w0 = sqrt(stiffness.toDouble())
    private val zeta = dampingRatio.coerceIn(0.05f, 1f).toDouble()
    private val wd = w0 * sqrt(1.0 - zeta * zeta)
    private val settle = 6.0 / (zeta * w0)

    override fun getInterpolation(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val x = t * settle
        val decay = exp(-zeta * w0 * x)
        val value = 1.0 - decay * (cos(wd * x) + (zeta * w0 / wd) * sin(wd * x))
        return value.toFloat().coerceAtLeast(0f)
    }
}

/** Adapter so [Interpolator] can be used with `ViewPropertyAnimator`. */
fun Interpolator.asTimeInterpolator(): TimeInterpolator = TimeInterpolator { getInterpolation(it) }

/**
 * Motion tokens, named after the Material 3 motion scheme so call sites read
 * the same way they would in a Compose app.
 */
object Motion {
    const val shortMillis = 200L
    const val mediumMillis = 420L
    const val longMillis = 620L

    /** Screen / surface transitions: quick start, springy settle. */
    val emphasized: Interpolator = SpringInterpolator(0.82f, 420f)

    /** State changes - toggles, selection, sheets: a light, visible bounce. */
    val expressive: Interpolator = SpringInterpolator(0.55f, 460f)

    /** Small emphasis - presses, icon swaps. */
    val quick: Interpolator = SpringInterpolator(0.9f, 620f)

    val decelerate: Interpolator = DecelerateInterpolator(1.6f)
    val overshoot: Interpolator = OvershootInterpolator(1.4f)
}
