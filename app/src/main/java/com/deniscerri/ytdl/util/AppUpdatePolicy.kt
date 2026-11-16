package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.database.models.GithubReleaseAsset

/** Rules shared by foreground checks, background checks and APK downloads. */
internal object AppUpdatePolicy {
    const val REPOSITORY = "nashmi233/ytdlnis"
    const val RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases"
    private const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
    private val versionPattern = Regex(
        "^(?:hammel-|v)?(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([A-Za-z][A-Za-z0-9.-]*))?$",
        RegexOption.IGNORE_CASE
    )

    data class Version(val parts: List<Int>, val preview: Boolean) : Comparable<Version> {
        // A channel suffix does not create a new Android build. Every update must
        // increase one of the four numeric components used by versionCode.
        override fun compareTo(other: Version): Int {
            parts.indices.forEach { index ->
                val comparison = parts[index].compareTo(other.parts[index])
                if (comparison != 0) return comparison
            }
            return 0
        }
    }

    fun version(value: String): Version? {
        val match = versionPattern.matchEntire(value.trim()) ?: return null
        val parts = (1..4).map { index ->
            val part = match.groupValues[index]
            if (part.isEmpty()) 0 else part.toIntOrNull() ?: return null
        }
        // Match the base-100 minor/patch/build fields in app/build.gradle.
        if (parts.drop(1).any { it !in 0..99 }) return null
        return Version(parts, match.groupValues[5].isNotEmpty())
    }

    fun selectAsset(
        release: GithubRelease,
        supportedAbis: List<String>,
        flavor: String,
        debug: Boolean
    ): GithubReleaseAsset? {
        if (!release.html_url.startsWith("$RELEASES_URL/tag/")) return null
        val buildType = if (debug) "debug" else "release"
        val compatible = release.assets.filter { asset ->
            val name = asset.name
            (name.startsWith("NashmiDownloader-") || name.startsWith("Hammel-", true)) &&
                asset.browser_download_url.startsWith("$RELEASES_URL/download/${release.tag_name}/") &&
                name.endsWith("-$buildType.apk") &&
                (name.contains("-foss-") == (flavor != "github"))
        }
        // Prefer the device's first ABI; fall back to the universal APK.
        for (abi in supportedAbis) {
            compatible.firstOrNull { it.name.endsWith("-$abi-$buildType.apk") }?.let { return it }
        }
        return compatible.firstOrNull { it.name.endsWith("-universal-$buildType.apk") }
    }

    fun selectRelease(
        releases: List<GithubRelease>,
        installedVersion: String,
        allowPreview: Boolean,
        skippedTags: Set<String>,
        supportedAbis: List<String>,
        flavor: String,
        debug: Boolean
    ): GithubRelease? {
        val current = version(installedVersion) ?: return null
        return releases.mapNotNull { release ->
            val incoming = version(release.tag_name) ?: return@mapNotNull null
            if (release.draft || release.tag_name in skippedTags || incoming <= current ||
                (!allowPreview && (release.prerelease || incoming.preview)) ||
                selectAsset(release, supportedAbis, flavor, debug) == null
            ) return@mapNotNull null
            release to incoming
        }.maxByOrNull { it.second }?.first
    }

    fun isInstallable(
        installedPackage: String,
        installedCode: Long,
        installedVersion: String,
        archivePackage: String,
        archiveCode: Long,
        archiveVersion: String,
        advertisedTag: String
    ): Boolean {
        val current = version(installedVersion) ?: return false
        val incoming = version(archiveVersion) ?: return false
        val advertised = version(advertisedTag) ?: return false
        return archivePackage == installedPackage && archiveCode > installedCode &&
            incoming > current && incoming.compareTo(advertised) == 0
    }
}
