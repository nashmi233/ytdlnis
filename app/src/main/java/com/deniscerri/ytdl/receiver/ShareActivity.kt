package com.deniscerri.ytdl.receiver

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.util.Extensions.extractURL
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.properties.Delegates


class ShareActivity : BaseActivity() {

    lateinit var context: Context
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController
    private var quickDownload by Delegates.notNull<Boolean>()

    private lateinit var wm: WindowManager
    private lateinit var myView: View


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        if (Settings.canDrawOverlays(this)){
            val params = WindowManager.LayoutParams(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                PixelFormat.TRANSLUCENT
            )
            wm = getSystemService(WINDOW_SERVICE) as WindowManager

            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            myView = inflater.inflate(R.layout.activity_share, null)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            wm.addView(myView, params)
            setContentView(R.layout.activity_share)

        }else{
            window.run {
                setBackgroundDrawable(ColorDrawable(0))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                } else {
                    setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                }
            }

            setContentView(R.layout.activity_share)
        }

        context = baseContext
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(this)[DownloadCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        cookieViewModel.updateCookiesFile()
        handleIntents(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent) {
        askPermissions()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        navController = navHostFragment.findNavController()
        navController.addOnDestinationChangedListener(object: NavController.OnDestinationChangedListener{
            @SuppressLint("RestrictedApi")
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?
            ) {
                navController.removeOnDestinationChangedListener(this)
                CoroutineScope(SupervisorJob()).launch {
                    navController.currentBackStack.collectLatest {
                        if (it.isEmpty()){
                            this@ShareActivity.finish()
                        }
                    }
                }
            }
        })

        val action = intent.action
        Log.e("aa", intent.toString())
        if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) == null && Intent.ACTION_SEND == action){
                intent.setClass(this, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()
                return
            }

            runCatching { supportFragmentManager.popBackStack() }

            quickDownload = intent.getBooleanExtra(
                "quick_download",
                sharedPreferences.getBoolean("quick_download", false) ||
                    sharedPreferences.getString("preferred_download_type", "video") == "command"
            )

            val data = when(action){
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)!!
                else -> intent.dataString!!
            }

            val inputQuery = data.extractURL()
            val ai = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)

            val type = intent.getStringExtra("TYPE")
            val background = intent.getBooleanExtra(
                "BACKGROUND",
                ai.metaData?.getBoolean("quick_run_background", false) == true
            )

            lifecycleScope.launch {
                val existingResults = withContext(Dispatchers.IO){
                    resultViewModel.getAllByURL(inputQuery)
                }

                val result = if (existingResults.size == 1) {
                    existingResults.first()
                } else {
                    if (existingResults.size > 1) resultViewModel.deleteAll()
                    downloadViewModel.createEmptyResultItem(inputQuery)
                }

                val isTikTok = inputQuery.contains("tiktok.com", ignoreCase = true)
                val shouldAskTikTok = isTikTok &&
                    type == null &&
                    !background &&
                    sharedPreferences.getBoolean("ask_tiktok_download_type", true)

                if (shouldAskTikTok) {
                    showTikTokDownloadChoices(result, inputQuery)
                    return@launch
                }

                val downloadType = DownloadType.valueOf(
                    type ?: downloadViewModel.getDownloadType(url = result.url).toString()
                )
                continueDownload(result, inputQuery, downloadType, background)
            }
        }
    }

    private fun showTikTokDownloadChoices(result: ResultItem, inputQuery: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("ماذا تريد تحميله؟")
            .setMessage("اختر نوع التنزيل من تيك توك")
            .setItems(arrayOf("فيديو كامل", "صوت فقط", "صورة الغلاف")) { _, which ->
                when (which) {
                    0 -> lifecycleScope.launch {
                        continueDownload(result, inputQuery, DownloadType.video, false)
                    }
                    1 -> lifecycleScope.launch {
                        continueDownload(result, inputQuery, DownloadType.audio, false)
                    }
                    2 -> lifecycleScope.launch {
                        downloadTikTokCover(result, inputQuery)
                    }
                }
            }
            .setNegativeButton("إلغاء") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private suspend fun continueDownload(
        result: ResultItem,
        inputQuery: String,
        downloadType: DownloadType,
        background: Boolean
    ) {
        if (sharedPreferences.getBoolean("download_card", true) && !background){
            downloadCardViewModel.setResultItem(result)
            downloadCardViewModel.setDownloadItem(null)
            val bundle = Bundle()
            bundle.putSerializable("type", downloadType)
            navController.setGraph(R.navigation.share_nav_graph, bundle)
        }else{
            Toast.makeText(
                this@ShareActivity,
                "جارٍ تجهيز التنزيل",
                Toast.LENGTH_SHORT
            ).show()

            withContext(Dispatchers.IO){
                val downloadItem = downloadViewModel.createDownloadItemFromResult(
                    result = result,
                    givenType = downloadType
                )
                downloadViewModel.queueDownloads(listOf(downloadItem))
            }
            this@ShareActivity.finish()
        }
    }

    private suspend fun downloadTikTokCover(initialResult: ResultItem, inputQuery: String) {
        try {
            val result = if (initialResult.thumb.isBlank()) {
                withContext(Dispatchers.IO) {
                    resultViewModel.repository
                        .getResultsFromSource(inputQuery, true)
                        .filterNotNull()
                        .firstOrNull()
                } ?: initialResult
            } else {
                initialResult
            }

            if (result.thumb.isBlank()) {
                Toast.makeText(
                    this@ShareActivity,
                    "لم أجد صورة لهذا الرابط",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val savedFile = withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val request = Request.Builder().url(result.thumb).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("تعذر تحميل الصورة")
                    }

                    val body = response.body
                    val subtype = body.contentType()?.subtype?.lowercase().orEmpty()
                    val extension = when {
                        subtype.contains("png") -> "png"
                        subtype.contains("webp") -> "webp"
                        else -> "jpg"
                    }

                    val showInGallery = sharedPreferences.getBoolean("save_to_gallery", true)
                    val directory = if (showInGallery) {
                        File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Hammel/Images"
                        )
                    } else {
                        File(
                            getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir,
                            "Images"
                        )
                    }
                    directory.mkdirs()

                    val baseName = result.title
                        .ifBlank { "TikTok_${System.currentTimeMillis()}" }
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        .take(80)

                    val file = File(directory, "$baseName.$extension")
                    file.outputStream().use { output ->
                        output.write(body.bytes())
                    }
                    file
                }
            }

            if (sharedPreferences.getBoolean("save_to_gallery", true)) {
                FileUtil.scanMedia(listOf(savedFile.absolutePath), this@ShareActivity)
            }

            Toast.makeText(
                this@ShareActivity,
                "تم حفظ الصورة",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(
                this@ShareActivity,
                e.message ?: "تعذر تحميل الصورة",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        startActivity(Intent(this, MainActivity::class.java))
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wm.isInitialized && ::myView.isInitialized) {
            try {
                wm.removeView(myView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )
    }
}
