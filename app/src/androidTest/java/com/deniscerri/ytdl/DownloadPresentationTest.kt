package com.deniscerri.ytdl

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deniscerri.ytdl.util.MediaShareUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.Format
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadPresentationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun posted(id: Int, expectedText: String): Notification {
        repeat(100) {
            val notification = context.getSystemService(NotificationManager::class.java)
                .activeNotifications.firstOrNull { it.id == id }?.notification
            if (notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.startsWith(expectedText) == true) return notification
            android.os.SystemClock.sleep(50)
        }
        error("Notification was not posted with expected text")
    }

    @Test fun notificationsContainOnlyTitleAndProgress() {
        val util = NotificationUtil(context)
        util.createNotificationChannel()
        val title = "مقطع تجريبي"
        val initial = util.createDownloadServiceNotification(null, title)
        assertEquals("0%", initial.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(initial.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        for (percent in listOf(0, 37, 100)) {
            util.updateDownloadNotification(12001, percent, title, NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
            val notification = posted(12001, "${percent.coerceAtMost(99)}%")
            assertEquals(title, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("${percent.coerceAtMost(99)}%", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertNull(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            assertNull(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            assertFalse(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
            assertEquals(2, notification.actions.size)
        }
        util.cancelDownloadNotification(12001)
        util.createDownloadFinished(12002, title, DownloadType.video, null, context.resources)
        val finished = posted(NotificationUtil.DOWNLOAD_FINISHED_NOTIFICATION_ID + 12002, "100%")
        assertTrue(finished.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("100%"))
        assertNull(finished.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @Test fun singleVideoSharesReadableContentUriAndMultipleVideosStayMultiple() {
        val first = File(context.cacheDir, "share-first.mp4").apply { writeText("test media bytes") }
        val second = File(context.cacheDir, "share-second.mp4").apply { writeText("second media bytes") }
        try {
            val single = requireNotNull(MediaShareUtil.createIntent(context, listOf(first.path)))
            assertEquals(Intent.ACTION_SEND, single.action)
            assertEquals("video/mp4", single.type)
            val uri = requireNotNull(single.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("content", uri.scheme)
            assertTrue(single.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri, single.clipData!!.getItemAt(0).uri)
            assertEquals("test media bytes", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            assertTrue(MediaShareUtil.isAvailable(context, uri.toString()))
            assertTrue(MediaShareUtil.isAvailable(context, Uri.fromFile(first).toString()))
            val multiple = requireNotNull(MediaShareUtil.createIntent(context, listOf(first.path, second.path)))
            assertEquals(Intent.ACTION_SEND_MULTIPLE, multiple.action)
            assertEquals(2, multiple.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!.size)
            assertEquals(2, multiple.clipData!!.itemCount)
            val remaining = requireNotNull(MediaShareUtil.createIntent(context, listOf("/missing/video.mp4", first.path)))
            assertEquals(Intent.ACTION_SEND, remaining.action)
            assertNull(MediaShareUtil.createIntent(context, listOf("https://example.com/movie", "/missing/video.mp4", "")))
        } finally { first.delete(); second.delete() }
    }

    /** Fixture only in the test APK, never an exported production component. */
    @Test fun seedScreenshotFixture() = runBlocking {
        val file = File(context.cacheDir, "hammel-preview.mp4").apply { writeText("UI verification fixture") }
        DBManager.getInstance(context).historyDao.insert(HistoryItem(
            id = 99001, url = "https://example.invalid/video", title = "مقطع تجريبي للمشاركة",
            author = "", duration = "00:30", thumb = "", type = DownloadType.video,
            time = System.currentTimeMillis() / 1000, downloadPath = listOf(file.path),
            website = "", format = Format(container = "mp4"), downloadId = 99001
        ))
        val util = NotificationUtil(context)
        util.createNotificationChannel()
        util.updateDownloadNotification(12003, 37, "مقطع تجريبي", NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
        posted(12003, "37%")
    }
}
