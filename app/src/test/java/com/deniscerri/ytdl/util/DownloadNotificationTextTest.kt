package com.deniscerri.ytdl.util

import org.junit.Assert.*
import org.junit.Test

class DownloadNotificationTextTest {
    @Test fun percentagesAreBoundedAndCompletionIsReservedForSavedFiles() {
        assertEquals(0, DownloadNotificationText.inProgressPercent(-1))
        assertEquals(0, DownloadNotificationText.inProgressPercent(0))
        assertEquals(47, DownloadNotificationText.inProgressPercent(47))
        assertEquals(99, DownloadNotificationText.inProgressPercent(100))
        assertEquals(99, DownloadNotificationText.inProgressPercent(Int.MAX_VALUE))
    }
    @Test fun arabicTitleStaysOnOneLine() {
        assertEquals("اسم المقطع الجديد", DownloadNotificationText.title(" اسم\nالمقطع\tالجديد ", "حمّل"))
    }
    @Test fun missingTitleUsesFallbackAndLongUrlsCannotExpandNotification() {
        assertEquals("حمّل", DownloadNotificationText.title(" \n", "حمّل"))
        val title = DownloadNotificationText.title("https://example.com/" + "x".repeat(300), "حمّل")
        assertEquals(90, title.length)
        assertTrue(title.endsWith("…"))
    }
}
