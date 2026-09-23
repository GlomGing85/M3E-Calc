package com.flexteam.m3ecalc.screens

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.flexteam.m3ecalc.Nav
import com.flexteam.m3ecalc.R
import com.flexteam.m3ecalc.Screen
import com.flexteam.m3ecalc.net.Updater
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.Type
import com.flexteam.m3ecalc.ui.Icons
import com.flexteam.m3ecalc.ui.M3Button
import com.flexteam.m3ecalc.ui.M3ButtonStyle
import com.flexteam.m3ecalc.ui.M3IconButton
import com.flexteam.m3ecalc.ui.M3TopBar
import com.flexteam.m3ecalc.ui.Snack
import com.flexteam.m3ecalc.ui.dp
import com.flexteam.m3ecalc.ui.m3Text
import com.flexteam.m3ecalc.ui.surfaceBox

/** Special thanks: who made this app possible, and a way to share it. */
class ThanksScreen(context: Context, nav: Nav) : Screen(context, nav) {

    private lateinit var root: FrameLayout

    override fun content(): View {
        val c = Theme.colors
        root = FrameLayout(context).apply { setBackgroundColor(c.surface) }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        column.addView(M3TopBar(context, context.getString(R.string.nav_thanks)) { nav.pop() })

        val scroll = ScrollView(context)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16.dp, 24.dp, 16.dp, 96.dp)
        }

        val heart = M3IconButton(context, Icons.FAVORITE, M3ButtonStyle.TONAL, 136) {}
        heart.contentDescription = context.getString(R.string.thanks_icon)
        body.addView(heart, LinearLayout.LayoutParams(136.dp, 136.dp))

        body.addView(
            m3Text(
                context, context.getString(R.string.thanks_heading),
                Type.headlineSmall, c.onSurface, Gravity.CENTER
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.dp; bottomMargin = 4.dp }
        )
        body.addView(
            m3Text(
                context, context.getString(R.string.thanks_body),
                Type.bodyMedium, c.onSurfaceVariant, Gravity.CENTER
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20.dp }
        )

        thanks().forEach { (title, text) ->
            body.addView(card(title, text), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp })
        }

        scroll.addView(body)
        column.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(column)

        val share = M3Button(
            context, context.getString(R.string.thanks_share), M3ButtonStyle.FILLED,
            sizeDp = 56, cornerRadiusDp = 16
        ) { share() }
        share.setIcon(Icons.SHARE)
        root.addView(share, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, 56.dp, Gravity.BOTTOM or Gravity.END
        ).apply { marginEnd = 16.dp; bottomMargin = 24.dp })

        return root
    }

    override fun applyInsets(top: Int, bottom: Int, ime: Int) {
        if (!::root.isInitialized) return
        val column = root.getChildAt(0) as LinearLayout
        (column.getChildAt(0) as M3TopBar).applyTopInset(top)
        root.setPadding(0, 0, 0, bottom)
    }

    private fun thanks(): List<Pair<String, String>> = listOf(
        context.getString(R.string.thanks_material_title) to context.getString(R.string.thanks_material_body),
        context.getString(R.string.thanks_aosp_title) to context.getString(R.string.thanks_aosp_body),
        context.getString(R.string.thanks_kotlin_title) to context.getString(R.string.thanks_kotlin_body),
        context.getString(R.string.thanks_users_title) to context.getString(R.string.thanks_users_body)
    )

    private fun card(title: String, text: String): View {
        val c = Theme.colors
        val card = surfaceBox(context, 20, c.surfaceContainerLow)
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
        }
        inner.addView(m3Text(context, title, Type.titleMedium, c.onSurface))
        inner.addView(
            m3Text(context, text, Type.bodyMedium, c.onSurfaceVariant),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
        )
        card.addView(inner)
        return card
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
            putExtra(
                Intent.EXTRA_TEXT,
                context.getString(R.string.thanks_share_text, "https://github.com/${Updater.REPO}")
            )
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.thanks_share))
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(chooser)
        } else {
            Snack.show(root, context.getString(R.string.thanks_share_unavailable))
        }
    }
}
