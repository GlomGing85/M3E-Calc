package com.flexteam.m3ecalc

import android.content.Context
import android.view.View

/** Which edge a screen slides in from. */
enum class Direction { NONE, LEFT, RIGHT, BOTTOM }

/** A screen in the app's own back stack. */
abstract class Screen(val context: Context, val nav: Nav) {

    /** Builds the screen's root view. Called once, when it is first pushed. */
    abstract fun content(): View

    /** Called every time the screen becomes the top of the stack. */
    open fun onShow() = Unit

    /** Called when another screen is pushed on top, or this one is popped. */
    open fun onHide() = Unit

    /** Status bar / navigation bar / IME insets, applied by the activity. */
    open fun applyInsets(top: Int, bottom: Int, ime: Int) = Unit

    open val title: String get() = javaClass.simpleName
}

/** The back stack. Implemented by [MainActivity]. */
interface Nav {
    fun push(screen: Screen, from: Direction = Direction.RIGHT)
    fun pop()
    fun popToRoot()
    val depth: Int
}
