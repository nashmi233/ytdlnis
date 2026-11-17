package com.deniscerri.ytdl

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deniscerri.ytdl.database.models.GithubRelease
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

@RunWith(AndroidJUnit4::class)
class AppUpdateFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val release = GithubRelease("https://github.com/nashmi233/ytdlnis/releases/tag/nash-1.8.9.8", "nash-1.8.9.8", "", Date(0), emptyList())

    @Test fun repeatedStartsDownloadOnceAndLateProgressCannotUndoCompletion() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<File>()
        var calls = 0
        var progress: ((Long) -> Unit)? = null
        val source = object : AppUpdateSource {
            override suspend fun findRelease() = Result.success(release)
            override suspend fun download(release: GithubRelease, progressCallback: (Long) -> Unit): Result<File> {
                calls++
                progress = progressCallback
                started.complete(Unit)
                return Result.success(finish.await())
            }
        }
        lateinit var model: AppUpdateViewModel
        val store = ViewModelStore()
        instrumentation.runOnMainSync {
            model = AppUpdateViewModel(app, source)
            store.put("update", model)
            model.start()
            model.start()
        }
        withTimeout(5000) { started.await() }
        val file = File(app.cacheDir, "update-test-only.apk").apply { writeText("not an installable APK") }
        try {
            instrumentation.runOnMainSync {
                progress!!(37)
                assertEquals(37, model.state.value!!.percent)
                assertEquals(AppUpdateViewModel.Phase.DOWNLOADING, model.state.value!!.phase)
            }
            finish.complete(file)
            repeat(100) {
                instrumentation.waitForIdleSync()
                if (model.state.value?.phase != AppUpdateViewModel.Phase.READY) android.os.SystemClock.sleep(20)
            }
            instrumentation.runOnMainSync {
                assertEquals(AppUpdateViewModel.Phase.READY, model.state.value!!.phase)
                progress!!(99)
                assertEquals(AppUpdateViewModel.Phase.READY, model.state.value!!.phase)
                assertEquals(file, model.state.value!!.file)
                model.start()
                assertEquals(1, calls)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            file.delete()
        }
        Unit
    }

    @Test fun rejectedUpdateNeverBecomesReadyForInstallation() {
        val source = object : AppUpdateSource {
            override suspend fun findRelease() = Result.success(release)
            override suspend fun download(release: GithubRelease, progress: (Long) -> Unit): Result<File> =
                Result.failure(IllegalStateException("signature mismatch"))
        }
        instrumentation.runOnMainSync {
            val model = AppUpdateViewModel(app, source)
            val store = ViewModelStore()
            store.put("update", model)
            model.start()
            assertEquals(AppUpdateViewModel.Phase.ERROR, model.state.value!!.phase)
            assertNull(model.state.value!!.file)
            store.clear()
        }
    }
}
