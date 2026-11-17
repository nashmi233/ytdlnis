package com.deniscerri.ytdl.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Share downloaded bytes with temporary read permission, never a page URL or file:// URI. */
object MediaShareUtil {
    private fun localFile(path: String): File? {
        if (path.isBlank()) return null
        val uri = Uri.parse(path)
        return when (uri.scheme) {
            null -> File(path)
            "file" -> uri.path?.let(::File)
            else -> null
        }
    }

    fun isAvailable(context: Context, path: String): Boolean = runCatching {
        val uri = Uri.parse(path)
        if (uri.scheme == "content") {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
        } else {
            localFile(path)?.let { it.isFile && it.canRead() } == true
        }
    }.getOrDefault(false)

    private fun resolve(context: Context, path: String): Uri? = runCatching {
        if (!isAvailable(context, path)) return null
        val uri = Uri.parse(path)
        if (uri.scheme == "content") uri else {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", requireNotNull(localFile(path)))
        }
    }.getOrNull()

    fun createIntent(context: Context, paths: List<String>): Intent? {
        val uris = paths.mapNotNull { resolve(context, it) }.distinct()
        if (uris.isEmpty()) return null
        val types = uris.map { uri ->
            runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
                ) ?: "application/octet-stream"
        }
        val mime = if (types.distinct().size == 1) types.first() else {
            val families = types.map { it.substringBefore('/') }.distinct()
            if (families.size == 1) families.first() + "/*" else "*/*"
        }
        return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData("media", arrayOf(mime), ClipData.Item(uris.first())).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
        }
    }
}
