package com.flexteam.m3ecalc.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.flexteam.m3ecalc.theme.Motion
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.Type
import com.flexteam.m3ecalc.theme.applyStyle
import com.flexteam.m3ecalc.theme.asTimeInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A Material 3 top app bar: 64dp on [surface], background extended behind the
 * status bar, [titleLarge] title, 48dp icon buttons on each side.
 */
class M3TopBar @JvmOverloads constructor(
    context: Context,
    title: String,
    private val onBack: (() -> Unit)? = null
) : LinearLayout(context) {

    private val row = LinearLayout(context)
    private val titleView = TextView(context)
    private val actions = LinearLayout(context)
    private val statusBarSpacer = View(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.colors.surface)
        statusBarSpacer.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0)
        addView(statusBarSpacer)

        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 64.dp)
        if (onBack != null) {
            val back = M3IconButton(context, Icons.ARROW_BACK, M3ButtonStyle.TEXT, 48) { onBack?.invoke() }
            back.contentDescription = context.getString(com.flexteam.m3ecalc.R.string.cd_back)
            row.addView(back, LayoutParams(48.dp, 48.dp).apply { marginStart = 4.dp })
        } else {
            row.addView(View(context), LayoutParams(16.dp, 16.dp))
        }
        titleView.apply {
            text = title
            applyStyle(Type.titleLarge)
            setTextColor(Theme.colors.onSurface)
            maxLines = 1
        }
        row.addView(
            titleView,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 16.dp }
        )
        actions.orientation = HORIZONTAL
        actions.gravity = Gravity.CENTER_VERTICAL
        row.addView(actions, LayoutParams(LayoutParams.WRAP_CONTENT, 48.dp))
        addView(row)
    }

    fun setTitle(value: String) {
        titleView.text = value
    }

    fun addAction(icon: String, description: String, action: () -> Unit): M3IconButton {
        val button = M3IconButton(context, icon, M3ButtonStyle.TEXT, 48) { action() }
        button.contentDescription = description
        actions.addView(button, LayoutParams(48.dp, 48.dp).apply { marginEnd = 4.dp })
        return button
    }

    /** Extends the bar behind the status bar. */
    fun applyTopInset(insetPx: Int) {
        statusBarSpacer.layoutParams = statusBarSpacer.layoutParams.apply { height = insetPx }
        statusBarSpacer.requestLayout()
    }
}

/**
 * An M3 Expressive slider: 16dp thick track with a tall 4 x 44dp handle,
 * [primary] on the left of the handle and [secondaryContainer] on the right.
 */
class M3Slider @JvmOverloads constructor(
    context: Context,
    var valueFrom: Float = 0f,
    var valueTo: Float = 100f,
    value: Float = 40f,
    var step: Float = 0f,
    var onValueChange: ((Float) -> Unit)? = null,
    var onValueChangeFinished: (() -> Unit)? = null
) : View(context) {

    var value: Float = value.coerceIn(valueFrom, valueTo)
        set(v) {
            val snapped = snap(v.coerceIn(valueFrom, valueTo))
            if (field != snapped) {
                field = snapped
                invalidate()
            }
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var dragging = false

    private fun snap(v: Float): Float =
        if (step <= 0f) v
        else valueFrom + (((v - valueFrom) / step).roundToInt() * step)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            44.dp + 12.dp
        )
    }

    override fun onDraw(canvas: Canvas) {
        val c = Theme.colors
        val centerY = height / 2f
        val trackHeight = 16f.dp
        val left = 8f.dp
        val right = width - 8f.dp
        val fraction = if (valueTo == valueFrom) 0f else (value - valueFrom) / (valueTo - valueFrom)
        val handleX = left + fraction * (right - left)

        // Inactive track
        trackPaint.color = c.secondaryContainer
        rect.set(handleX, centerY - trackHeight / 2, right, centerY + trackHeight / 2)
        canvas.drawRoundRect(rect, trackHeight / 2, trackHeight / 2, trackPaint)

        // Active track
        trackPaint.color = c.primary
        rect.set(left, centerY - trackHeight / 2, handleX, centerY + trackHeight / 2)
        canvas.drawRoundRect(rect, trackHeight / 2, trackHeight / 2, trackPaint)

        // Tall handle (4 x 44dp)
        handlePaint.color = c.primary
        val handleHeight = if (dragging) 44f.dp else 36f.dp
        val handleWidth = 4f.dp
        rect.set(
            handleX - handleWidth / 2, centerY - handleHeight / 2,
            handleX + handleWidth / 2, centerY + handleHeight / 2
        )
        canvas.drawRoundRect(rect, handleWidth / 2, handleWidth / 2, handlePaint)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val left = 8f.dp
        val right = width - 8f.dp
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x, left, right)
            }
            MotionEvent.ACTION_MOVE -> if (dragging) update(event.x, left, right)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    onValueChangeFinished?.invoke()
                }
            }
        }
        return true
    }

    private fun update(x: Float, left: Float, right: Float) {
        val span = max(1f, right - left)
        val fraction = ((x - left) / span).coerceIn(0f, 1f)
        val next = snap(valueFrom + fraction * (valueTo - valueFrom))
        if (next != value) {
            value = next
            onValueChange?.invoke(next)
        }
        invalidate()
    }
}

/** A Material 3 switch: 52 x 32dp track, thumb that grows when checked. */
class M3Switch @JvmOverloads constructor(
    context: Context,
    initialChecked: Boolean = false,
    var onChanged: ((Boolean) -> Unit)? = null
) : View(context) {

    var checked: Boolean = initialChecked
        set(v) {
            if (field != v) {
                field = v
                animateThumb()
                onChanged?.invoke(v)
            }
        }

    /** Animated 0..1 thumb position, driven by the expressive spring. */
    private var thumbFraction = if (initialChecked) 1f else 0f
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { checked = !checked }
    }

    private var thumbAnimator: android.animation.ValueAnimator? = null

    private fun animateThumb() {
        thumbAnimator?.cancel()
        val animator = android.animation.ValueAnimator.ofFloat(thumbFraction, if (checked) 1f else 0f)
        animator.duration = Motion.mediumMillis
        animator.interpolator = Motion.expressive
        animator.addUpdateListener {
            thumbFraction = it.animatedValue as Float
            invalidate()
        }
        animator.start()
        thumbAnimator = animator
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(52.dp, 32.dp)
    }

    override fun onDraw(canvas: Canvas) {
        val c = Theme.colors
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2

        trackPaint.color = if (checked) c.primary else c.surfaceContainerHighest
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        if (!checked) {
            outlinePaint.color = c.outline
            outlinePaint.strokeWidth = 2f.dp
            rect.set(1f.dp, 1f.dp, w - 1f.dp, h - 1f.dp)
            canvas.drawRoundRect(rect, radius, radius - 1f.dp, outlinePaint)
        }

        val thumbDiameter = (16f + 8f * thumbFraction) * UI.density
        val travel = w - thumbDiameter
        val cx = thumbDiameter / 2 + travel * thumbFraction
        thumbPaint.color = if (checked) c.onPrimary else c.outline
        canvas.drawCircle(cx, h / 2, thumbDiameter / 2, thumbPaint)
    }
}

/** A pill shaped search field used by the History screen. */
class SearchField @JvmOverloads constructor(
    context: Context,
    hint: String,
    var onQuery: ((String) -> Unit)? = null
) : FrameLayout(context) {

    val input = EditText(context)
    private val clearButton = M3IconButton(context, Icons.CLOSE, M3ButtonStyle.TEXT, 40) {
        input.setText("")
        onQuery?.invoke("")
    }

    init {
        val c = Theme.colors
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(c.surfaceContainerHigh)
            cornerRadius = 28f.dp
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 56.dp)
            setPadding(16.dp, 0, 8.dp, 0)
        }
        val icon = TextView(context).apply {
            typeface = Icons.typeface(context)
            text = Icons.SEARCH
            textSize = 22f
            setTextColor(c.onSurfaceVariant)
            includeFontPadding = false
        }
        row.addView(icon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 12.dp })

        clearButton.apply {
            contentDescription = context.getString(com.flexteam.m3ecalc.R.string.cd_clear)
            visibility = View.GONE
        }

        input.apply {
            this.hint = hint
            isSingleLine = true
            setTextColor(c.onSurface)
            setHintTextColor(c.onSurfaceVariant)
            applyStyle(Type.bodyLarge)
            background = null
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    onQuery?.invoke(s?.toString() ?: "")
                    clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
            })
        }
        row.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(clearButton, LinearLayout.LayoutParams(40.dp, 40.dp))
        addView(row)
    }

    fun setText(value: String) {
        input.setText(value)
        input.setSelection(value.length)
    }
}

/** An M3 snack bar: [inverseSurface] pill, slides up with the expressive spring. */
object Snack {

    private var current: View? = null

    fun show(
        host: ViewGroup,
        message: String,
        actionLabel: String? = null,
        durationMillis: Long = 4500,
        action: (() -> Unit)? = null
    ) {
        // Float over everything, whatever kind of layout the screen is built from.
        val target = UI.overlayHost ?: host
        current?.let { target.removeView(it) }
        val c = Theme.colors
        val context = host.context
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 12.dp, 8.dp, 12.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(c.inverseSurface)
                cornerRadius = 16f.dp
            }
            elevation = 6f.dp
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            ).apply {
                marginStart = 16.dp
                marginEnd = 16.dp
                bottomMargin = 24.dp
            }
        }
        val label = TextView(context).apply {
            text = message
            applyStyle(Type.bodyMedium)
            setTextColor(c.inverseOnSurface)
            maxLines = 2
        }
        pill.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (actionLabel != null && action != null) {
            val button = M3Button(context, actionLabel, M3ButtonStyle.TEXT, sizeDp = 40) {
                action()
                dismiss(host, pill)
            }
            pill.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        current = pill
        target.addView(pill)
        pill.translationY = 96f.dp
        pill.alpha = 0f
        pill.animate().translationY(0f).alpha(1f)
            .setDuration(Motion.mediumMillis)
            .setInterpolator(Motion.expressive.asTimeInterpolator()).start()
        pill.postDelayed({ dismiss(host, pill) }, durationMillis)
    }

    private fun dismiss(@Suppress("UNUSED_PARAMETER") host: ViewGroup, view: View) {
        if (current !== view) return
        current = null
        view.animate().translationY(96f.dp).alpha(0f).setDuration(Motion.shortMillis)
            .setInterpolator(Motion.decelerate.asTimeInterpolator())
            .withEndAction { (view.parent as? ViewGroup)?.removeView(view) }.start()
    }
}

/**
 * A Material 3 basic dialog: 28dp corners, [surfaceContainerHigh], headline
 * title, body copy and right aligned text buttons.
 */
class M3Dialog(
    private val context: Context,
    private val title: String,
    private val message: String? = null
) {

    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val buttons = mutableListOf<M3Button>()
    private var custom: View? = null
    private var dialog: AlertDialog? = null

    fun content(view: View): M3Dialog {
        custom = view
        return this
    }

    fun button(
        label: String,
        style: M3ButtonStyle = M3ButtonStyle.TEXT,
        dismissOnClick: Boolean = true,
        action: () -> Unit = {}
    ): M3Dialog {
        buttons.add(M3Button(context, label, style, sizeDp = 40) {
            action()
            if (dismissOnClick) dialog?.dismiss()
        })
        return this
    }

    fun show(): AlertDialog {
        val c = Theme.colors
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 24.dp, 24.dp, 16.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(c.surfaceContainerHigh)
                cornerRadius = 28f.dp
            }
        }
        root.addView(TextView(context).apply {
            text = title
            applyStyle(Type.headlineSmall)
            setTextColor(c.onSurface)
        })
        if (message != null) {
            root.addView(TextView(context).apply {
                text = message
                applyStyle(Type.bodyMedium)
                setTextColor(c.onSurfaceVariant)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp })
        }
        custom?.let {
            root.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp })
        }
        if (buttons.isNotEmpty()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            buttons.forEach { button ->
                row.addView(button, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = 8.dp })
            }
            root.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24.dp })
        }

        val built = AlertDialog.Builder(context)
            .setView(root)
            .create()
        built.window?.setBackgroundDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0x00000000)
            cornerRadius = 28f.dp
        })
        built.window?.setLayout(
            min(
                (context.resources.displayMetrics.widthPixels * 0.86f).toInt(),
                376.dp
            ),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog = built
        built.show()
        return built
    }

    fun dismiss() = dialog?.dismiss()
}

/** A destructive-action confirmation with a snackbar-friendly label. */
fun confirmDialog(
    context: Context,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit
) {
    M3Dialog(context, title, message)
        .button(context.getString(com.flexteam.m3ecalc.R.string.cancel))
        .button(confirmLabel, M3ButtonStyle.FILLED) { onConfirm() }
        .show()
}

/** A single-line text prompt (used to edit a conversion rate). */
fun promptDialog(
    context: Context,
    title: String,
    message: String? = null,
    initial: String = "",
    numeric: Boolean = false,
    confirmLabel: String = "Save",
    onConfirm: (String) -> Unit
) {
    val input = EditText(context).apply {
        setText(initial)
        setSelection(initial.length)
        isSingleLine = true
        applyStyle(Type.bodyLarge)
        setTextColor(Theme.colors.onSurface)
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Theme.colors.surfaceContainerHighest)
            cornerRadius = 12f.dp
        }
        setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        setSelection(length())
    }
    M3Dialog(context, title, message)
        .content(input)
        .button(context.getString(com.flexteam.m3ecalc.R.string.cancel))
        .button(confirmLabel, M3ButtonStyle.FILLED) {
            onConfirm(input.text.toString().trim())
        }
        .show()
}
