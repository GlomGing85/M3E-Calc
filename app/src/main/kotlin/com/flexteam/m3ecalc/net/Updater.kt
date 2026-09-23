package com.flexteam.m3ecalc.net

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Update check and APK download.
 *
 * Talks to the GitHub releases API for this repository. Everything runs on a
 * background thread and calls back on the main looper; a device without
 * network simply gets a failure result, which the UI reports.
 */
object Updater {

    const val REPO = "GlomGing85/M3E-Calc"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private val main = Handler(Looper.getMainLooper())

    data class Release(
        val tag: String,
        val name: String,
        val body: String,
        val assetName: String?,
        val assetUrl: String?,
        val assetSize: Long,
        val publishedAt: String
    )

    /** A handle that can cancel an in-flight download. */
    class Task {
        private val cancelled = AtomicBoolean(false)
        fun cancel() {
            cancelled.set(true)
        }
        val isCancelled: Boolean get() = cancelled.get()
    }

    fun check(onResult: (Result<Release?>) -> Unit) {
        Thread {
            val result = runCatching { fetchLatest() }
            main.post { onResult(result) }
        }.apply { isDaemon = true }.start()
    }

    private fun fetchLatest(): Release? {
        val connection = open(API)
        try {
            val code = connection.responseCode
            if (code == 404) return null
            if (code !in 200..299) error("HTTP $code")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            var assetName: String? = null
            var assetUrl: String? = null
            var assetSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk")) {
                        assetName = name
                        assetUrl = asset.optString("browser_download_url")
                        assetSize = asset.optLong("size")
                        break
                    }
                }
            }
            return Release(
                tag = json.optString("tag_name"),
                name = json.optString("name").ifBlank { json.optString("tag_name") },
                body = json.optString("body"),
                assetName = assetName,
                assetUrl = assetUrl?.takeIf { it.isNotBlank() },
                assetSize = assetSize,
                publishedAt = json.optString("published_at")
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads [url] into [target], reporting progress on the main thread. */
    fun download(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onDone: (Result<File>) -> Unit
    ): Task {
        val task = Task()
        Thread {
            val result = runCatching {
                target.parentFile?.mkdirs()
                val connection = open(url)
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) error("HTTP $code")
                    val total = if (connection.contentLengthLong > 0) {
                        connection.contentLengthLong
                    } else {
                        -1L
                    }
                    var downloaded = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (task.isCancelled) {
                                    target.delete()
                                    error("cancelled")
                                }
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val snapshot = downloaded
                                main.post { onProgress(snapshot, total) }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                target
            }
            main.post { onDone(result) }
        }.apply { isDaemon = true }.start()
        return task
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "M3E-Calc-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
}
