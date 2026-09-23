package com.flexteam.m3ecalc.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flexteam.m3ecalc.BuildInfo
import com.flexteam.m3ecalc.Direction
import com.flexteam.m3ecalc.Nav
import com.flexteam.m3ecalc.R
import com.flexteam.m3ecalc.Screen
import com.flexteam.m3ecalc.net.Updater
import com.flexteam.m3ecalc.theme.DynamicColor
import com.flexteam.m3ecalc.theme.Theme
import com.flexteam.m3ecalc.theme.Type
import com.flexteam.m3ecalc.ui.Icons
import com.flexteam.m3ecalc.ui.M3Button
import com.flexteam.m3ecalc.ui.M3ButtonStyle
import com.flexteam.m3ecalc.ui.M3IconButton
import com.flexteam.m3ecalc.ui.M3TopBar
import com.flexteam.m3ecalc.ui.Snack
import com.flexteam.m3ecalc.ui.connectedGroup
import com.flexteam.m3ecalc.ui.dp
import com.flexteam.m3ecalc.ui.m3Text

/** About: app identity, version, and the way out to updates, GitHub and credits. */
class AboutScreen(context: Context, nav: Nav) : Screen(context, nav) {

    private lateinit var root: LinearLayout

    override fun content(): View {
        val c = Theme.colors
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.surface)
        }
        root.addView(M3TopBar(context, context.getString(R.string.nav_about)) { nav.pop() })

        val scroll = ScrollView(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp, 24.dp, 24.dp, 32.dp)
        }

        val logo = M3IconButton(context, Icons.EQUAL, M3ButtonStyle.TONAL, 136) {
            Snack.show(root, context.getString(R.string.about_logo_hint))
        }
        logo.contentDescription = context.getString(R.string.app_name)
        column.addView(logo, LinearLayout.LayoutParams(136.dp, 136.dp))

        column.addView(
            m3Text(context, context.getString(R.string.app_name), Type.headlineMedium, c.onSurface, Gravity.CENTER),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.dp }
        )
        column.addView(
            m3Text(
                context,
                context.getString(R.string.about_version, BuildInfo.VERSION_NAME, BuildInfo.VERSION_CODE),
                Type.bodyMedium, c.onSurfaceVariant, Gravity.CENTER
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
        )
        column.addView(
            m3Text(
                context,
                context.getString(R.string.about_built, BuildInfo.BUILD_DATE),
                Type.labelSmall, c.onSurfaceVariant, Gravity.CENTER
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2.dp }
        )
        column.addView(
            m3Text(
                context,
                context.getString(
                    if (DynamicColor.supported) R.string.about_dynamic_yes else R.string.about_dynamic_no
                ),
                Type.labelSmall, c.onSurfaceVariant, Gravity.CENTER
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2.dp }
        )

        val updates = M3Button(
            context, context.getString(R.string.about_check_updates), M3ButtonStyle.FILLED, sizeDp = 56
        ) {
            nav.push(DownloadScreen(context, nav), Direction.RIGHT)
        }
        val github = M3Button(
            context, context.getString(R.string.about_github), M3ButtonStyle.FILLED, sizeDp = 56
        ) { openGithub() }
        val thanks = M3Button(context, "", M3ButtonStyle.TONAL, sizeDp = 56) {
            nav.push(ThanksScreen(context, nav), Direction.RIGHT)
        }
        thanks.setIcon(Icons.FAVORITE)
        thanks.contentDescription = context.getString(R.string.nav_thanks)

        column.addView(
            connectedGroup(context, listOf(updates, github, thanks)),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32.dp }
        )

        column.addView(
            m3Text(
                context, context.getString(R.string.about_license, BuildInfo.VERSION_NAME),
                Type.bodySmall, c.onSurfaceVariant, Gravity.CENTER
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 28.dp }
        )
        column.addView(
            m3Text(
                context,
                context.getString(R.string.about_icons),
                Type.labelSmall, c.onSurfaceVariant, Gravity.CENTER
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
        )

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

    private fun openGithub() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Updater.REPO}"))
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Snack.show(root, context.getString(R.string.about_no_browser, Updater.REPO))
        }
    }
}
