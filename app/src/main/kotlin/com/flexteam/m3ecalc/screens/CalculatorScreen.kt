package com.flexteam.m3ecalc.screens

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.flexteam.m3ecalc.App
import com.flexteam.m3ecalc.Direction
import com.flexteam.m3ecalc.Nav
import com.flexteam.m3ecalc.R
import com.flexteam.m3ecalc.Screen
import com.flexteam.m3ecalc.calc.CalcException
import com.flexteam.m3ecalc.calc.CalcOptions
import com.flexteam.m3ecalc.calc.Calculator
import com.flexteam.m3ecalc.calc.Formatter
import com.flexteam.m3ecalc.theme.Motion
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.Type
import com.flexteam.m3ecalc.theme.applyStyle
import com.flexteam.m3ecalc.theme.asTimeInterpolator
import com.flexteam.m3ecalc.ui.FloatingToolbar
import com.flexteam.m3ecalc.ui.Icons
import com.flexteam.m3ecalc.ui.M3Button
import com.flexteam.m3ecalc.ui.M3ButtonStyle
import com.flexteam.m3ecalc.ui.M3IconButton
import com.flexteam.m3ecalc.ui.Snack
import com.flexteam.m3ecalc.ui.connectedGroup
import com.flexteam.m3ecalc.ui.dp
import com.flexteam.m3ecalc.ui.surfaceBox
import kotlin.math.max
import kotlin.math.min

/**
 * Home: the calculator.
 *
 * A calculation surface (the rounded box), a floating toolbar row that opens
 * the other destinations, and the keypad. The `calculate` toolbar entry opens
 * the advanced panel with the scientific functions.
 */
class CalculatorScreen(context: Context, nav: Nav) : Screen(context, nav) {

    private val expression = StringBuilder()
    private var ans: Double? = null
    private var lastResult: String? = null
    private var evaluated = false

    private lateinit var root: FrameLayout
    private lateinit var expressionView: TextView
    private lateinit var resultView: TextView
    private lateinit var advancedPanel: FrameLayout
    private lateinit var advancedToggle: M3IconButton
    private lateinit var keypadColumn: LinearLayout
    private var advancedOpen = false
    private var bottomPadding = 0

    private val options: CalcOptions
        get() = CalcOptions(
            angleUnit = App.settings.angleUnit,
            precision = App.settings.precision,
            thousandsSeparator = App.settings.thousandsSeparator
        )

    override fun content(): View {
        root = FrameLayout(context)
        root.setBackgroundColor(Theme.colors.surface)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 0, 16.dp, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        column.addView(buildDisplay())
        column.addView(buildToolbarRow())
        column.addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = 8.dp })
        keypadColumn = buildKeypad()
        column.addView(keypadColumn)
        root.addView(column)

        advancedPanel = buildAdvancedPanel()
        advancedPanel.visibility = View.GONE
        root.addView(advancedPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))

        refresh()
        return root
    }

    override fun applyInsets(top: Int, bottom: Int, ime: Int) {
        if (!::root.isInitialized) return
        root.setPadding(0, top + 12.dp, 0, max(bottom, ime) + 12.dp)
    }

    // -------------------------------------------------------------- surfaces

    private fun buildDisplay(): View {
        val c = Theme.colors
        val box = surfaceBox(context, 48, c.surfaceContainerHigh)
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28.dp, 24.dp, 28.dp, 24.dp)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 200.dp
            )
        }

        expressionView = TextView(context).apply {
            applyStyle(Type.bodyLarge)
            setTextColor(c.onSurfaceVariant)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            setSingleLine(true)
            contentDescription = context.getString(R.string.cd_expression)
        }
        inner.addView(expressionView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val divider = View(context).apply { setBackgroundColor(c.outlineVariant) }
        inner.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, max(1, 1.dp)
        ).apply { topMargin = 4.dp; bottomMargin = 4.dp })

        resultView = TextView(context).apply {
            setTextColor(c.onSurface)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            setSingleLine(true)
            typeface = Typeface.create(Typeface.DEFAULT, 500, false)
            contentDescription = context.getString(R.string.cd_result)
        }
        inner.addView(resultView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.6f
        ))

        box.addView(inner)
        return box
    }

    private fun buildToolbarRow(): LinearLayout {
        val toolbar = FloatingToolbar(context)
        toolbar.action(Icons.HISTORY_2, context.getString(R.string.nav_history)) {
            nav.push(HistoryScreen(context, nav), Direction.LEFT)
        }
        toolbar.action(Icons.STRAIGHTEN, context.getString(R.string.nav_converter)) {
            nav.push(ConverterScreen(context, nav), Direction.RIGHT)
        }
        advancedToggle = toolbar.action(Icons.CALCULATE, context.getString(R.string.nav_advanced)) {
            toggleAdvanced()
        }
        toolbar.action(Icons.SETTINGS, context.getString(R.string.nav_settings)) {
            nav.push(SettingsScreen(context, nav), Direction.RIGHT)
        }
        toolbar.action(Icons.INFO, context.getString(R.string.nav_about)) {
            nav.push(AboutScreen(context, nav), Direction.RIGHT)
        }

        val backspace = M3IconButton(context, Icons.BACKSPACE, M3ButtonStyle.TONAL, 64) {
            backspace()
        }
        backspace.contentDescription = context.getString(R.string.cd_backspace)
        backspace.setOnLongClickListener {
            clearAll()
            true
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 64.dp
            ).apply { topMargin = 16.dp }
        }
        row.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 64.dp
        ))
        row.addView(backspace, LinearLayout.LayoutParams(0, 64.dp, 1f).apply { marginStart = 8.dp })
        return row
    }

    private fun buildKeypad(): LinearLayout {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(
            listOf(
                key("C", M3ButtonStyle.ELEVATED) { clearAll() },
                key("( )", M3ButtonStyle.ELEVATED) { insertParenthesis() },
                key("%", M3ButtonStyle.ELEVATED) { insert("%") },
                key("\u00F7", M3ButtonStyle.ELEVATED) { operator("\u00F7") }
            ),
            listOf(
                key("7", M3ButtonStyle.TONAL) { digit("7") },
                key("8", M3ButtonStyle.TONAL) { digit("8") },
                key("9", M3ButtonStyle.TONAL) { digit("9") },
                key("\u00D7", M3ButtonStyle.ELEVATED) { operator("\u00D7") }
            ),
            listOf(
                key("4", M3ButtonStyle.TONAL) { digit("4") },
                key("5", M3ButtonStyle.TONAL) { digit("5") },
                key("6", M3ButtonStyle.TONAL) { digit("6") },
                key("\u2212", M3ButtonStyle.ELEVATED) { operator("\u2212") }
            ),
            listOf(
                key("1", M3ButtonStyle.TONAL) { digit("1") },
                key("2", M3ButtonStyle.TONAL) { digit("2") },
                key("3", M3ButtonStyle.TONAL) { digit("3") },
                key("+", M3ButtonStyle.ELEVATED) { operator("+") }
            ),
            listOf(
                key("+/\u2212", M3ButtonStyle.TONAL) { negate() },
                key("0", M3ButtonStyle.TONAL) { digit("0") },
                key(".", M3ButtonStyle.TONAL) { decimalPoint() },
                key("=", M3ButtonStyle.FILLED) { equals() }
            )
        )
        rows.forEachIndexed { index, rowButtons ->
            val group = connectedGroup(context, rowButtons)
            column.addView(group, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) topMargin = 8.dp })
        }
        return column
    }

    private fun key(label: String, style: M3ButtonStyle, action: () -> Unit): M3Button =
        M3Button(context, label, style, sizeDp = 56) { action() }

    // -------------------------------------------------------- advanced panel

    private fun buildAdvancedPanel(): FrameLayout {
        val c = Theme.colors
        val panel = FrameLayout(context)
        val card = surfaceBox(context, 28, c.surfaceContainerHigh, elevationDp = 6)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 12.dp, 16.dp, 16.dp)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = context.getString(R.string.advanced_title)
            applyStyle(Type.titleMedium)
            setTextColor(c.onSurface)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val angleToggle = M3Button(
            context, App.settings.angleUnit.name, M3ButtonStyle.TONAL, sizeDp = 40
        ) {}
        angleToggle.onAction = {
            val next = if (App.settings.angleUnit == com.flexteam.m3ecalc.calc.AngleUnit.DEG) {
                com.flexteam.m3ecalc.calc.AngleUnit.RAD
            } else {
                com.flexteam.m3ecalc.calc.AngleUnit.DEG
            }
            App.settings.angleUnit = next
            angleToggle.setLabel(next.name)
            refresh()
        }
        header.addView(angleToggle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val close = M3IconButton(context, Icons.CLOSE, M3ButtonStyle.TEXT, 40) { toggleAdvanced() }
        close.contentDescription = context.getString(R.string.cd_close)
        header.addView(close, LinearLayout.LayoutParams(40.dp, 40.dp).apply { marginStart = 4.dp })
        column.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12.dp })

        val rows = listOf(
            listOf("sin(" to { insert("sin(") }, "cos(" to { insert("cos(") },
                "tan(" to { insert("tan(") }, "\u03C0" to { insert("\u03C0") }),
            listOf("asin(" to { insert("asin(") }, "acos(" to { insert("acos(") },
                "atan(" to { insert("atan(") }, "e" to { insert("e") }),
            listOf("ln(" to { insert("ln(") }, "log(" to { insert("log(") },
                "\u221A" to { insert("\u221A") }, "^" to { insert("^") }),
            listOf("x\u00B2" to { insert("^2") }, "1/x" to { insert("inv(") },
                "|x|" to { insert("abs(") }, "n!" to { insert("!") }),
            listOf("(" to { insert("(") }, ")" to { insert(")") },
                "mod" to { insert(" mod ") }, "Ans" to { insert("Ans") })
        )
        rows.forEachIndexed { index, row ->
            val buttons = row.map { (label, action) ->
                M3Button(context, label, M3ButtonStyle.ELEVATED, sizeDp = 48) { action() }
            }
            column.addView(connectedGroup(context, buttons), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) topMargin = 6.dp })
        }

        card.addView(column)
        panel.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = 16.dp; marginEnd = 16.dp; bottomMargin = 8.dp })
        return panel
    }

    private fun toggleAdvanced() {
        advancedOpen = !advancedOpen
        advancedToggle.setGlyph(if (advancedOpen) Icons.CLOSE else Icons.CALCULATE)
        if (advancedOpen) {
            advancedPanel.visibility = View.VISIBLE
            advancedPanel.translationY = 400f.dp
            advancedPanel.alpha = 0f
            advancedPanel.animate().translationY(0f).alpha(1f)
                .setDuration(Motion.mediumMillis)
                .setInterpolator(Motion.expressive.asTimeInterpolator()).start()
            keypadColumn.alpha = 0.35f
            keypadColumn.setOnTouchListener { _, _ -> true }
        } else {
            advancedPanel.animate().translationY(400f.dp).alpha(0f)
                .setDuration(Motion.shortMillis)
                .setInterpolator(Motion.decelerate.asTimeInterpolator())
                .withEndAction { advancedPanel.visibility = View.GONE }.start()
            keypadColumn.alpha = 1f
            keypadColumn.setOnTouchListener(null)
        }
    }

    // ----------------------------------------------------------------- input

    private fun digit(value: String) {
        if (evaluated) {
            expression.setLength(0)
            evaluated = false
        }
        expression.append(value)
        refresh()
    }

    private fun decimalPoint() {
        if (evaluated) {
            expression.setLength(0)
            evaluated = false
        }
        if (currentNumberContainsDot()) return
        if (expression.isEmpty() || !lastCharIsDigit()) expression.append("0")
        expression.append(".")
        refresh()
    }

    private fun operator(symbol: String) {
        evaluated = false
        if (expression.isEmpty()) {
            // A leading minus is a sign, anything else is ignored.
            if (symbol == "\u2212") expression.append(symbol)
            refresh()
            return
        }
        val last = expression.last().toString()
        if (last == "+" || last == "\u2212" || last == "\u00D7" || last == "\u00F7" || last == "^") {
            expression.setLength(expression.length - 1)
        }
        expression.append(symbol)
        refresh()
    }

    private fun insert(text: String) {
        if (evaluated) {
            // Anything starting with an operator continues from the answer;
            // a function name or a digit starts a new expression.
            val continues = !text.first().isLetter()
            val carry = lastResult?.replace("\u2009", "") ?: ""
            expression.setLength(0)
            if (continues) expression.append(carry)
            evaluated = false
        }
        expression.append(text)
        refresh()
    }

    private fun insertParenthesis() {
        if (evaluated) {
            expression.setLength(0)
            evaluated = false
        }
        val open = expression.count { it == '(' }
        val close = expression.count { it == ')' }
        val last = if (expression.isEmpty()) ' ' else expression.last()
        val shouldOpen = open == close || last == '(' || "+\u2212\u00D7\u00F7^(".contains(last)
        expression.append(if (shouldOpen) "(" else ")")
        refresh()
    }

    private fun negate() {
        if (expression.isEmpty()) {
            expression.append("\u2212")
            refresh()
            return
        }
        // Toggle the sign of the number currently being typed.
        var index = expression.length - 1
        while (index >= 0 && (expression[index].isDigit() || expression[index] == '.')) index--
        val start = index + 1
        if (start < expression.length) {
            if (index >= 0 && expression[index] == '\u2212') {
                expression.deleteCharAt(index)
            } else {
                expression.insert(start, "\u2212")
            }
        } else {
            expression.insert(0, "\u2212")
        }
        refresh()
    }

    private fun backspace() {
        if (evaluated) {
            expression.setLength(0)
            evaluated = false
            lastResult = null
            refresh()
            return
        }
        if (expression.isNotEmpty()) {
            // Delete a whole function name in one go.
            var cut = 1
            if (expression.last() == '(') {
                var i = expression.length - 2
                while (i >= 0 && expression[i].isLetter()) i--
                cut = expression.length - 1 - i
            }
            repeat(min(cut, expression.length)) { expression.deleteCharAt(expression.length - 1) }
        }
        refresh()
    }

    private fun clearAll() {
        expression.setLength(0)
        lastResult = null
        evaluated = false
        refresh()
    }

    private fun equals() {
        val typed = expression.toString()
        if (typed.isBlank()) return
        // +\ is what the live preview already shows as 5, so evaluate the
        // completed form rather than telling the user it is wrong.
        val text = (Calculator.complete(typed) ?: typed).trim()
        val result = try {
            val value = Calculator.evaluate(text, options, ans)
            ans = value
            Formatter.format(value, options)
        } catch (error: CalcException) {
            showError(error.message ?: context.getString(R.string.error_generic))
            return
        }
        lastResult = result
        evaluated = true
        if (text != typed.trim()) {
            expression.setLength(0)
            expression.append(text)
        }
        App.db.addCalculation(
            expression = text,
            result = result,
            limit = App.settings.historyLimit
        )
        refresh()
    }

    private fun refresh() {
        val text = expression.toString()
        expressionView.text = when {
            evaluated -> "$text ="
            else -> text
        }
        expressionView.setTextColor(Theme.colors.onSurfaceVariant)

        val shown = if (evaluated) {
            lastResult
        } else {
            Calculator.preview(text, options, ans)
        }
        resultView.setTextColor(Theme.colors.onSurface)
        if (shown == null) {
            resultView.text = if (evaluated) lastResult ?: "" else ""
            resultView.textSize = 36f
        } else {
            resultView.text = shown
            resultView.textSize = textSizeFor(shown.length)
        }
    }

    private fun showError(message: String) {
        resultView.text = message
        resultView.setTextColor(Theme.colors.error)
        resultView.textSize = textSizeFor(message.length)
        expressionView.setTextColor(Theme.colors.error)
        Snack.show(root, message)
    }

    private fun textSizeFor(length: Int): Float = when {
        length <= 9 -> 36f
        length <= 13 -> 30f
        length <= 18 -> 24f
        else -> 19f
    }

    private fun lastCharIsDigit(): Boolean =
        expression.isNotEmpty() && expression.last().isDigit()

    private fun currentNumberContainsDot(): Boolean {
        var i = expression.length - 1
        while (i >= 0 && (expression[i].isDigit() || expression[i] == '.')) {
            if (expression[i] == '.') return true
            i--
        }
        return false
    }

    /** Called when History (or the converter) hands a value back. */
    fun acceptResult(text: String) {
        expression.setLength(0)
        expression.append(text)
        evaluated = false
        refresh()
    }

    override fun onShow() {
        if (advancedOpen) toggleAdvanced()
        refresh()
    }
}
