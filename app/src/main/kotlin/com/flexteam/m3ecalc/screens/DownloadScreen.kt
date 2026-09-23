package com.flexteam.m3ecalc.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.flexteam.m3ecalc.BuildInfo
import com.flexteam.m3ecalc.Nav
import com.flexteam.m3ecalc.R
import com.flexteam.m3ecalc.Screen
import com.flexteam.m3ecalc.net.UpdateFileProvider
import com.flexteam.m3ecalc.net.Updater
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
import com.flexteam.m3ecalc.ui.makePressable
import com.flexteam.m3ecalc.ui.surfaceBox
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Download manager: checks GitHub for a newer release, downloads its APK and
 * keeps the finished files until the user deletes or installs them.
 */
class DownloadScreen(context: Context, nav: Nav) : Screen(context, nav) {

    private enum class State { READY, DOWNLOADING, FAILED }

    private class Item(
        val file: File,
        var state: State = State.READY,
        var progress: Long = 0,
        var total: Long = -1,
        var message: String = "",
        var task: Updater.Task? = null
    )

    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var emptyView: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var versionText: TextView
    private val items = mutableListOf<Item>()
    private var latest: Updater.Release? = null

    override fun content(): View {
        val c = Theme.colors
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(c.surface)
        }
        val bar = M3TopBar(context, context.getString(R.string.nav_downloads)) { nav.pop() }
        val more = bar.addAction(Icons.MORE_VERT, context.getString(R.string.cd_more)) {}
        more.onAction = { showMenu(more) }
        root.addView(bar)

        val scroll = ScrollView(context).apply { isFillViewport = true }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 8.dp, 16.dp, 24.dp)
        }

        val card = surfaceBox(context, 20, c.surfaceContainerLow)
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
        }
        versionText = m3Text(
            context,
            context.getString(R.string.downloads_current, BuildInfo.VERSION_NAME, BuildInfo.VERSION_CODE),
            Type.titleMedium, c.onSurface
        )
        inner.addView(versionText)
        statusText = m3Text(
            context, context.getString(R.string.downloads_status_unknown),
            Type.bodySmall, c.onSurfaceVariant
        )
        inner.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 4.dp })
        val check = M3Button(
            context, context.getString(R.string.about_check_updates), M3ButtonStyle.FILLED, sizeDp = 48
        ) { check() }
        inner.addView(
            connectedGroup(context, listOf(check)),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp }
        )
        card.addView(inner)
        column.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16.dp })

        column.addView(
            m3Text(context, context.getString(R.string.downloads_title), Type.titleMedium, c.onSurface),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp }
        )

        list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(list)

        emptyView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16.dp, 48.dp, 16.dp, 16.dp)
        }
        val icon = TextView(context).apply {
            typeface = Icons.typeface(context)
            text = Icons.DOWNLOAD
            textSize = 56f
            setTextColor(c.outlineVariant)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        emptyView.addView(icon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12.dp })
        emptyView.addView(
            m3Text(
                context, context.getString(R.string.downloads_empty),
                Type.titleSmall, c.onSurface, Gravity.CENTER
            )
        )
        emptyView.addView(
            m3Text(
                context, context.getString(R.string.downloads_empty_body),
                Type.bodySmall, c.onSurfaceVariant, Gravity.CENTER
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp }
        )
        column.addView(emptyView)

        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        reloadFromDisk()
        return root
    }

    override fun applyInsets(top: Int, bottom: Int, ime: Int) {
        if (!::root.isInitialized) return
        (root.getChildAt(0) as M3TopBar).applyTopInset(top)
        root.setPadding(0, 0, 0, bottom)
    }

    override fun onHide() {
        // In-flight downloads keep running; the screen just stops rendering them.
    }

    // ------------------------------------------------------------------ check

    private fun check() {
        statusText.text = context.getString(R.string.downloads_checking)
        Updater.check { result ->
            result.onSuccess { release ->
                if (release == null) {
                    latest = null
                    statusText.text = context.getString(R.string.downloads_no_releases)
                    return@onSuccess
                }
                latest = release
                val update = isNewer(release.tag)
                statusText.text = if (update) {
                    context.getString(R.string.downloads_update_available, release.tag, sizeLabel(release.assetSize))
                } else {
                    context.getString(R.string.downloads_up_to_date, release.tag)
                }
                if (update && release.assetUrl != null) offerDownload(release)
            }.onFailure { error ->
                statusText.text = context.getString(
                    R.string.downloads_check_failed,
                    error.message ?: context.getString(R.string.error_generic)
                )
            }
        }
    }

    /** "v1.2.0" > "1.0.0" compares segment by segment. */
    private fun isNewer(tag: String): Boolean {
        fun parts(value: String) = value.trimStart('v', 'V').split(".").map { it.toIntOrNull() ?: 0 }
        val candidate = parts(tag)
        val current = parts(BuildInfo.VERSION_NAME)
        for (i in 0 until maxOf(candidate.size, current.size)) {
            val a = candidate.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun offerDownload(release: Updater.Release) {
        val url = release.assetUrl ?: return
        val name = release.assetName ?: "M3E-Calc-${release.tag}.apk"
        if (items.any { it.file.name == name }) {
            Snack.show(root, context.getString(R.string.downloads_already_downloaded))
            return
        }
        val target = File(UpdateFileProvider.downloadsDir(context), name)
        val item = Item(target, State.DOWNLOADING)
        items.add(0, item)
        render()
        item.task = Updater.download(
            url, target,
            onProgress = { downloaded, total ->
                item.progress = downloaded
                item.total = total
                render()
            },
            onDone = { result ->
                result.onSuccess {
                    item.state = State.READY
                    item.task = null
                    render()
                    Snack.show(root, context.getString(R.string.downloads_finished, name))
                }.onFailure { error ->
                    if (error.message == "cancelled") {
                        items.remove(item)
                    } else {
                        item.state = State.FAILED
                        item.message = error.message ?: ""
                        item.task = null
                    }
                    render()
                }
            }
        )
    }

    // ------------------------------------------------------------------- list

    private fun reloadFromDisk() {
        items.clear()
        UpdateFileProvider.downloadsDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".apk") }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { items.add(Item(it)) }
        render()
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { item -> list.addView(row(item)) }
    }

    private fun row(item: Item): View {
        val c = Theme.colors
        val card = surfaceBox(context, 20, c.surfaceContainerLow)
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            m3Text(context, item.file.name, Type.titleSmall, c.onSurface),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val delete = M3IconButton(context, Icons.DELETE, M3ButtonStyle.TEXT, 40) {
            item.task?.cancel()
            item.file.delete()
            items.remove(item)
            render()
            Snack.show(root, context.getString(R.string.downloads_deleted, item.file.name))
        }
        delete.contentDescription = context.getString(R.string.downloads_delete)
        header.addView(delete, LinearLayout.LayoutParams(40.dp, 40.dp))
        inner.addView(header)

        val status = when (item.state) {
            State.READY -> context.getString(
                R.string.downloads_ready,
                sizeLabel(item.file.length()),
                SimpleDateFormat.getDateInstance().format(Date(item.file.lastModified()))
            )
            State.DOWNLOADING -> if (item.total > 0) {
                context.getString(
                    R.string.downloads_progress,
                    sizeLabel(item.progress),
                    sizeLabel(item.total),
                    (100 * item.progress / item.total).toInt()
                )
            } else {
                context.getString(R.string.downloads_progress_unknown, sizeLabel(item.progress))
            }
            State.FAILED -> context.getString(
                R.string.downloads_failed,
                item.message.ifEmpty { context.getString(R.string.error_generic) }
            )
        }
        inner.addView(
            m3Text(context, status, Type.bodySmall,
                if (item.state == State.FAILED) c.error else c.onSurfaceVariant),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
        )

        if (item.state == State.DOWNLOADING) {
            val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = item.total <= 0
                max = 100
                progress = if (item.total > 0) (100 * item.progress / item.total).toInt() else 0
            }
            inner.addView(bar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp })
        }

        if (item.state == State.READY) {
            val install = M3Button(
                context, context.getString(R.string.downloads_install), M3ButtonStyle.FILLED, sizeDp = 48
            ) { install(item) }
            val open = M3Button(
                context, context.getString(R.string.downloads_open), M3ButtonStyle.TONAL, sizeDp = 48
            ) { openFile(item) }
            inner.addView(
                connectedGroup(context, listOf(install, open)),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 12.dp }
            )
        } else if (item.state == State.DOWNLOADING) {
            val cancel = M3Button(
                context, context.getString(R.string.cancel), M3ButtonStyle.OUTLINED, sizeDp = 48
            ) { item.task?.cancel() }
            inner.addView(
                connectedGroup(context, listOf(cancel)),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 12.dp }
            )
        }

        card.addView(inner)
        card.isClickable = true
        card.makePressable(c.stateLayer(c.onSurface, 0x1F))
        card.setOnClickListener { if (item.state == State.READY) install(item) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp })
        }
    }

    private fun install(item: Item) {
        val uri: Uri = UpdateFileProvider.uriFor(context, item.file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            Snack.show(root, context.getString(R.string.downloads_install_failed))
        }
    }

    private fun openFile(item: Item) {
        val uri: Uri = UpdateFileProvider.uriFor(context, item.file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            Snack.show(root, context.getString(R.string.downloads_install_failed))
        }
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(context, anchor)
        menu.menu.add(0, 1, 0, context.getString(R.string.about_check_updates))
        menu.menu.add(0, 2, 1, context.getString(R.string.downloads_clear_finished))
        menu.menu.add(0, 3, 2, context.getString(R.string.downloads_open_folder))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> check()
                2 -> {
                    val finished = items.filter { it.state == State.READY }
                    finished.forEach {
                        it.file.delete()
                        items.remove(it)
                    }
                    render()
                    Snack.show(root, context.getString(R.string.downloads_cleared, finished.size))
                }
                else -> {
                    val dir = UpdateFileProvider.downloadsDir(context)
                    Snack.show(root, context.getString(R.string.downloads_folder, dir.absolutePath))
                }
            }
            true
        }
        menu.show()
    }

    private fun sizeLabel(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
