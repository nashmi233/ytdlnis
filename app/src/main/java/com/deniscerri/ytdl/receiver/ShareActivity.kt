package com.deniscerri.ytdl.receiver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
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
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.SharedLinkPolicy
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        // Receiving a share is a normal activity; no overlay permission/window is needed.
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.activity_share)

        context = baseContext
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(this)[DownloadCardViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        cookieViewModel.updateCookiesFile()
        if (savedInstanceState?.getBoolean("share_consumed") == true) {
            finish()
            return
        }
        handleIntents(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("share_consumed", intent.action == null)
        super.onSaveInstanceState(outState)
    }

    private var requestedSharePermissions = false

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && (Build.VERSION.SDK_INT >= 30 ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            handleIntents(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent) {
        val needsStorage = Build.VERSION.SDK_INT < 30 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        val needsNotifications = Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (!requestedSharePermissions && (needsStorage || needsNotifications)) {
            requestedSharePermissions = true
            askPermissions()
            return
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        navController = navHostFragment.findNavController()
        val action = intent.action
        if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) {
            val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: intent.clipData?.let { clip ->
                    (0 until clip.itemCount).mapNotNull { index ->
                        val item = clip.getItemAt(index)
                        item.text?.toString() ?: item.uri?.toString()
                    }.joinToString("\n")
                }
            if (sharedText.isNullOrBlank() && action == Intent.ACTION_SEND && intent.hasExtra(Intent.EXTRA_STREAM)) {
                intent.setClass(this, MainActivity::class.java)
                startActivity(intent)
                finish()
                return
            }
            val inputQuery = SharedLinkPolicy.extract(if (action == Intent.ACTION_SEND) sharedText else intent.dataString)
            if (inputQuery == null) {
                Toast.makeText(this, "لم أجد رابط فيديو صالحًا في المشاركة", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            runCatching { supportFragmentManager.popBackStack() }

            val ai = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)

            val type = intent.getStringExtra("TYPE")
            val background = SharedLinkPolicy.downloadImmediately(
                action == Intent.ACTION_SEND, type,
                intent.getBooleanExtra("BACKGROUND", ai.metaData?.getBoolean("quick_run_background", false) == true)
            )

            lifecycleScope.launch {
                val existingResults = withContext(Dispatchers.IO){
                    resultViewModel.getAllByURL(inputQuery)
                }

                val result = existingResults.firstOrNull()
                    ?: downloadViewModel.createEmptyResultItem(inputQuery)

                val isTikTok = inputQuery.contains("tiktok.com", ignoreCase = true)
                val shouldAskTikTok = isTikTok &&
                    type == null &&
                    !background &&
                    sharedPreferences.getBoolean("ask_tiktok_download_type", true)

                if (shouldAskTikTok) {
                    showTikTokDownloadChoices(result, inputQuery)
                    return@launch
                }

                val downloadType = SharedLinkPolicy.downloadType(
                    action == Intent.ACTION_SEND, type, downloadViewModel.getDownloadType(url = result.url)
                )
                continueDownload(result, inputQuery, downloadType, background)
            }
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
                continueDownload(result, inputQuery, DownloadType.video, false)
            }
        }

        chooserView.findViewById<MaterialButton>(R.id.tiktok_audio_button).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                continueDownload(result, inputQuery, DownloadType.audio, false)
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

            try {
                val queued = withContext(Dispatchers.IO) {
                    val downloadItem = downloadViewModel.createDownloadItemFromResult(
                        result = result,
                        givenType = downloadType
                    )
                    downloadViewModel.queueDownloads(listOf(downloadItem))
                }
                intent.action = null
                val message = if (queued.duplicateDownloadIDs.isNotEmpty()) {
                    "هذا الفيديو موجود مسبقًا في التنزيلات"
                } else queued.message.ifBlank { "بدأ تنزيل الفيديو" }
                Toast.makeText(this@ShareActivity, message, Toast.LENGTH_SHORT).show()
                this@ShareActivity.finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@ShareActivity, e.message ?: "تعذر بدء التنزيل", Toast.LENGTH_LONG).show()
                finish()
            }
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
