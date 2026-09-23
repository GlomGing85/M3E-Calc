package com.flexteam.m3ecalc.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.flexteam.m3ecalc.theme.Motion
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.applyStyle
import com.flexteam.m3ecalc.theme.Type
import com.flexteam.m3ecalc.theme.asTimeInterpolator
import com.flexteam.m3ecalc.theme.typefaceFor
import kotlin.math.max

enum class M3ButtonStyle { FILLED, TONAL, ELEVATED, OUTLINED, TEXT }

/** M3 size scale: side padding / label size / icon size for a given height. */
private data class SizeScale(val labelSp: Float, val iconSp: Float, val padH: Int)

private fun scaleFor(heightDp: Int): SizeScale = when {
    heightDp <= 32 -> SizeScale(12f, 16f, 10)
    heightDp <= 40 -> SizeScale(13f, 18f, 14)
    heightDp <= 56 -> SizeScale(14f, 20f, 24)
    heightDp <= 96 -> SizeScale(20f, 32f, 32)
    else -> SizeScale(28f, 48f, 40)
}

/**
 * Ripple + slight press-scale, the press feedback every tappable part gets.
 * The depth is driven by the "press effect" setting in Settings.
 */
class PressScaleTouchListener(
    private val depth: () -> Float,
    private val haptics: () -> Boolean = { true }
) : View.OnTouchListener {
    private var pressed = false

    @SuppressLint("ClickSafeLint")
    override fun onTouch(v: View, e: MotionEvent): Boolean {
        if (!v.isEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                val d = depth()
                v.animate().cancel()
                v.animate().scaleX(1f - d).scaleY(1f - d)
                    .setDuration(Motion.shortMillis / 2)
                    .setInterpolator(Motion.quick.asTimeInterpolator()).start()
            }
            MotionEvent.ACTION_UP -> {
                if (pressed) {
                    pressed = false
                    if (haptics()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    release(v)
                    // No performClick() here: this listener returns false, so the
                    // view's own touch handling fires the click exactly once.
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pressed) {
                    pressed = false
                    release(v)
                }
            }
        }
        return false
    }

    private fun release(v: View) {
        v.animate().cancel()
        v.animate().scaleX(1f).scaleY(1f).setDuration(Motion.shortMillis)
            .setInterpolator(Motion.expressive.asTimeInterpolator()).start()
    }
}

private fun pressDepth(): Float = 0.16f * Theme.pressScale.coerceIn(0f, 1f)

/** Adds ripple + press-scale to an already clickable view. */
fun View.makePressable(rippleColor: Int, mask: GradientDrawable? = null) {
    if (background != null && background is RippleDrawable) return
    val ripple = RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        background,
        mask
    )
    background = ripple
    setOnTouchListener(PressScaleTouchListener({ pressDepth() }, { UI.haptics }))
    isHapticFeedbackEnabled = true
}

private fun shape(
    color: Int,
    radii: FloatArray? = null,
    radius: Float = 0f,
    stroke: Pair<Float, Int>? = null
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color)
    if (radii != null) cornerRadii = radii else cornerRadius = radius
    if (stroke != null) setStroke(stroke.first.toInt(), stroke.second)
}

/**
 * A Material 3 button: pill shaped, [M3ButtonStyle] coloured, ripple plus a
 * slight press-scale. Height follows the M3 size scale (32/40/56/96/136).
 */
class M3Button @JvmOverloads constructor(
    context: Context,
    label: String = "",
    style: M3ButtonStyle = M3ButtonStyle.FILLED,
    icon: String? = null,
    var sizeDp: Int = 56,
    /** Fixed corner radius instead of the pill default (used by the extended FAB). */
    var cornerRadiusDp: Int? = null,
    var onAction: (() -> Unit)? = null
) : FrameLayout(context) {

    private var style: M3ButtonStyle = style
    private var iconGlyph: String? = icon

    private val labelView = TextView(context)
    private val iconView = TextView(context)
    private var radii: FloatArray? = null
    private var selectedTonal = false

    private val scale get() = scaleFor(sizeDp)

    init {
        isClickable = true
        isFocusable = true
        minimumHeight = sizeDp.dp
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        iconView.apply {
            typeface = Icons.typeface(context)
            includeFontPadding = false
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.dp }
        }
        labelView.apply {
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(iconView)
        row.addView(labelView)
        addView(row)
        setLabel(label)
        applyStyle()
        setOnClickListener {
            onAction?.invoke()
        }
    }

    fun setLabel(text: String) {
        labelView.text = text
        labelView.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        labelView.textSize = scale.labelSp
        labelView.typeface = typefaceFor(500)
        labelView.includeFontPadding = false
        requestLayout()
    }

    fun setIcon(glyph: String?) {
        iconGlyph = glyph
        if (glyph == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.text = glyph
            iconView.textSize = scale.iconSp
            iconView.visibility = View.VISIBLE
        }
        requestLayout()
    }

    /** Corner radii override used by connected button groups. */
    fun setCorners(r: FloatArray) {
        radii = r
        applyStyle()
    }

    fun setStyle(newStyle: M3ButtonStyle, tonalWhenSelected: Boolean = false) {
        style = newStyle
        selectedTonal = tonalWhenSelected
        applyStyle()
    }

    private val cornerRadiusPx: Float
        get() = cornerRadiusDp?.dp?.toFloat() ?: (sizeDp.dp / 2f)

    private fun cornerArray(): FloatArray {
        val r = cornerRadiusPx
        val rr = radii ?: FloatArray(8) { r }
        return rr
    }

    private fun applyStyle() {
        val c = Theme.colors
        val effective = if (selectedTonal && style == M3ButtonStyle.FILLED) M3ButtonStyle.TONAL else style
        val bg = when (effective) {
            M3ButtonStyle.FILLED -> c.primary
            M3ButtonStyle.TONAL -> c.secondaryContainer
            M3ButtonStyle.ELEVATED -> c.surfaceContainerLow
            M3ButtonStyle.OUTLINED -> 0x00000000
            M3ButtonStyle.TEXT -> 0x00000000
        }
        val fg = when (effective) {
            M3ButtonStyle.FILLED -> c.onPrimary
            M3ButtonStyle.TONAL -> c.onSecondaryContainer
            M3ButtonStyle.ELEVATED -> c.primary
            M3ButtonStyle.OUTLINED -> c.primary
            M3ButtonStyle.TEXT -> c.primary
        }
        val outline = if (effective == M3ButtonStyle.OUTLINED) Pair(1f.dp.toFloat(), c.outline) else null
        val radii = cornerArray()
        val drawable = shape(bg, radii = radii, stroke = outline)
        // The mask needs its own instance: sharing it with the content drawable
        // makes the ripple and the background fight over the same constant state.
        val mask = shape(0xFFFFFFFF.toInt(), radii = radii)
        background = RippleDrawable(ColorStateList.valueOf(c.stateLayer(fg, 0x1F)), drawable, mask)
        elevation = if (effective == M3ButtonStyle.ELEVATED) 3f.dp else 0f
        labelView.setTextColor(fg)
        iconView.setTextColor(fg)
        setOnTouchListener(PressScaleTouchListener({ pressDepth() }, { UI.haptics }))
        val pad = scale.padH.dp
        setPadding(pad, 0, pad, 0)
        alpha = if (isEnabled) 1f else 0.38f
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.38f
    }
}

/** A circular Material 3 icon button (32/40/48/56/96/136dp). */
class M3IconButton @JvmOverloads constructor(
    context: Context,
    glyph: String,
    style: M3ButtonStyle = M3ButtonStyle.TEXT,
    var sizeDp: Int = 56,
    var cornerRadiusDp: Int? = null,
    var onAction: (() -> Unit)? = null
) : FrameLayout(context) {

    private var glyph: String = glyph
    private var style: M3ButtonStyle = style

    private val iconView = TextView(context)
    private var selected = false

    init {
        isClickable = true
        isFocusable = true
        iconView.apply {
            typeface = Icons.typeface(context)
            includeFontPadding = false
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
        }
        addView(iconView)
        iconView.text = glyph
        iconView.textSize = iconSizeFor(sizeDp)
        applyStyle()
        setOnClickListener { onAction?.invoke() }
    }

    fun setGlyph(g: String) {
        glyph = g
        iconView.text = g
    }

    fun setSelectedState(value: Boolean) {
        selected = value
        applyStyle()
    }

    fun setStyle(newStyle: M3ButtonStyle) {
        style = newStyle
        applyStyle()
    }

    private fun applyStyle() {
        val c = Theme.colors
        val effective = if (selected && style == M3ButtonStyle.TEXT) M3ButtonStyle.TONAL else style
        val bg = when (effective) {
            M3ButtonStyle.FILLED -> c.primary
            M3ButtonStyle.TONAL -> c.secondaryContainer
            M3ButtonStyle.ELEVATED -> c.surfaceContainerLow
            else -> 0x00000000
        }
        val fg = when (effective) {
            M3ButtonStyle.FILLED -> c.onPrimary
            M3ButtonStyle.TONAL -> c.onSecondaryContainer
            M3ButtonStyle.ELEVATED -> c.primary
            else -> if (selected) c.onSurface else c.onSurfaceVariant
        }
        val r = cornerRadiusDp?.dp?.toFloat() ?: (sizeDp.dp / 2f)
        val drawable = shape(bg, radius = r)
        val mask = shape(0xFFFFFFFF.toInt(), radius = r)
        background = RippleDrawable(ColorStateList.valueOf(c.stateLayer(fg, 0x1F)), drawable, mask)
        elevation = if (effective == M3ButtonStyle.ELEVATED) 3f.dp else 0f
        iconView.setTextColor(fg)
        setOnTouchListener(PressScaleTouchListener({ pressDepth() }, { UI.haptics }))
    }
}

private fun iconSizeFor(sizeDp: Int): Float = when {
    sizeDp <= 32 -> 16f
    sizeDp <= 40 -> 20f
    sizeDp <= 56 -> 24f
    sizeDp <= 96 -> 40f
    else -> 64f
}

/**
 * An M3 Expressive connected button group: a row with 3dp gaps where only the
 * inner adjoining corners shrink to 8dp and the outer corners stay pill round.
 */
fun connectedGroup(
    context: Context,
    buttons: List<M3Button>,
    gapDp: Int = 3,
    equalWidth: Boolean = true
): LinearLayout {
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    val inner = 8f.dp
    buttons.forEachIndexed { index, button ->
        val first = index == 0
        val last = index == buttons.size - 1
        val outer = button.sizeDp.dp / 2f
        val radii = floatArrayOf(
            if (first) outer else inner, if (first) outer else inner,   // top-left
            if (last) outer else inner, if (last) outer else inner,     // top-right
            if (last) outer else inner, if (last) outer else inner,     // bottom-right
            if (first) outer else inner, if (first) outer else inner    // bottom-left
        )
        if (buttons.size == 1) {
            button.setCorners(FloatArray(8) { outer })
        } else {
            button.setCorners(radii)
        }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (!equalWidth) lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.marginEnd = if (index < buttons.size - 1) gapDp.dp else 0
        button.layoutParams = lp
        row.addView(button)
    }
    return row
}

/** One entry of a floating toolbar. */
data class ToolbarAction(
    val icon: String,
    val description: String,
    val action: () -> Unit
)

/**
 * The M3 Expressive horizontal floating toolbar: 64dp tall, fully rounded,
 * [surfaceContainer] (or [primaryContainer] when vibrant), 48dp icon buttons.
 */
class FloatingToolbar @JvmOverloads constructor(
    context: Context,
    val vibrant: Boolean = false
) : LinearLayout(context) {

    private val buttons = mutableListOf<M3IconButton>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(8.dp, 0, 8.dp, 0)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, 64.dp)
        restyle()
    }

    fun restyle() {
        val c = Theme.colors
        val bg = if (vibrant) c.primaryContainer else c.surfaceContainer
        val drawable = shape(bg, null).apply { cornerRadius = 32f.dp }
        background = drawable
        elevation = 3f.dp
        buttons.forEach { it.setStyle(M3ButtonStyle.TEXT) }
    }

    fun action(icon: String, description: String, action: () -> Unit): M3IconButton {
        val button = M3IconButton(context, icon, M3ButtonStyle.TEXT, 48) { action() }
        button.contentDescription = description
        val lp = LayoutParams(48.dp, 48.dp).apply {
            marginStart = if (buttons.isEmpty()) 0 else 4.dp
        }
        button.layoutParams = lp
        buttons.add(button)
        addView(button)
        return button
    }

    /** Number of icon buttons in the toolbar. */
    val size: Int get() = buttons.size

    operator fun get(index: Int): M3IconButton = buttons[index]
}

/** A 1dp [outlineVariant] divider with 16dp horizontal insets. */
fun m3Divider(context: Context, insetDp: Int = 16, verticalMarginDp: Int = 0): View {
    val c = Theme.colors
    return View(context).apply {
        setBackgroundColor(c.outlineVariant)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, max(1, 1.dp)
        ).apply {
            marginStart = insetDp.dp
            marginEnd = insetDp.dp
            topMargin = verticalMarginDp.dp
            bottomMargin = verticalMarginDp.dp
        }
    }
}

/** A plain rounded container with a colour-role background. */
fun surfaceBox(context: Context, radiusDp: Int, color: Int, elevationDp: Int = 0): FrameLayout =
    FrameLayout(context).apply {
        background = shape(color, null).apply { cornerRadius = radiusDp.dp.toFloat() }
        elevation = elevationDp.dp.toFloat()
    }

/** Text with a colour role and a type style. */
fun m3Text(
    context: Context,
    text: String,
    style: com.flexteam.m3ecalc.theme.TextStyle,
    color: Int,
    gravity: Int = Gravity.START
): TextView = TextView(context).apply {
    this.text = text
    applyStyle(style)
    setTextColor(color)
    this.gravity = gravity
}
