package com.flexteam.m3ecalc.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import com.flexteam.m3ecalc.App
import com.flexteam.m3ecalc.Nav
import com.flexteam.m3ecalc.R
import com.flexteam.m3ecalc.Screen
import com.flexteam.m3ecalc.data.HistoryEntry
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.Type
import com.flexteam.m3ecalc.theme.applyStyle
import com.flexteam.m3ecalc.ui.Icons
import com.flexteam.m3ecalc.ui.M3Button
import com.flexteam.m3ecalc.ui.M3ButtonStyle
import com.flexteam.m3ecalc.ui.M3Dialog
import com.flexteam.m3ecalc.ui.M3TopBar
import com.flexteam.m3ecalc.ui.SearchField
import com.flexteam.m3ecalc.ui.Snack
import com.flexteam.m3ecalc.ui.confirmDialog
import com.flexteam.m3ecalc.ui.dp
import com.flexteam.m3ecalc.ui.m3Text
import com.flexteam.m3ecalc.ui.surfaceBox
import com.flexteam.m3ecalc.ui.makePressable
import kotlin.math.max

/**
 * History: every calculation the app has made, stored on the device.
 * Search it, tap an entry to reuse its result, long-press to copy or delete
 * it (with Undo), or clear everything from the overflow menu.
 */
class HistoryScreen(context: Context, nav: Nav) : Screen(context, nav) {

    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var emptyBlock: LinearLayout
    private lateinit var emptyIcon: TextView
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var counter: TextView
    private var query = ""

    override fun content(): View {
        val c = Theme.colors
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.surface)
        }

        val bar = M3TopBar(context, context.getString(R.string.nav_history)) { nav.pop() }
        val more = bar.addAction(Icons.MORE_VERT, context.getString(R.string.cd_more)) {}
        more.onAction = { showMenu(more) }
        root.addView(bar)

        root.addView(SearchField(context, context.getString(R.string.history_search)) { value ->
            query = value
            render()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = 16.dp; marginEnd = 16.dp; topMargin = 8.dp })

        counter = m3Text(context, "", Type.labelMedium, c.onSurfaceVariant)
        root.addView(counter, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = 32.dp; marginEnd = 32.dp; topMargin = 12.dp })

        val scroll = ScrollView(context).apply { isFillViewport = true }
        list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 8.dp, 16.dp, 24.dp)
        }

        val empty = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32.dp, 0, 32.dp, 0)
        }
        emptyIcon = TextView(context).apply {
            typeface = Icons.typeface(context)
            text = Icons.HISTORY
            textSize = 64f
            setTextColor(c.outlineVariant)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        emptyTitle = m3Text(context, context.getString(R.string.history_empty), Type.titleMedium, c.onSurface, Gravity.CENTER)
        emptyBody = m3Text(
            context, context.getString(R.string.history_empty_body), Type.bodyMedium,
            c.onSurfaceVariant, Gravity.CENTER
        )
        empty.addView(emptyIcon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16.dp })
        empty.addView(emptyTitle)
        empty.addView(emptyBody, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8.dp })

        val scrollContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scrollContent.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        emptyBlock = empty
        scrollContent.addView(empty, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 72.dp })
        scroll.addView(scrollContent)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        render()
        return root
    }

    override fun applyInsets(top: Int, bottom: Int, ime: Int) {
        if (!::root.isInitialized) return
        (root.getChildAt(0) as M3TopBar).applyTopInset(top)
        root.setPadding(0, 0, 0, max(bottom, ime))
    }

    override fun onShow() = render()

    private fun render() {
        val entries = App.db.calculations(query, App.settings.historyLimit)
        list.removeAllViews()
        val hasEmptyQuery = query.isBlank()
        counter.text = when {
            entries.isEmpty() -> ""
            else -> context.resources.getQuantityString(R.plurals.history_count, entries.size, entries.size)
        }
        emptyBlock.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        if (entries.isEmpty()) {
            emptyTitle.text = if (hasEmptyQuery) context.getString(R.string.history_empty)
            else context.getString(R.string.history_no_match)
            emptyBody.text = if (hasEmptyQuery) context.getString(R.string.history_empty_body) else query
        } else {
            entries.forEach { entry ->
                list.addView(row(entry), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dp })
            }
        }
    }

    private fun row(entry: HistoryEntry): View {
        val c = Theme.colors
        val card = surfaceBox(context, 20, c.surfaceContainerLow)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
        }
        column.addView(
            m3Text(context, entry.expression, Type.bodyMedium, c.onSurfaceVariant),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        column.addView(
            m3Text(context, "= ${entry.result}", Type.titleLarge, c.onSurface),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
        )
        column.addView(
            m3Text(
                context,
                DateUtils.getRelativeTimeSpanString(
                    entry.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ).toString(),
                Type.labelSmall,
                c.onSurfaceVariant
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp }
        )
        card.addView(column)
        card.contentDescription = context.getString(R.string.history_use_result)
        card.isClickable = true
        card.makePressable(c.stateLayer(c.onSurface, 0x1F))
        card.setOnClickListener {
            (context as? com.flexteam.m3ecalc.MainActivity)?.deliverToCalculator(entry.result)
            nav.pop()
        }
        card.setOnLongClickListener { showEntryMenu(it, entry); true }

        return card
    }

    private fun showEntryMenu(anchor: View, entry: HistoryEntry) {
        val menu = PopupMenu(context, anchor)
        menu.menu.add(0, 1, 0, context.getString(R.string.history_copy_result))
        menu.menu.add(0, 2, 1, context.getString(R.string.history_copy_expression))
        menu.menu.add(0, 3, 2, context.getString(R.string.history_delete))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    copy(entry.result, context.getString(R.string.copied_result))
                    true
                }
                2 -> {
                    copy(entry.expression, context.getString(R.string.copied_expression))
                    true
                }
                else -> {
                    delete(entry)
                    true
                }
            }
        }
        menu.show()
    }

    private fun copy(value: String, message: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("M3E Calc", value))
        Snack.show(root, message)
    }

    private fun delete(entry: HistoryEntry) {
        App.db.deleteCalculation(entry.id)
        render()
        Snack.show(
            root,
            context.getString(R.string.history_deleted),
            context.getString(R.string.undo)
        ) {
            App.db.restoreCalculation(entry)
            render()
        }
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(context, anchor)
        menu.menu.add(0, 1, 0, context.getString(R.string.history_clear_all))
        menu.menu.add(0, 2, 1, context.getString(R.string.history_retention))
        menu.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                confirmDialog(
                    context,
                    context.getString(R.string.history_clear_all),
                    context.getString(R.string.history_clear_all_body),
                    context.getString(R.string.clear)
                ) {
                    val count = App.db.calculations().size
                    App.db.clearCalculations()
                    render()
                    Snack.show(root, context.getString(R.string.history_cleared, count))
                }
            } else {
                showRetention()
            }
            true
        }
        menu.show()
    }

    private fun showRetention() {
        val options = listOf(50, 100, 200, 500, 1000)
        val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        options.forEach { value ->
            val button = M3Button(
                context,
                context.getString(R.string.history_keep_n, value),
                if (value == App.settings.historyLimit) M3ButtonStyle.TONAL else M3ButtonStyle.TEXT,
                sizeDp = 48
            ) {
                App.settings.historyLimit = value
                render()
                Snack.show(root, context.getString(R.string.history_keep_n, value))
            }
            row.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp })
        }
        M3Dialog(context, context.getString(R.string.history_retention), context.getString(R.string.history_retention_body))
            .content(row)
            .show()
    }
}
