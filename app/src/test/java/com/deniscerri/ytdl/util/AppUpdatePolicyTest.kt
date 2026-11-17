package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.database.models.GithubReleaseAsset
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class AppUpdatePolicyTest {
    private val base = "https://github.com/nashmi233/ytdlnis/releases"
    private fun release(tag: String = "v1.8.9.3", abi: String = "arm64-v8a", flavor: String = "", build: String = "debug") =
        GithubRelease("$base/tag/$tag", tag, "", Date(0), listOf(
            GithubReleaseAsset("NashmiDownloader-1.8.9.3-${flavor}$abi-$build.apk",
                "$base/download/$tag/NashmiDownloader-1.8.9.3-${flavor}$abi-$build.apk", 123)
        ))
    private fun select(vararg releases: GithubRelease, current: String = "1.8.9.2", preview: Boolean = false, skipped: Set<String> = emptySet()) =
        AppUpdatePolicy.selectRelease(releases.toList(), current, preview, skipped, listOf("arm64-v8a", "armeabi-v7a"), "github", true)

    @Test fun newerBuildIsOffered() { assertEquals("v1.8.9.3", select(release())?.tag_name) }
    @Test fun installedBuildIsNeverOfferedAgain() { assertNull(select(release("v1.8.9.2"))) }
    @Test fun olderBuildIsNotOffered() { assertNull(select(release("v1.8.8.9"))) }
    @Test fun stableDoesNotForceDowngradeFromPreview() { assertNull(select(release("v1.8.9.2"), current = "1.8.9.3-beta")) }
    @Test fun equalPreviewAndStableAreSameAndroidBuild() { assertNull(select(release("v1.8.9.2"), current = "1.8.9.2-beta")) }
    @Test fun numericComponentsDoNotCollapse() {
        assertTrue(AppUpdatePolicy.version("1.10.0")!! > AppUpdatePolicy.version("1.9.9.9")!!)
        assertTrue(AppUpdatePolicy.version("1.8.10")!! > AppUpdatePolicy.version("1.8.9.9")!!)
        assertEquals(0, AppUpdatePolicy.version("v1.8.9")!!.compareTo(AppUpdatePolicy.version("1.8.9.0")!!))
    }
    @Test fun hammelTagsAreSupported() { assertNotNull(select(release("hammel-1.8.9.3"))) }
    @Test fun nashReleaseAndAssetNamesAreSupported() {
        val r = release("nash-1.8.9.8")
        r.assets = r.assets.map { it.copy(name = "NASH-1.8.9.8-arm64-v8a-debug.apk") }
        assertNotNull(select(r))
        assertTrue(installable(version = "1.8.9.8", tag = "nash-1.8.9.8", code = 108090800))
    }
    @Test fun renamedTagCannotOfferInstalledVersionAgain() {
        assertNull(select(release("nash-1.8.9.2")))
    }
    @Test fun nashPreviewStillRequiresOptIn() {
        assertNull(select(release("nash-1.8.9.8-test.1")))
    }
    @Test fun malformedTagsDoNotBreakValidUpdates() {
        assertNotNull(select(release("nightly"), release("999999999999999999999.1.0"), release()))
        assertNull(AppUpdatePolicy.version("1.100.0"))
    }
    @Test fun previewsRequireOptInByFlag() { assertNull(select(release().copy(prerelease = true))) }
    @Test fun previewsRequireOptInByTag() { assertNull(select(release("hammel-1.8.9.3-play-preview.1"))) }
    @Test fun optedInPreviewCanUpdate() { assertNotNull(select(release("hammel-1.8.9.3-play-preview.1").copy(prerelease = true), preview = true)) }
    @Test fun draftIsNeverOffered() { assertNull(select(release().copy(draft = true), preview = true)) }
    @Test fun skippedReleaseStaysSkipped() { assertNull(select(release(), skipped = setOf("v1.8.9.3"))) }
    @Test fun highestVersionWinsRegardlessOfApiOrder() { assertEquals("v1.10.0", select(release(), release("v1.10.0"), release("v1.8.10"))?.tag_name) }
    @Test fun upstreamReleaseCannotUpdateHammel() { assertNull(select(release().copy(html_url = "https://github.com/deniscerri/ytdlnis/releases/tag/v1.8.9.3"))) }
    @Test fun upstreamAssetCannotUpdateHammel() {
        val r = release()
        r.assets = listOf(r.assets.first().copy(browser_download_url = "https://github.com/deniscerri/ytdlnis/releases/download/v1.8.9.3/app.apk"))
        assertNull(select(r))
    }
    @Test fun wrongTagAssetIsNotOffered() {
        val r = release()
        r.assets = listOf(r.assets.first().copy(browser_download_url = "$base/download/v1.8.9.2/app.apk"))
        assertNull(select(r))
    }
    @Test fun universalApkIsValidFallback() { assertNotNull(select(release(abi = "universal"))) }
    @Test fun unsupportedAbiIsNotOffered() { assertNull(select(release(abi = "x86"))) }
    @Test fun matchingAbiPreferredOverUniversal() {
        val r = release(abi = "universal")
        r.assets = r.assets + release().assets
        assertTrue(AppUpdatePolicy.selectAsset(r, listOf("arm64-v8a"), "github", true)!!.name.contains("arm64-v8a"))
    }
    @Test fun noCrossFlavorUpdates() { assertNull(select(release(flavor = "foss-"))) }
    @Test fun noCrossBuildTypeUpdates() { assertNull(select(release(build = "release"))) }
    @Test fun noApkMeansNoUpdatePrompt() { assertNull(select(release().copy(assets = emptyList()))) }
    @Test fun playPreviewWithSameNumberDoesNotLoop() {
        assertNull(select(release("hammel-1.8.9.2-play-preview.1", flavor = "foss-").copy(prerelease = true), preview = true))
    }
    private fun installable(pkg: String = "com.nashmi233.downloader", code: Long = 108090300,
        version: String = "1.8.9.3", tag: String = "hammel-1.8.9.3") =
        AppUpdatePolicy.isInstallable("com.nashmi233.downloader", 108090204, "1.8.9.2", pkg, code, version, tag)
    @Test fun validArchiveCanInstall() { assertTrue(installable()) }
    @Test fun foreignPackageCannotInstall() { assertFalse(installable(pkg = "com.deniscerri.ytdl")) }
    @Test fun staleArchiveCodeCannotInstall() { assertFalse(installable(code = 108090204)) }
    @Test fun mislabeledReleaseCannotInstall() { assertFalse(installable(version = "1.8.9.2")) }
    @Test fun sameVersionWithDifferentAbiCodeCannotLoop() { assertFalse(installable(code = 108090205, version = "1.8.9.2", tag = "v1.8.9.2")) }
}
