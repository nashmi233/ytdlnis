package com.deniscerri.ytdl

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.core.RuntimeManager
import com.deniscerri.ytdl.core.models.ExecuteException
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.receiver.ShareActivity
import com.deniscerri.ytdl.util.BgUtilsPoTokenGeneratorUtil
import com.deniscerri.ytdl.util.Extensions.extractURL
import com.deniscerri.ytdl.util.Extensions.hasReachedEnd
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.ObserveAlarmScheduler
import com.deniscerri.ytdl.util.ThemeUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class App : Application(), DefaultLifecycleObserver {

    val isForegroundLaunch = CompletableDeferred<Boolean>()

    override fun onStart(owner: LifecycleOwner) {
        if (!isForegroundLaunch.isCompleted) {
            isForegroundLaunch.complete(true)
        }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        instance = this

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@App)
        setDefaultValues()
        sharedPreferences.edit {
            putString("app_language", "ar")
        }
        registerClipboardLinkHandler()

        applicationScope = CoroutineScope(SupervisorJob())
        applicationScope.launch(Dispatchers.IO) {
            try {
                createNotificationChannels()
                initLibraries()

                val appVer = sharedPreferences.getString("version", "")!!
                if(appVer.isEmpty() || appVer != BuildConfig.VERSION_NAME){
                    sharedPreferences.edit(commit = true){
                        putString("version", BuildConfig.VERSION_NAME)
                    }
                }

                val db = DBManager.getInstance(this@App)
                val scheduler = ObserveAlarmScheduler(this@App)
                db.observeSourcesDao.getAllSources()
                    .filter { it.status == ObserveSourcesRepository.SourceStatus.ACTIVE && !it.hasReachedEnd() }
                    .forEach { scheduler.schedule(it) }

                delay(300)
                if (!isForegroundLaunch.isCompleted) {
                    isForegroundLaunch.complete(false)
                    val useBgUtilPoTokenServer = sharedPreferences.getBoolean("use_bgutils_potoken_generator", false)
                    val bgUtilsMethod = sharedPreferences.getString("bgutils_potoken_method", "generation_script")
                    if (useBgUtilPoTokenServer && bgUtilsMethod == "server") {
                        BgUtilsPoTokenGeneratorUtil.runServer(this@App)
                    }
                }
            }catch (e: Exception){
                Looper.prepare().runCatching {
                    Toast.makeText(this@App, e.message, Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
        ThemeUtil.init(this)
    }

    private fun registerClipboardLinkHandler() {
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                if (activity !is MainActivity) return

                val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
                val detectLink = preferences.getBoolean("auto_detect_clipboard", true)
                val autoDownload = preferences.getBoolean("auto_download_copied_link", false)
                if (!detectLink && !autoDownload) return

                val clipboard = activity.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
                val rawText = clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(activity)
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                if (rawText.isBlank()) return
                val url = runCatching { rawText.extractURL() }.getOrDefault("").trim()

                if (url.isBlank() || !Patterns.WEB_URL.matcher(url).matches()) {
                    preferences.edit { remove("last_auto_clipboard_url") }
                    return
                }

                val lastUrl = preferences.getString("last_auto_clipboard_url", "").orEmpty()
                if (lastUrl == url) return

                preferences.edit { putString("last_auto_clipboard_url", url) }

                val shareIntent = Intent(activity, ShareActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                    putExtra("BACKGROUND", autoDownload)
                }
                activity.startActivity(shareIntent)
            }
        })
    }

    @Throws(ExecuteException::class)
    private fun initLibraries() {
        RuntimeManager.getInstance().init(this)
    }

    private fun setDefaultValues(){
        val SPL = 2
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        if (sp.getInt("spl", 0) != SPL) {
            PreferenceManager.setDefaultValues(this, R.xml.root_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.downloading_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.general_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.processing_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.folders_preference, true)
            PreferenceManager.setDefaultValues(this, R.xml.updating_preferences, true)
            PreferenceManager.setDefaultValues(this, R.xml.advanced_preferences, true)
            sp.edit().putInt("spl", SPL).apply()
        }
    }

    private fun createNotificationChannels() {
        val notificationUtil = NotificationUtil(this)
        notificationUtil.createNotificationChannel()
    }

    companion object {
        private const val TAG = "App"
        private lateinit var applicationScope: CoroutineScope
        lateinit var instance: App
    }
}
