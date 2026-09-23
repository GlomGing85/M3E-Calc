package com.flexteam.m3ecalc.net

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves downloaded APKs to the package installer.
 *
 * Android 7+ refuses `file://` URIs across apps, and the AndroidX FileProvider
 * is not available in this build, so the app ships this minimal provider for
 * its own private downloads directory.
 */
class UpdateFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = resolve(uri) ?: throw FileNotFoundException("No such download: $uri")
        if (!file.exists()) throw FileNotFoundException("No such download: $uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    private fun resolve(uri: Uri): File? {
        val context: Context = context ?: return null
        val name = uri.lastPathSegment ?: return null
        if (name.contains('/') || name.contains("..")) return null
        val file = File(downloadsDir(context), name)
        return if (file.canonicalPath.startsWith(downloadsDir(context).canonicalPath)) file else null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.flexteam.m3ecalc.updates"

        fun downloadsDir(context: Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
                .apply { mkdirs() }

        fun uriFor(context: Context, file: File): Uri =
            Uri.parse("content://$AUTHORITY/${file.name}")
    }
}
