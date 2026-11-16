package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import android.os.Build
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.YTDLRequest
import com.deniscerri.ytdl.core.packages.PackageBase.Companion.sharedClient
import com.deniscerri.ytdl.database.models.GithubRelease
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException


class UpdateUtil(var context: Context) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val channelMap = mapOf(
        Pair<String, RuntimeManager.UpdateChannel>("stable", RuntimeManager.UpdateChannel.STABLE),
        Pair<String, RuntimeManager.UpdateChannel>("nightly", RuntimeManager.UpdateChannel.NIGHTLY),
        Pair<String, RuntimeManager.UpdateChannel>("master", RuntimeManager.UpdateChannel.MASTER)
    )

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun tryGetNewVersion(): Result<GithubRelease> {
        return try {
            val releases = fetchGithubReleases()
            val skipped = sharedPreferences.getString("skip_updates", "")
                .orEmpty().split(",").toSet()
            val release = AppUpdatePolicy.selectRelease(
                releases, BuildConfig.VERSION_NAME,
                sharedPreferences.getBoolean("update_beta", false), skipped,
                Build.SUPPORTED_ABIS.toList(), BuildConfig.FLAVOR, BuildConfig.DEBUG
            )
            if (release == null) {
                NotificationUtil(context).cancelAppUpdate()
                Result.failure(IllegalStateException(context.getString(R.string.you_are_in_latest_version)))
            } else {
                Result.success(release)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGithubReleases(): List<GithubRelease> =
        runCatching { fetchGithubReleases() }.getOrDefault(emptyList())

    private fun fetchGithubReleases(): List<GithubRelease> {
        val conn = URL(AppUpdatePolicy.RELEASES_API).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 5000
            check(conn.responseCode in 200..299) { context.getString(R.string.network_error) }
            val type = object : TypeToken<List<GithubRelease>>() {}.type
            return InputStreamReader(conn.inputStream).use { reader ->
                Gson().fromJson<List<GithubRelease>>(reader, type) ?: emptyList()
            }
        } finally {
            conn.disconnect()
        }
    }

    data class YTDLPUpdateResponse (
        val status: YTDLPUpdateStatus,
        val message: String = ""
    )

    enum class YTDLPUpdateStatus {
        DONE, ALREADY_UP_TO_DATE, PROCESSING, ERROR
    }

    suspend fun updateYTDL(c: String? = null) : YTDLPUpdateResponse =
        withContext(Dispatchers.IO){
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (updatingYTDL) {
                YTDLPUpdateResponse(YTDLPUpdateStatus.PROCESSING)
            }

            updatingYTDL = true

            val channel = if (c.isNullOrBlank()) sharedPreferences.getString("ytdlp_source", "stable") else c

            when(channel) {
                "stable", "nightly", "master" -> {
                    val res = RuntimeManager.getInstance().updateYTDL(context, channelMap[channel]!!)
                    if (res != RuntimeManager.UpdateStatus.DONE) {
                        YTDLPUpdateResponse(YTDLPUpdateStatus.ALREADY_UP_TO_DATE)
                    }else {
                        val version = RuntimeManager.getInstance().version(context)
                        YTDLPUpdateResponse(YTDLPUpdateStatus.DONE, "Updated yt-dlp to ${channel}@${version}")
                    }
                }
                else -> {
                    val request = YTDLRequest(emptyList())
                    request.addOption("--update-to", "$channel")

                    val res = RuntimeManager.getInstance().execute(request)
                    val out = res.out.lines().last { it.isNotBlank() }

                    if (out.contains("ERROR")) YTDLPUpdateResponse(YTDLPUpdateStatus.ERROR, out)
                    if (out.contains("yt-dlp is up to date")) YTDLPUpdateResponse(YTDLPUpdateStatus.ALREADY_UP_TO_DATE, out)
                    else YTDLPUpdateResponse(YTDLPUpdateStatus.DONE, out)
                }
            }


    }

    @SuppressLint("Range", "UnspecifiedRegisterReceiverFlag")
    suspend fun downloadReleaseApk(release: GithubRelease, onProgress: (Long) -> Unit) : Result<File> {
        return withContext(Dispatchers.IO) {
            var tempApk: File? = null
            try {
                val releaseVersion = AppUpdatePolicy.selectAsset(
                    release, Build.SUPPORTED_ABIS.toList(), BuildConfig.FLAVOR, BuildConfig.DEBUG
                ) ?: error("لا يوجد ملف تحديث متوافق مع نسخة حمّل المثبتة")
                val incoming = AppUpdatePolicy.version(release.tag_name)
                val current = AppUpdatePolicy.version(BuildConfig.VERSION_NAME)
                require(incoming != null && current != null && incoming > current) {
                    context.getString(R.string.you_are_in_latest_version)
                }
                File(FileUtil.getDefaultApksPath()).mkdirs()
                // Unique staging file: cancelled/failed downloads are never offered for installation.
                tempApk = File.createTempFile("hammel-update-", ".apk", File(FileUtil.getDefaultApksPath()))
                val downloadFile = requireNotNull(tempApk)

                //download
                val request = Request.Builder()
                    .url(releaseVersion.browser_download_url)
                    .build()

                sharedClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { context.getString(R.string.network_error) }
                    val body = response.body
                    val totalBytes = body.contentLength()
                    var bytesDownloaded = 0L

                    body.byteStream().use { inputStream ->
                        downloadFile.outputStream().use { outputStream ->
                            val buffer = ByteArray(8192) // 8KB buffer
                            var bytesRead: Int

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) throw CancellationException("Download cancelled by user")

                                outputStream.write(buffer, 0, bytesRead)
                                bytesDownloaded += bytesRead

                                // Calculate percentage
                                val progress = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).coerceIn(0, 100) else 0L
                                onProgress(progress)
                            }
                        }
                    }

                    check(totalBytes < 0 || bytesDownloaded == totalBytes) { "ملف التحديث غير مكتمل" }
                }
                validateDownloadedApk(downloadFile, release.tag_name)
                Result.success(downloadFile)
            } catch (e: CancellationException) {
                tempApk?.delete()
                throw e
            } catch (e: Exception) {
                tempApk?.delete()
                Result.failure(e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(file: File, advertisedTag: String) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, flags)
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("تعذر قراءة ملف التحديث")
        require(AppUpdatePolicy.isInstallable(
            installed.packageName, PackageInfoCompat.getLongVersionCode(installed),
            installed.versionName.orEmpty(), archive.packageName,
            PackageInfoCompat.getLongVersionCode(archive), archive.versionName.orEmpty(), advertisedTag
        )) { "ملف التحديث لا يخص حمّل أو ليس إصدارًا أحدث من المثبت" }

        val compatibleSignature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val oldSigning = installed.signingInfo
            val newSigning = archive.signingInfo
            if (oldSigning == null || newSigning == null) false
            else if (oldSigning.hasMultipleSigners() || newSigning.hasMultipleSigners()) {
                oldSigning.apkContentsSigners.toSet() == newSigning.apkContentsSigners.toSet()
            } else {
                newSigning.signingCertificateHistory?.toSet()
                    ?.containsAll(oldSigning.apkContentsSigners.toSet()) == true
            }
        } else {
            val installedSignatures = installed.signatures
            !installedSignatures.isNullOrEmpty() &&
                installedSignatures.toSet() == archive.signatures?.toSet()
        }
        require(compatibleSignature) { "توقيع التحديث مختلف عن النسخة المثبتة؛ تعذر تحديثها مع الاحتفاظ بالبيانات" }
    }

    companion object {
        var updatingYTDL = false
        var updatingApp = false
    }
}
