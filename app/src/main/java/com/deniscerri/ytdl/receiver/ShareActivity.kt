package com.deniscerri.ytdl.receiver

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
import com.google.android.material.button.MaterialButton
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

class ShareActivity : BaseActivity() {

    lateinit var context: Context
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController
    private var quickDownload = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        // A normal transparent activity is enough for the Android share flow.
        // Do not use application overlay windows here; they are unnecessary and
        // can cause permission/review problems on Play-distributed builds.
        window.setBackgroundDrawable(ColorDrawable(0))
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        setContentView(R.layout.activity_share)

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
        navController.addOnDestinationChangedListener(object : NavController.OnDestinationChangedListener {
            @SuppressLint("RestrictedApi")
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?
            ) {
                navController.removeOnDestinationChangedListener(this)
                CoroutineScope(SupervisorJob()).launch {
                    navController.currentBackStack.collectLatest {
                        if (it.isEmpty()) {
                            this@ShareActivity.finish()
                        }
                    }
                }
            }
        })

        val action = intent.action
        Log.d("ShareActivity", intent.toString())
        if (Intent.ACTION_SEND != action && Intent.ACTION_VIEW != action) {
            finish()
            return
        }

        if (intent.getStringExtra(Intent.EXTRA_TEXT) == null && Intent.ACTION_SEND == action) {
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

        val data = when (action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> intent.dataString
        }

        if (data.isNullOrBlank()) {
            Toast.makeText(this, "لم يتم العثور على رابط صالح", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val inputQuery = data.extractURL()
        if (inputQuery.isBlank()) {
            Toast.makeText(this, "لم يتم العثور على رابط صالح", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val ai = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        val type = intent.getStringExtra("TYPE")
        val requestedBackground = intent.getBooleanExtra(
            "BACKGROUND",
            ai.metaData?.getBoolean("quick_run_background", false) == true
        )
        val runImmediately = requestedBackground || quickDownload

        lifecycleScope.launch {
            val existingResults = withContext(Dispatchers.IO) {
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
                !runImmediately &&
                sharedPreferences.getBoolean("ask_tiktok_download_type", true)

            if (shouldAskTikTok) {
                showTikTokDownloadChoices(result, inputQuery)
                return@launch
            }

            val downloadType = type
                ?.let { runCatching { DownloadType.valueOf(it) }.getOrNull() }
                ?: downloadViewModel.getDownloadType(url = result.url)

            continueDownload(result, downloadType, runImmediately)
        }
    }

    private fun showTikTokDownloadChoices(result: ResultItem, inputQuery: String) {
        val chooserView = layoutInflater.inflate(R.layout.dialog_tiktok_download_choices, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("ماذا تريد تحميله؟")
            .setMessage("اختر نوع التنزيل من تيك توك")
            .setView(chooserView)
            .setNegativeButton("إلغاء") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()

        chooserView.findViewById<MaterialButton>(R.id.tiktok_video_button).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                continueDownload(result, DownloadType.video, false)
            }
        }

        chooserView.findViewById<MaterialButton>(R.id.tiktok_audio_button).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                continueDownload(result, DownloadType.audio, false)
            }
        }

        chooserView.findViewById<MaterialButton>(R.id.tiktok_image_button).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                downloadTikTokCover(result, inputQuery)
            }
        }

        dialog.show()
    }

    private suspend fun continueDownload(
        result: ResultItem,
        downloadType: DownloadType,
        background: Boolean
    ) {
        if (sharedPreferences.getBoolean("download_card", true) && !background) {
            downloadCardViewModel.setResultItem(result)
            downloadCardViewModel.setDownloadItem(null)
            val bundle = Bundle()
            bundle.putSerializable("type", downloadType)
            navController.setGraph(R.navigation.share_nav_graph, bundle)
        } else {
            Toast.makeText(
                this@ShareActivity,
                "جارٍ تجهيز التنزيل",
                Toast.LENGTH_SHORT
            ).show()

            withContext(Dispatchers.IO) {
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
                finish()
                return
            }

            val legacySavedFile = withContext(Dispatchers.IO) {
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
                    val mimeType = when (extension) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    val bytes = body.bytes()

                    val baseName = result.title
                        .ifBlank { "TikTok_${System.currentTimeMillis()}" }
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        .take(80)
                    val fileName = "$baseName.$extension"
                    val showInGallery = sharedPreferences.getBoolean("save_to_gallery", true)

                    if (showInGallery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "${Environment.DIRECTORY_DOWNLOADS}/Hammel/Images"
                            )
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }

                        val uri = contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        ) ?: throw IllegalStateException("تعذر إنشاء ملف الصورة")

                        try {
                            contentResolver.openOutputStream(uri)?.use { output ->
                                output.write(bytes)
                            } ?: throw IllegalStateException("تعذر حفظ الصورة")

                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(uri, values, null, null)
                        } catch (e: Exception) {
                            contentResolver.delete(uri, null, null)
                            throw e
                        }
                        null
                    } else {
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
                        File(directory, fileName).also { file ->
                            file.outputStream().use { output -> output.write(bytes) }
                        }
                    }
                }
            }

            if (
                legacySavedFile != null &&
                sharedPreferences.getBoolean("save_to_gallery", true)
            ) {
                FileUtil.scanMedia(listOf(legacySavedFile.absolutePath), this@ShareActivity)
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
            finish()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        startActivity(Intent(this, MainActivity::class.java))
        super.onConfigurationChanged(newConfig)
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
