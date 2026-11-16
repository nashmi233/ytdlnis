package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.database.enums.DownloadType
import org.junit.Assert.*
import org.junit.Test

class SharedLinkPolicyTest {
    @Test fun extractsTikTokFromCaption() {
        assertEquals("https://vm.tiktok.com/ZAb123/", SharedLinkPolicy.extract("شوف الفيديو 😂 https://vm.tiktok.com/ZAb123/ تمت المشاركة"))
    }
    @Test fun preservesQueryParameters() {
        val url = "https://www.tiktok.com/@someone/video/123?is_from_webapp=1&sender_device=pc"
        assertEquals(url, SharedLinkPolicy.extract(url))
    }
    @Test fun acceptsOtherPlatforms() {
        for (url in listOf("https://youtu.be/abc?t=12", "https://www.instagram.com/reel/abc/", "https://x.com/user/status/123", "https://example.com/video.mp4")) {
            assertEquals(url, SharedLinkPolicy.extract(url))
        }
    }
    @Test fun stripsSurroundingPunctuation() { assertEquals("https://vm.tiktok.com/abc/", SharedLinkPolicy.extract("(https://vm.tiktok.com/abc/)،")) }
    @Test fun plainTextAndLocalSchemesAreRejected() {
        for (text in listOf(null, "", "هذا نص فقط", "file:///sdcard/video.mp4", "content://video/1", "ftp://example.com/video")) {
            assertNull(SharedLinkPolicy.extract(text))
        }
    }
    @Test fun sharedLinkDefaultsToVideoEvenWithAudioPreference() {
        assertEquals(DownloadType.video, SharedLinkPolicy.downloadType(true, null, DownloadType.audio))
    }
    @Test fun sharedLinkNeverUsesPreferredCommand() {
        assertEquals(DownloadType.video, SharedLinkPolicy.downloadType(true, null, DownloadType.command))
    }
    @Test fun dedicatedAudioShortcutStillWorks() {
        assertEquals(DownloadType.audio, SharedLinkPolicy.downloadType(true, "audio", DownloadType.video))
    }
    @Test fun malformedTypeDoesNotCrash() {
        assertEquals(DownloadType.video, SharedLinkPolicy.downloadType(true, "unknown", DownloadType.audio))
    }
    @Test fun ordinaryShareImmediatelyQueues() { assertTrue(SharedLinkPolicy.downloadImmediately(true, null, false)) }
    @Test fun explicitChoiceKeepsItsDialog() { assertFalse(SharedLinkPolicy.downloadImmediately(true, "audio", false)) }
    @Test fun backgroundShortcutImmediatelyQueues() { assertTrue(SharedLinkPolicy.downloadImmediately(true, "audio", true)) }
    @Test fun openingLinkInsideAppKeepsPreferences() {
        assertFalse(SharedLinkPolicy.downloadImmediately(false, null, false))
        assertEquals(DownloadType.audio, SharedLinkPolicy.downloadType(false, null, DownloadType.audio))
    }
}
