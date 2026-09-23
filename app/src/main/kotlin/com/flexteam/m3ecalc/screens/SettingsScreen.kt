package com.flexteam.m3ecalc.screens

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flexteam.m3ecalc.App
import com.flexteam.m3ecalc.MainActivity
import com.flexteam.m3ecalc.Nav
import com.flexteam.m3ecalc.R
import com.flexteam.m3ecalc.Screen
import com.flexteam.m3ecalc.calc.AngleUnit
import com.flexteam.m3ecalc.theme.DisplayBold
import com.flexteam.m3ecalc.theme.DynamicColor
import com.flexteam.m3ecalc.theme.SectionHeader
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.ThemeMode
import com.flexteam.m3ecalc.theme.ThemeSource
import com.flexteam.m3ecalc.theme.Type
import com.flexteam.m3ecalc.theme.applyStyle
import com.flexteam.m3ecalc.ui.M3Button
import com.flexteam.m3ecalc.ui.M3ButtonStyle
import com.flexteam.m3ecalc.ui.M3Dialog
import com.flexteam.m3ecalc.ui.M3Slider
import com.flexteam.m3ecalc.ui.M3Switch
import com.flexteam.m3ecalc.ui.M3TopBar
import com.flexteam.m3ecalc.ui.Snack
import com.flexteam.m3ecalc.ui.connectedGroup
import com.flexteam.m3ecalc.ui.dp
import com.flexteam.m3ecalc.ui.m3Divider
import com.flexteam.m3ecalc.ui.m3Text
import com.flexteam.m3ecalc.ui.makePressable
import com.flexteam.m3ecalc.ui.surfaceBox
import kotlin.math.roundToInt

/**
 * Settings. Appearance, colour source, button feedback, calculation options
 * and the on-device data. Everything here is persisted and applied live.
 */
class SettingsScreen(context: Context, nav: Nav) : Screen(context, nav) {

    private lateinit var root: LinearLayout
    private lateinit var themeName: TextView
    private lateinit var themeCaption: TextView
    private lateinit var pressValue: TextView
    private lateinit var precisionValue: TextView
    private lateinit var keepValue: TextView
    private val modeButtons = mutableMapOf<ThemeMode, M3Button>()
    private val angleButtons = mutableMapOf<AngleUnit, M3Button>()
    private val keepButtons = mutableMapOf<Int, M3Button>()

    override fun content(): View {
        val c = Theme.colors
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.surface)
        }
        root.addView(M3TopBar(context, context.getString(R.string.nav_settings)) { nav.pop() })

        val scroll = ScrollView(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 32.dp)
        }

        column.addSection(R.string.settings_appearance)
        column.addView(appearanceGroup())
        column.addDivider()

        column.addSection(R.string.settings_theme)
        column.addView(themeCard())
        column.addDivider()

        column.addSection(R.string.settings_buttons)
        column.addView(pressSection())
        column.addView(switchRow(R.string.settings_haptics, App.settings.haptics) { value ->
            App.settings.haptics = value
        })
        column.addDivider()

        column.addSection(R.string.settings_calculation)
        column.addView(angleGroup())
        column.addView(precisionSection())
        column.addView(switchRow(R.string.settings_separator, App.settings.thousandsSeparator) { value ->
            App.settings.thousandsSeparator = value
        })
        column.addDivider()

        column.addSection(R.string.settings_history)
        column.addView(historySection())
        column.addDivider()

        column.addSection(R.string.settings_data)
        column.addView(dataSection())

        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        return root
    }

    override fun applyInsets(top: Int, bottom: Int, ime: Int) {
        if (!::root.isInitialized) return
        (root.getChildAt(0) as M3TopBar).applyTopInset(top)
        root.setPadding(0, 0, 0, bottom)
    }

    // ------------------------------------------------------------- sections

    private fun LinearLayout.addSection(titleRes: Int) {
        val label = m3Text(context, context.getString(titleRes), SectionHeader, Theme.colors.onSurface)
        addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16.dp; bottomMargin = 12.dp })
    }

    private fun LinearLayout.addDivider() {
        addView(m3Divider(context, 0, 16))
    }

    private fun appearanceGroup(): View {
        val modes = listOf(
            ThemeMode.SYSTEM to context.getString(R.string.mode_system),
            ThemeMode.LIGHT to context.getString(R.string.mode_light),
            ThemeMode.DARK to context.getString(R.string.mode_dark)
        )
        val buttons = modes.map { (mode, label) ->
            M3Button(context, label, M3ButtonStyle.FILLED, sizeDp = 56) {
                App.settings.themeMode = mode
                refreshModes()
                (context as? MainActivity)?.applyThemeChange()
            }.also { modeButtons[mode] = it }
        }
        refreshModes()
        return connectedGroup(context, buttons)
    }

    private fun refreshModes() {
        modeButtons.forEach { (mode, button) ->
            button.setStyle(M3ButtonStyle.FILLED, tonalWhenSelected = mode == App.settings.themeMode)
        }
    }

    private fun themeCard(): View {
        val c = Theme.colors
        val box = surfaceBox(context, 48, c.secondaryContainer)
        themeName = TextView(context).apply {
            text = themeNameText()
            applyStyle(DisplayBold)
            setTextColor(c.onSecondaryContainer)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        themeCaption = m3Text(
            context, themeCaptionText(), Type.bodyMedium, c.onSecondaryContainer, Gravity.CENTER
        )
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16.dp, 0, 16.dp, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 200.dp
            )
        }
        inner.addView(themeName, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        inner.addView(themeCaption, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8.dp })
        box.addView(inner)
        box.isClickable = true
        box.makePressable(c.stateLayer(c.onSecondaryContainer, 0x1F))
        box.contentDescription = context.getString(R.string.settings_theme_switch)
        box.setOnClickListener { cycleTheme() }
        return box
    }

    private fun themeNameText(): String = context.getString(
        if (App.settings.themeSource == ThemeSource.DYNAMIC && DynamicColor.supported)
            R.string.theme_dynamic else R.string.theme_purple
    )

    private fun themeCaptionText(): String = when {
        DynamicColor.supported -> context.getString(R.string.theme_dynamic_hint)
        else -> context.getString(R.string.theme_dynamic_unavailable)
    }

    private fun cycleTheme() {
        if (!DynamicColor.supported) {
            App.settings.themeSource = ThemeSource.PURPLE
            themeName.text = themeNameText()
            themeCaption.text = themeCaptionText()
            Snack.show(root, context.getString(R.string.theme_dynamic_unavailable))
            return
        }
        val next = if (App.settings.themeSource == ThemeSource.DYNAMIC) ThemeSource.PURPLE
        else ThemeSource.DYNAMIC
        App.settings.themeSource = next
        (context as? MainActivity)?.applyThemeChange()
    }

    private fun pressSection(): View {
        val c = Theme.colors
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            m3Text(context, context.getString(R.string.settings_press_effect), Type.titleSmall, c.onSurface),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        pressValue = m3Text(
            context, percentLabel(App.settings.pressEffect), Type.titleSmall, c.primary, Gravity.END
        )
        header.addView(pressValue, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        column.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val slider = M3Slider(context, 0f, 100f, App.settings.pressEffect * 100f, 1f)
        slider.onValueChange = { value ->
            pressValue.text = percentLabel(value / 100f)
            App.settings.pressEffect = value / 100f
            Theme.pressScale = App.settings.pressEffect
        }
        column.addView(slider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val demo = M3Button(context, context.getString(R.string.settings_press_preview), M3ButtonStyle.FILLED, sizeDp = 56) {}
        column.addView(connectedGroup(context, listOf(demo)), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8.dp })
        return column
    }

    private fun precisionSection(): View {
        val c = Theme.colors
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            m3Text(context, context.getString(R.string.settings_precision), Type.titleSmall, c.onSurface),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        precisionValue = m3Text(
            context, App.settings.precision.toString(), Type.titleSmall, c.primary, Gravity.END
        )
        header.addView(precisionValue, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        column.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val slider = M3Slider(context, 0f, 15f, App.settings.precision.toFloat(), 1f)
        slider.onValueChange = { value ->
            val decimals = value.roundToInt()
            precisionValue.text = decimals.toString()
            App.settings.precision = decimals
        }
        column.addView(slider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return column
    }

    private fun angleGroup(): View {
        val units = listOf(
            AngleUnit.DEG to context.getString(R.string.angle_deg),
            AngleUnit.RAD to context.getString(R.string.angle_rad)
        )
        val buttons = units.map { (unit, label) ->
            M3Button(context, label, M3ButtonStyle.FILLED, sizeDp = 48) {
                App.settings.angleUnit = unit
                refreshAngles()
            }.also { angleButtons[unit] = it }
        }
        refreshAngles()
        return connectedGroup(context, buttons)
    }

    private fun refreshAngles() {
        angleButtons.forEach { (unit, button) ->
            button.setStyle(M3ButtonStyle.FILLED, tonalWhenSelected = unit == App.settings.angleUnit)
        }
    }

    private fun switchRow(labelRes: Int, initial: Boolean, onChanged: (Boolean) -> Unit): View {
        val c = Theme.colors
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            m3Text(context, context.getString(labelRes), Type.titleSmall, c.onSurface),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(M3Switch(context, initial, onChanged), LinearLayout.LayoutParams(52.dp, 32.dp))
        row.setPadding(0, 8.dp, 0, 8.dp)
        return row
    }

    private fun historySection(): View {
        val c = Theme.colors
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            m3Text(context, context.getString(R.string.settings_keep), Type.titleSmall, c.onSurface),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        keepValue = m3Text(
            context, App.settings.historyLimit.toString(), Type.titleSmall, c.primary, Gravity.END
        )
        row.addView(keepValue, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        column.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val limits = listOf(50, 100, 200, 500, 1000)
        val buttons = limits.map { value ->
            M3Button(context, value.toString(), M3ButtonStyle.FILLED, sizeDp = 48) {
                App.settings.historyLimit = value
                keepValue.text = value.toString()
                limits.forEach { limit ->
                    keepButtons[limit]?.setStyle(M3ButtonStyle.FILLED, tonalWhenSelected = limit == value)
                }
            }.also { keepButtons[value] = it }
        }
        limits.forEach { value ->
            keepButtons[value]?.setStyle(
                M3ButtonStyle.FILLED,
                tonalWhenSelected = value == App.settings.historyLimit
            )
        }
        column.addView(connectedGroup(context, buttons), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12.dp })

        val clear = M3Button(
            context, context.getString(R.string.history_clear_all), M3ButtonStyle.OUTLINED, sizeDp = 48
        ) {
            com.flexteam.m3ecalc.ui.confirmDialog(
                context,
                context.getString(R.string.history_clear_all),
                context.getString(R.string.history_clear_all_body),
                context.getString(R.string.clear)
            ) {
                val count = App.db.calculations().size
                App.db.clearCalculations()
                Snack.show(root, context.getString(R.string.history_cleared, count))
            }
        }
        column.addView(connectedGroup(context, listOf(clear)), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12.dp })
        return column
    }

    private fun dataSection(): View {
        val c = Theme.colors
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(
            m3Text(
                context,
                context.getString(
                    R.string.settings_storage_summary,
                    App.db.calculationCount(),
                    App.db.conversions(1000).size
                ),
                Type.bodySmall,
                c.onSurfaceVariant
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp }
        )
        val clearConversions = M3Button(
            context, context.getString(R.string.converter_clear_recent), M3ButtonStyle.OUTLINED, sizeDp = 48
        ) {
            App.db.clearConversions()
            Snack.show(root, context.getString(R.string.converter_recent_cleared))
        }
        val resetRates = M3Button(
            context, context.getString(R.string.rates_reset), M3ButtonStyle.OUTLINED, sizeDp = 48
        ) {
            M3Dialog(
                context,
                context.getString(R.string.rates_reset),
                context.getString(R.string.rates_reset_body)
            )
                .button(context.getString(R.string.cancel))
                .button(context.getString(R.string.reset), M3ButtonStyle.FILLED) {
                    App.db.rates().forEach { App.db.deleteRate(it.code) }
                    App.db.ensureRates(com.flexteam.m3ecalc.data.Units.defaultRates)
                    App.db.setMeta("rates_updated", System.currentTimeMillis().toString())
                    Snack.show(root, context.getString(R.string.rates_reset_done))
                }
                .show()
        }
        column.addView(
            connectedGroup(context, listOf(clearConversions, resetRates)),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return column
    }

    private fun percentLabel(value: Float): String = "${(value * 100).roundToInt()}%"
}
