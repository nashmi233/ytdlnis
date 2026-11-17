package com.deniscerri.ytdl.util

/** Public notification text must not contain engine output, paths or multi-line metadata. */
internal object DownloadNotificationText {
    fun title(value: String?, fallback: String): String {
        val compact = value.orEmpty().replace(Regex("\\s+"), " ").trim().ifBlank { fallback }
        return if (compact.length > 90) compact.take(89) + "…" else compact
    }

    // yt-dlp can report 100 before merging/conversion and moving the final file.
    // Only createDownloadFinished, after those operations succeed, displays 100%.
    fun inProgressPercent(reported: Int): Int = reported.coerceIn(0, 99)
}
