package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.database.enums.DownloadType
import java.net.URI

internal object SharedLinkPolicy {
    private val webLink = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun extract(text: String?): String? {
        return webLink.findAll(text.orEmpty()).map { match ->
            match.value.trimEnd('.', ',', '!', '?', ')', ']', '}', '،', '؛', '。')
        }.firstOrNull { candidate ->
            runCatching {
                val uri = URI(candidate)
                uri.host != null && uri.userInfo == null &&
                    (uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
            }.getOrDefault(false)
        }
    }

    fun downloadType(shared: Boolean, explicitType: String?, preferredType: DownloadType): DownloadType {
        val explicit = DownloadType.entries.firstOrNull { it.name == explicitType }
        return explicit ?: if (shared) DownloadType.video else preferredType
    }

    fun downloadImmediately(shared: Boolean, explicitType: String?, background: Boolean): Boolean =
        background || (shared && explicitType == null)
}
