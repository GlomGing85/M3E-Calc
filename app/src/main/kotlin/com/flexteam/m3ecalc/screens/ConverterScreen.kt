package com.flexteam.m3ecalc.screens

import android.content.Context
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import com.flexteam.m3ecalc.App
import com.flexteam.m3ecalc.Nav
import com.flexteam.m3ecalc.R
import com.flexteam.m3ecalc.Screen
import com.flexteam.m3ecalc.calc.CalcException
import com.flexteam.m3ecalc.calc.Calculator
import com.flexteam.m3ecalc.calc.Formatter
import com.flexteam.m3ecalc.data.Categories
import com.flexteam.m3ecalc.data.Conversion
import com.flexteam.m3ecalc.data.ConversionEntry
import com.flexteam.m3ecalc.data.ConverterUnit
import com.flexteam.m3ecalc.data.Rate
import com.flexteam.m3ecalc.data.Units
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.Type
import com.flexteam.m3ecalc.ui.FloatingToolbar
import com.flexteam.m3ecalc.ui.Icons
import com.flexteam.m3ecalc.ui.M3Button
import com.flexteam.m3ecalc.ui.M3ButtonStyle
import com.flexteam.m3ecalc.ui.M3Dialog
import com.flexteam.m3ecalc.ui.M3IconButton
import com.flexteam.m3ecalc.ui.M3TopBar
import com.flexteam.m3ecalc.ui.Snack
import com.flexteam.m3ecalc.ui.connectedGroup
import com.flexteam.m3ecalc.ui.dp
import com.flexteam.m3ecalc.ui.m3Text
import com.flexteam.m3ecalc.ui.makePressable
import com.flexteam.m3ecalc.ui.promptDialog
import com.flexteam.m3ecalc.ui.surfaceBox
import kotlin.math.max

/**
 * Converter. The same keypad as Home builds an expression (so `2×1024` works),
 * the value is evaluated and then converted between the units of the category
 * chosen on the bottom toolbar. Recent conversions are stored on the device.
 */
class ConverterScreen(context: Context, nav: Nav) : Screen(context, nav) {

    private val input = StringBuilder()
    private lateinit var root: LinearLayout
    private lateinit var toolbar: FloatingToolbar
    private lateinit var inputValue: TextView
    private lateinit var inputUnit: TextView
    private lateinit var outputValue: TextView
    private lateinit var outputUnit: TextView
    private lateinit var fromButton: M3Button
    private lateinit var toButton: M3Button
    private lateinit var categoryLabel: TextView
    private lateinit var hexRow: LinearLayout
    private val categoryButtons = mutableMapOf<String, M3IconButton>()

    private var category: String = App.settings.converterCategory
    private var rates: List<Rate> = App.db.rates()

    private var from: String = ""
    private var to: String = ""

    override fun content(): View {
        val c = Theme.colors
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.surface)
        }

        val bar = M3TopBar(context, context.getString(R.string.nav_converter)) { nav.pop() }
        val more = bar.addAction(Icons.MORE_VERT, context.getString(R.string.cd_more)) {}
        more.onAction = { showMenu(more) }
        root.addView(bar)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 8.dp, 16.dp, 0)
        }
        categoryLabel = m3Text(context, "", Type.titleMedium, c.primary)
        body.addView(categoryLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8.dp })
        body.addView(buildDisplay())
        body.addView(buildUnitRow())
        body.addView(View(context), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        hexRow = buildHexRow()
        body.addView(hexRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8.dp })
        body.addView(buildKeypad())
        body.addView(buildToolbar(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 64.dp
        ).apply { topMargin = 12.dp; gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        restoreUnits()
        render()
        return root
    }

    override fun applyInsets(top: Int, bottom: Int, ime: Int) {
        if (!::root.isInitialized) return
        (root.getChildAt(0) as M3TopBar).applyTopInset(top)
        root.setPadding(0, 0, 0, max(bottom, ime) + 12.dp)
    }

    private fun buildDisplay(): View {
        val c = Theme.colors
        val box = surfaceBox(context, 48, c.surfaceContainerHigh)
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28.dp, 16.dp, 28.dp, 16.dp)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 180.dp
            )
        }

        inputValue = TextView(context).apply {
            setTextColor(c.onSurface)
            textSize = 34f
            gravity = Gravity.END or Gravity.BOTTOM
            maxLines = 1
            setSingleLine(true)
            contentDescription = context.getString(R.string.converter_input)
        }
        inputUnit = m3Text(context, "", Type.labelLarge, c.onSurfaceVariant, Gravity.END)
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        top.addView(inputValue, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        top.addView(inputUnit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        inner.addView(top, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        inner.addView(View(context).apply { setBackgroundColor(c.outlineVariant) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, max(1, 1.dp))
                .apply { topMargin = 6.dp; bottomMargin = 6.dp })

        outputValue = TextView(context).apply {
            setTextColor(c.primary)
            textSize = 34f
            gravity = Gravity.END or Gravity.BOTTOM
            maxLines = 1
            setSingleLine(true)
            contentDescription = context.getString(R.string.converter_output)
        }
        outputUnit = m3Text(context, "", Type.labelLarge, c.onSurfaceVariant, Gravity.END)
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        bottom.addView(outputValue, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        bottom.addView(outputUnit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        inner.addView(bottom, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        box.addView(inner)
        box.contentDescription = context.getString(R.string.converter_edit_input)
        box.isClickable = true
        box.makePressable(c.stateLayer(c.onSurface, 0x1F))
        box.setOnClickListener { editInput() }
        return box
    }

    private fun buildUnitRow(): LinearLayout {
        val c = Theme.colors
        fromButton = M3Button(context, "", M3ButtonStyle.OUTLINED, sizeDp = 48) { pickUnit(true) }
        toButton = M3Button(context, "", M3ButtonStyle.OUTLINED, sizeDp = 48) { pickUnit(false) }
        val swap = M3IconButton(context, Icons.SWAP_VERT, M3ButtonStyle.TONAL, 48) {
            val swapFrom = from
            from = to
            to = swapFrom
            persistUnits()
            render()
        }
        swap.contentDescription = context.getString(R.string.converter_swap)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(fromButton, LinearLayout.LayoutParams(0, 48.dp, 1f))
        row.addView(swap, LinearLayout.LayoutParams(48.dp, 48.dp).apply {
            marginStart = 8.dp; marginEnd = 8.dp
        })
        row.addView(toButton, LinearLayout.LayoutParams(0, 48.dp, 1f))
        row.setBackgroundColor(c.surface)
        row.setPadding(0, 12.dp, 0, 12.dp)
        return row
    }

    /** A-F keys, shown only while the input base is hexadecimal. */
    private fun buildHexRow(): LinearLayout {
        val letters = listOf("A", "B", "C", "D", "E", "F").map { letter ->
            M3Button(context, letter, M3ButtonStyle.ELEVATED, sizeDp = 48) { append(letter) }
        }
        val row = connectedGroup(context, letters)
        row.visibility = View.GONE
        return row
    }

    private fun buildKeypad(): LinearLayout {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(
            listOf(
                key("C", M3ButtonStyle.ELEVATED) { clear() },
                key("( )", M3ButtonStyle.ELEVATED) { parenthesis() },
                key("%", M3ButtonStyle.ELEVATED) { append("%") },
                key("\u00F7", M3ButtonStyle.ELEVATED) { append("\u00F7") }
            ),
            listOf(
                key("7", M3ButtonStyle.TONAL) { append("7") },
                key("8", M3ButtonStyle.TONAL) { append("8") },
                key("9", M3ButtonStyle.TONAL) { append("9") },
                key("\u00D7", M3ButtonStyle.ELEVATED) { append("\u00D7") }
            ),
            listOf(
                key("4", M3ButtonStyle.TONAL) { append("4") },
                key("5", M3ButtonStyle.TONAL) { append("5") },
                key("6", M3ButtonStyle.TONAL) { append("6") },
                key("\u2212", M3ButtonStyle.ELEVATED) { append("\u2212") }
            ),
            listOf(
                key("1", M3ButtonStyle.TONAL) { append("1") },
                key("2", M3ButtonStyle.TONAL) { append("2") },
                key("3", M3ButtonStyle.TONAL) { append("3") },
                key("+", M3ButtonStyle.ELEVATED) { append("+") }
            ),
            listOf(
                key("+/\u2212", M3ButtonStyle.TONAL) { append("\u2212") },
                key("0", M3ButtonStyle.TONAL) { append("0") },
                key(".", M3ButtonStyle.TONAL) { append(".") },
                key("=", M3ButtonStyle.FILLED) { commit() }
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

    private fun key(label: String, style: M3ButtonStyle, action: () -> Unit): M3Button {
        val button = M3Button(context, label, style, sizeDp = 56) { action() }
        if (label == "C") {
            button.setOnLongClickListener {
                backspace()
                true
            }
        }
        return button
    }

    private fun buildToolbar(): FloatingToolbar {
        toolbar = FloatingToolbar(context)
        val entries = listOf(
            Categories.CURRENCY to Icons.CURRENCY_EXCHANGE,
            Categories.WEIGHT to Icons.WEIGHT,
            Categories.DATA to Icons.STORAGE,
            Categories.LENGTH to Icons.STRAIGHTEN,
            Categories.TEMPERATURE to Icons.DEVICE_THERMOSTAT,
            Categories.BASE to Icons.DEPLOYED_CODE
        )
        entries.forEach { (id, icon) ->
            val button = toolbar.action(icon, Categories.title(id)) {
                selectCategory(id)
            }
            categoryButtons[id] = button
        }
        return toolbar
    }

    private fun selectCategory(id: String) {
        if (category == id) return
        category = id
        App.settings.converterCategory = id
        rates = App.db.rates()
        restoreUnits(force = true)
        render()
    }

    private fun restoreUnits(force: Boolean = false) {
        val available = Units.units(category, rates)
        if (available.isEmpty()) return
        val saved = App.settings.units(category)
        from = saved?.first?.takeIf { id -> available.any { it.id == id } } ?: available[0].id
        to = saved?.second?.takeIf { id -> available.any { it.id == id } }
            ?: available[minOf(1, available.size - 1)].id
        if (force) persistUnits()
    }

    private fun persistUnits() = App.settings.setUnits(category, from, to)

    private fun render() {
        val c = Theme.colors
        categoryLabel.text = Categories.title(category)
        categoryButtons.forEach { (id, button) ->
            button.setSelectedState(id == category)
        }

        val available = Units.units(category, rates)
        if (::hexRow.isInitialized) {
            hexRow.visibility =
                if (category == Categories.BASE && from.equals("HEX", true)) View.VISIBLE else View.GONE
        }
        fromButton.setLabel(available.firstOrNull { it.id == from }?.id ?: from)
        toButton.setLabel(available.firstOrNull { it.id == to }?.id ?: to)
        inputUnit.text = Units.label(category, from, rates)
        outputUnit.text = Units.label(category, to, rates)
        inputValue.text = if (input.isEmpty()) "0" else input.toString()

        val result = convertNow()
        when (result) {
            is Conversion.Value -> {
                outputValue.setTextColor(c.primary)
                outputValue.text = Formatter.conversion(
                    result.value, App.settings.precision, App.settings.thousandsSeparator
                )
                outputValue.textSize = textSizeFor(outputValue.text.length)
            }
            is Conversion.Text -> {
                outputValue.setTextColor(c.primary)
                outputValue.text = result.text
                outputValue.textSize = textSizeFor(result.text.length)
            }
            is Conversion.Failed -> {
                outputValue.setTextColor(if (result.message.isEmpty()) c.onSurfaceVariant else c.error)
                outputValue.text = result.message.ifEmpty { "\u2014" }
                outputValue.textSize = textSizeFor(outputValue.text.length)
            }
        }
    }

    private fun textSizeFor(length: Int): Float = when {
        length <= 9 -> 34f
        length <= 14 -> 26f
        length <= 20 -> 20f
        else -> 16f
    }

    private fun convertNow(): Conversion {
        if (input.isEmpty()) return Conversion.Failed("")
        // A number base is a radix change: the digits are read in the source
        // base, so the expression engine stays out of it.
        if (category == Categories.BASE) {
            return Units.convert(category, from, to, input.toString(), rates)
        }
        val value = try {
            Calculator.evaluate(input.toString())
        } catch (error: CalcException) {
            return Conversion.Failed(error.message ?: context.getString(R.string.error_generic))
        }
        return Units.convert(
            category, from, to,
            value.toBigDecimal().toPlainString(), rates
        )
    }

    private fun append(text: String) {
        if (text == "." && currentNumberHasDot()) return
        input.append(text)
        render()
    }

    private fun parenthesis() {
        val open = input.count { it == '(' }
        val close = input.count { it == ')' }
        val last = if (input.isEmpty()) ' ' else input.last()
        val shouldOpen = open == close || last == '(' || "+\u2212\u00D7\u00F7(".contains(last)
        input.append(if (shouldOpen) "(" else ")")
        render()
    }

    private fun backspace() {
        if (input.isNotEmpty()) input.deleteCharAt(input.length - 1)
        render()
    }

    private fun clear() {
        input.setLength(0)
        render()
    }

    /** "=" stores the conversion in the recent list. */
    private fun commit() {
        val result = convertNow()
        val output = when (result) {
            is Conversion.Value -> Formatter.conversion(
                result.value, App.settings.precision, App.settings.thousandsSeparator
            )
            is Conversion.Text -> result.text
            is Conversion.Failed -> {
                Snack.show(root, result.message.ifEmpty { context.getString(R.string.converter_nothing) })
                return
            }
        }
        val entry = ConversionEntry(
            id = 0,
            category = category,
            fromUnit = from,
            toUnit = to,
            input = input.toString(),
            output = output,
            timestamp = System.currentTimeMillis()
        )
        App.db.addConversion(entry)
        Snack.show(root, context.getString(R.string.converter_saved, output, to))
    }

    private fun editInput() {
        promptDialog(
            context,
            context.getString(R.string.converter_edit_input),
            context.getString(R.string.converter_edit_input_body),
            initial = input.toString(),
            confirmLabel = context.getString(R.string.apply)
        ) { value ->
            input.setLength(0)
            input.append(value.filter { it.isDigit() || "+-*/^%().".contains(it) })
            render()
        }
    }

    private fun pickUnit(isFrom: Boolean) {
        val available = Units.units(category, rates)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val dialog = M3Dialog(
            context,
            context.getString(if (isFrom) R.string.converter_from else R.string.converter_to),
            Categories.title(category)
        )
        available.forEach { unit: ConverterUnit ->
            val selected = (if (isFrom) from else to) == unit.id
            val button = M3Button(
                context, "${unit.id} \u00B7 ${unit.label}",
                if (selected) M3ButtonStyle.TONAL else M3ButtonStyle.TEXT,
                sizeDp = 48
            ) {
                if (isFrom) from = unit.id else to = unit.id
                if (from == to) {
                    val other = available.firstOrNull { it.id != from }
                    if (other != null) {
                        if (isFrom) to = other.id else from = other.id
                    }
                }
                persistUnits()
                render()
                dialog.dismiss()
            }
            column.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp })
        }
        val scroll = ScrollView(context).apply { addView(column) }
        dialog.content(scroll).show()
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(context, anchor)
        menu.menu.add(0, 1, 0, context.getString(R.string.converter_recent))
        menu.menu.add(0, 2, 1, context.getString(R.string.converter_edit_rates))
        menu.menu.add(0, 3, 2, context.getString(R.string.converter_clear_recent))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showRecent()
                2 -> editRates()
                else -> {
                    App.db.clearConversions()
                    Snack.show(root, context.getString(R.string.converter_recent_cleared))
                }
            }
            true
        }
        menu.show()
    }

    private fun showRecent() {
        val entries = App.db.conversions(50)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (entries.isEmpty()) {
            column.addView(m3Text(
                context, context.getString(R.string.converter_recent_empty),
                Type.bodyMedium, Theme.colors.onSurfaceVariant, Gravity.CENTER
            ))
        }
        entries.forEach { entry ->
            val c = Theme.colors
            val row = surfaceBox(context, 16, c.surfaceContainerLow)
            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp, 12.dp, 16.dp, 12.dp)
            }
            inner.addView(m3Text(
                context,
                "${entry.input} ${entry.fromUnit} = ${entry.output} ${entry.toUnit}",
                Type.bodyLarge, c.onSurface
            ))
            inner.addView(m3Text(
                context,
                "${Categories.title(entry.category)} \u00B7 " + DateUtils.getRelativeTimeSpanString(
                    entry.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ),
                Type.labelSmall, c.onSurfaceVariant
            ))
            row.addView(inner)
            row.isClickable = true
            row.makePressable(c.stateLayer(c.onSurface, 0x1F))
            row.setOnClickListener {
                input.setLength(0)
                input.append(entry.output.replace("\u2009", ""))
                if (category != entry.category) {
                    category = entry.category
                    App.settings.converterCategory = entry.category
                    rates = App.db.rates()
                }
                restoreUnits(force = true)
                from = entry.toUnit
                to = entry.fromUnit
                persistUnits()
                render()
            }
            column.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp })
        }
        val scroll = ScrollView(context).apply { addView(column) }
        M3Dialog(context, context.getString(R.string.converter_recent), context.getString(R.string.converter_recent_body))
            .content(scroll)
            .button(context.getString(R.string.converter_clear_recent)) {
                App.db.clearConversions()
                Snack.show(root, context.getString(R.string.converter_recent_cleared))
            }
            .button(context.getString(R.string.close), M3ButtonStyle.FILLED)
            .show()
    }

    private fun editRates() {
        val c = Theme.colors
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val updated = App.db.meta("rates_updated")?.toLongOrNull()
        column.addView(m3Text(
            context,
            if (updated == null) context.getString(R.string.rates_never_updated)
            else context.getString(
                R.string.rates_updated_at,
                DateUtils.getRelativeTimeSpanString(updated, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS)
            ),
            Type.bodySmall, c.onSurfaceVariant
        ), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12.dp })

        App.db.rates().forEach { rate ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(m3Text(context, rate.code, Type.titleSmall, c.onSurface),
                LinearLayout.LayoutParams(64.dp, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(m3Text(context, rate.rate.toString(), Type.bodyMedium, c.onSurfaceVariant),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val edit = M3IconButton(context, Icons.TUNE, M3ButtonStyle.TEXT, 40) {
                promptDialog(
                    context,
                    context.getString(R.string.rate_for, rate.code),
                    context.getString(R.string.rate_hint),
                    initial = rate.rate.toString(),
                    numeric = true,
                    confirmLabel = context.getString(R.string.save)
                ) { value ->
                    val parsed = value.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        Snack.show(root, context.getString(R.string.rate_invalid))
                    } else {
                        App.db.upsertRate(rate.code, parsed)
                        App.db.setMeta("rates_updated", System.currentTimeMillis().toString())
                        rates = App.db.rates()
                        render()
                        editRates()
                    }
                }
            }
            edit.contentDescription = context.getString(R.string.rate_for, rate.code)
            row.addView(edit, LinearLayout.LayoutParams(40.dp, 40.dp))
            column.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp })
        }
        val scroll = ScrollView(context).apply { addView(column) }
        M3Dialog(context, context.getString(R.string.converter_edit_rates), context.getString(R.string.rates_body))
            .content(scroll)
            .button(context.getString(R.string.close), M3ButtonStyle.FILLED)
            .show()
    }

    private fun currentNumberHasDot(): Boolean {
        var i = input.length - 1
        while (i >= 0 && (input[i].isDigit() || input[i] == '.')) {
            if (input[i] == '.') return true
            i--
        }
        return false
    }
}
