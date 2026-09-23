package com.flexteam.m3ecalc

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import com.flexteam.m3ecalc.screens.CalculatorScreen
import com.flexteam.m3ecalc.theme.Motion
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.asTimeInterpolator
import com.flexteam.m3ecalc.ui.UI

/**
 * Single activity host. The seven screens are plain views stacked in a
 * FrameLayout; [Nav] pushes and pops them with the slide transitions the
 * design asks for, and the system back gesture plays the entry in reverse.
 */
class MainActivity : Activity(), Nav {

    private lateinit var container: FrameLayout
    private val stack = mutableListOf<Screen>()
    private val views = mutableMapOf<Screen, View>()
    private val enteredFrom = mutableMapOf<Screen, Direction>()

    private var topInset = 0
    private var bottomInset = 0
    private var imeInset = 0

    var calculator: CalculatorScreen? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        App.init(this)
        Theme.resolve(this, App.settings)
        super.onCreate(savedInstanceState)

        UI.density = resources.displayMetrics.density
        UI.fontScale = resources.configuration.fontScale

        window.setBackgroundDrawable(ColorDrawable(Theme.colors.surface))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        drawEdgeToEdge()

        container = FrameLayout(this)
        setContentView(container)
        UI.overlayHost = container
        container.setOnApplyWindowInsetsListener { _, insets ->
            readInsets(insets)
            insets
        }

        val home = CalculatorScreen(this, this)
        calculator = home
        push(home, Direction.NONE)
    }

    @Suppress("DEPRECATION")
    private fun drawEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        applyBarIcons()
    }

    /** Dark icons on a light status bar, light icons on a dark one. */
    @Suppress("DEPRECATION")
    private fun applyBarIcons() {
        val light = !Theme.colors.isDark
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController ?: return
            val flag = BarAppearance.APPEARANCE_LIGHT_STATUS_BARS or
                BarAppearance.APPEARANCE_LIGHT_NAVIGATION_BARS
            if (light) controller.setSystemBarsAppearance(
                flag,
                BarAppearance.APPEARANCE_LIGHT_STATUS_BARS or
                    BarAppearance.APPEARANCE_LIGHT_NAVIGATION_BARS
            ) else controller.setSystemBarsAppearance(0, flag)
        } else if (Build.VERSION.SDK_INT >= 27) {
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (light) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun readInsets(insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            topInset = bars.top
            bottomInset = bars.bottom
            imeInset = ime.bottom
        } else {
            @Suppress("DEPRECATION")
            topInset = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            bottomInset = insets.systemWindowInsetBottom
            imeInset = 0
        }
        stack.lastOrNull()?.applyInsets(topInset, bottomInset, imeInset)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyBarIcons()
    }

    // ------------------------------------------------------------------- Nav

    override val depth: Int get() = stack.size

    override fun push(screen: Screen, from: Direction) {
        val view = screen.content()
        views[screen] = view
        enteredFrom[screen] = from
        container.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val width = screenWidth()
        when (from) {
            Direction.LEFT -> view.translationX = -width.toFloat()
            Direction.RIGHT -> view.translationX = width.toFloat()
            Direction.BOTTOM -> view.translationY = container.heightOrScreen().toFloat()
            Direction.NONE -> Unit
        }

        val previous = stack.lastOrNull()
        stack.add(screen)

        previous?.let { prev ->
            val prevView = views[prev] ?: return@let
            prev.onHide()
            val animator = when (from) {
                Direction.LEFT -> prevView.animate().translationX(width * 0.25f)
                Direction.RIGHT -> prevView.animate().translationX(-width * 0.25f)
                Direction.BOTTOM -> prevView.animate().alpha(0.35f)
                Direction.NONE -> null
            }
            animator?.setDuration(Motion.mediumMillis)
                ?.setInterpolator(Motion.emphasized.asTimeInterpolator())?.start()
        }

        view.animate().translationX(0f).translationY(0f).alpha(1f)
            .setDuration(if (from == Direction.NONE) 0L else Motion.mediumMillis)
            .setInterpolator(Motion.emphasized.asTimeInterpolator()).start()

        screen.applyInsets(topInset, bottomInset, imeInset)
        screen.onShow()
    }

    override fun pop() {
        if (stack.size <= 1) {
            finish()
            return
        }
        val top = stack.removeAt(stack.size - 1)
        val view = views.remove(top)
        val from = enteredFrom.remove(top) ?: Direction.RIGHT
        val below = stack.last()
        val belowView = views[below]
        val width = screenWidth()

        view?.animate()
            ?.translationX(
                when (from) {
                    Direction.LEFT -> -width.toFloat()
                    Direction.BOTTOM -> 0f
                    else -> width.toFloat()
                }
            )
            ?.translationY(if (from == Direction.BOTTOM) container.heightOrScreen().toFloat() else 0f)
            ?.alpha(if (from == Direction.BOTTOM) 1f else 0.6f)
            ?.setDuration(Motion.mediumMillis)
            ?.setInterpolator(Motion.emphasized.asTimeInterpolator())
            ?.withEndAction { container.removeView(view) }?.start()

        belowView?.animate()?.translationX(0f)?.alpha(1f)
            ?.setDuration(Motion.mediumMillis)
            ?.setInterpolator(Motion.emphasized.asTimeInterpolator())?.start()

        top.onHide()
        below.applyInsets(topInset, bottomInset, imeInset)
        below.onShow()
    }

    override fun popToRoot() {
        while (stack.size > 1) pop()
    }

    /** Hands a value from another screen (History, Converter) to the keypad. */
    fun deliverToCalculator(text: String) {
        calculator?.acceptResult(text)
    }

    private fun screenWidth(): Int =
        if (container.width > 0) container.width else resources.displayMetrics.widthPixels

    private fun FrameLayout.heightOrScreen(): Int =
        if (height > 0) height else resources.displayMetrics.heightPixels

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (stack.size > 1) pop() else super.onBackPressed()
    }

    /** Recreates the whole UI after a setting changes the colour scheme. */
    fun applyThemeChange() {
        Theme.resolve(this, App.settings)
        recreate()
    }
}

/** WindowInsetsController appearance bits (API 30). */
private object BarAppearance {
    const val APPEARANCE_LIGHT_STATUS_BARS = 8
    const val APPEARANCE_LIGHT_NAVIGATION_BARS = 16
}
