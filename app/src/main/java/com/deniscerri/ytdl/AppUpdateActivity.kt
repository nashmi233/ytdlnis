package com.deniscerri.ytdl

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Non-exported destination: tapping our notification authorizes this download. */
class AppUpdateActivity : AppCompatActivity() {
    private val model: AppUpdateViewModel by viewModels()
    private var installationOffered = false
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
            installDownloadedUpdate()
        } else {
            findViewById<TextView>(R.id.update_status).setText(R.string.nash_allow_install)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        setContentView(R.layout.activity_app_update)
        installationOffered = savedInstanceState?.getBoolean("installationOffered") ?: false
        NotificationUtil(this).cancelAppUpdate()
        val status = findViewById<TextView>(R.id.update_status)
        val progress = findViewById<ProgressBar>(R.id.update_progress)
        val action = findViewById<MaterialButton>(R.id.update_action)
        findViewById<MaterialButton>(R.id.update_cancel).setOnClickListener { finish() }
        action.setOnClickListener {
            if (model.state.value?.phase == AppUpdateViewModel.Phase.READY) installDownloadedUpdate()
            else model.start()
        }
        model.state.observe(this) { state ->
            progress.isVisible = state.phase == AppUpdateViewModel.Phase.CHECKING || state.phase == AppUpdateViewModel.Phase.DOWNLOADING
            progress.isIndeterminate = state.phase == AppUpdateViewModel.Phase.CHECKING
            progress.progress = state.percent
            action.isVisible = state.phase == AppUpdateViewModel.Phase.READY || state.phase == AppUpdateViewModel.Phase.ERROR
            action.setText(if (state.phase == AppUpdateViewModel.Phase.READY) R.string.nash_install_update else R.string.nash_retry_update)
            status.text = when (state.phase) {
                AppUpdateViewModel.Phase.CHECKING -> getString(R.string.nash_checking_update)
                AppUpdateViewModel.Phase.DOWNLOADING -> getString(R.string.nash_downloading_update, state.percent)
                AppUpdateViewModel.Phase.READY -> getString(R.string.nash_update_ready)
                AppUpdateViewModel.Phase.ERROR -> getString(R.string.nash_update_failed)
                AppUpdateViewModel.Phase.CURRENT -> getString(R.string.you_are_in_latest_version)
            }
            if (state.phase == AppUpdateViewModel.Phase.READY && !installationOffered) {
                installationOffered = true
                installDownloadedUpdate()
            }
        }
        model.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("installationOffered", installationOffered)
        super.onSaveInstanceState(outState)
    }

    private fun installDownloadedUpdate() {
        val file = model.state.value?.file ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                findViewById<TextView>(R.id.update_status).setText(R.string.nash_allow_install)
                installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            findViewById<TextView>(R.id.update_status).setText(R.string.nash_update_install_failed)
        }
    }
}

/** Retains one download across rotation; leaving the screen cancels its job. */
internal interface AppUpdateSource {
    suspend fun findRelease(): Result<GithubRelease>
    suspend fun download(release: GithubRelease, progress: (Long) -> Unit): Result<File>
}

private class GithubAppUpdateSource(application: Application) : AppUpdateSource {
    private val updater = UpdateUtil(application)
    override suspend fun findRelease() = withContext(Dispatchers.IO) { updater.tryGetNewVersion() }
    override suspend fun download(release: GithubRelease, progress: (Long) -> Unit) = updater.downloadReleaseApk(release, progress)
}

class AppUpdateViewModel internal constructor(application: Application, private val source: AppUpdateSource) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, GithubAppUpdateSource(application))
    enum class Phase { CHECKING, DOWNLOADING, READY, ERROR, CURRENT }
    data class State(val phase: Phase, val percent: Int = 0, val file: File? = null)
    val state = MutableLiveData(State(Phase.CHECKING))
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true || state.value?.phase == Phase.READY) return
        job = viewModelScope.launch {
            state.value = State(Phase.CHECKING)
            // Fetch trusted release metadata again; never accept an APK URL from an intent.
            val check = source.findRelease()
            val release = check.getOrNull()
            if (release == null) {
                val current = check.exceptionOrNull()?.message == getApplication<Application>().getString(R.string.you_are_in_latest_version)
                state.value = State(if (current) Phase.CURRENT else Phase.ERROR)
                return@launch
            }
            state.value = State(Phase.DOWNLOADING)
            val result = source.download(release) { percent ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (state.value?.phase == Phase.DOWNLOADING) {
                        state.value = State(Phase.DOWNLOADING, percent.toInt())
                    }
                }
            }
            // Late progress is ignored once a terminal state has been published.
            result.fold(
                onSuccess = { state.value = State(Phase.READY, 100, it) },
                onFailure = { state.value = State(Phase.ERROR) }
            )
        }
    }
}
