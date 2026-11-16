package com.deniscerri.ytdl.ui.more

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.util.NavbarUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MoreFragment : Fragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var mainSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var downloadQueue: TextView
    private lateinit var downloads: TextView
    private lateinit var cookies: TextView
    private lateinit var observeSources: TextView
    private lateinit var terminateApp: TextView
    private lateinit var settings: TextView
    private lateinit var mainActivity: MainActivity
    private lateinit var downloadViewModel: DownloadViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        return inflater.inflate(R.layout.fragment_more, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainSharedPreferences =  PreferenceManager.getDefaultSharedPreferences(requireContext())
        mainSharedPreferencesEditor = mainSharedPreferences.edit()
        downloads = view.findViewById(R.id.downloads)
        downloadQueue = view.findViewById(R.id.download_queue)
        cookies = view.findViewById(R.id.cookies)
        observeSources = view.findViewById(R.id.observe_sources)
        terminateApp = view.findViewById(R.id.terminate)
        settings = view.findViewById(R.id.settings)

        bindQuickSettings(view)

        var showingDownloads = false
        var showingDownloadQueue = false

        NavbarUtil.getNavBarItems(requireContext()).apply {
            showingDownloads = any { n -> n.itemId == R.id.historyFragment && n.isVisible }
            showingDownloadQueue = any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }
        }

        downloads.isVisible = !showingDownloads
        downloadQueue.isVisible = !showingDownloadQueue

        downloads.setOnClickListener {
            findNavController().navigate(R.id.historyFragment)
        }

        downloadQueue.setOnClickListener {
            findNavController().navigate(R.id.downloadQueueMainFragment)
        }

        cookies.setOnClickListener {
            findNavController().navigate(R.id.cookiesFragment)
        }

        observeSources.setOnClickListener {
            findNavController().navigate(R.id.observeSourcesFragment)
        }

        terminateApp.setOnClickListener {
            showTerminateConfirmationDialog()
        }
        terminateApp.setOnLongClickListener {
            showTerminateConfirmationDialog(skipPreference = true)
            true
        }

        settings.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

    }

    override fun onResume() {
        super.onResume()
        view?.let { bindQuickSettings(it) }
    }

    private fun bindQuickSettings(view: View) {
        view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.auto_detect_clipboard).apply {
            setOnCheckedChangeListener(null)
            isChecked = mainSharedPreferences.getBoolean("auto_detect_clipboard", true)
            setOnCheckedChangeListener { _, checked ->
                mainSharedPreferences.edit().putBoolean("auto_detect_clipboard", checked).apply()
            }
        }
        view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.auto_download_copied_link).apply {
            setOnCheckedChangeListener(null)
            isChecked = mainSharedPreferences.getBoolean("auto_download_copied_link", false)
            setOnCheckedChangeListener { _, checked ->
                mainSharedPreferences.edit().putBoolean("auto_download_copied_link", checked).apply()
            }
        }
        view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.save_to_gallery).apply {
            setOnCheckedChangeListener(null)
            isChecked = mainSharedPreferences.getBoolean("save_to_gallery", true)
            setOnCheckedChangeListener { _, checked ->
                mainSharedPreferences.edit().putBoolean("save_to_gallery", checked).apply()
            }
        }
        view.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.ask_tiktok_download_type).apply {
            setOnCheckedChangeListener(null)
            isChecked = mainSharedPreferences.getBoolean("ask_tiktok_download_type", true)
            setOnCheckedChangeListener { _, checked ->
                mainSharedPreferences.edit().putBoolean("ask_tiktok_download_type", checked).apply()
            }
        }
    }

    fun showTerminateConfirmationDialog(skipPreference: Boolean = false) {
        val shouldAskToTerminate = mainSharedPreferences.getBoolean("ask_terminate_app", true)
        if (!shouldAskToTerminate && !skipPreference) {
            terminateApp.isEnabled = false
            terminateApp()
            return
        }

        var doNotShowAgainFinalState = !shouldAskToTerminate

        lateinit var dialog: AlertDialog
        val terminateDialog = MaterialAlertDialogBuilder(requireContext())
        terminateDialog.setTitle(getString(R.string.kill_app))
        val dialogView = layoutInflater.inflate(R.layout.dialog_terminate_app, null)
        val checkbox = dialogView.findViewById<CheckBox>(R.id.doNotShowAgain)
        terminateDialog.setView(dialogView)

        checkbox.isChecked = doNotShowAgainFinalState
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            doNotShowAgainFinalState = isChecked
        }

        terminateDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
            dialogInterface.cancel()
        }

        terminateDialog.setPositiveButton(getString(R.string.ok), null)
        dialog = terminateDialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.setCanceledOnTouchOutside(false)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            mainSharedPreferencesEditor.putBoolean("ask_terminate_app", !doNotShowAgainFinalState).commit()
            terminateApp()
        }
    }

    fun terminateApp() {
        lifecycleScope.launch {
            downloadViewModel.pauseAllDownloads()
            mainActivity.finishAndRemoveTask()
            mainActivity.finishAffinity()
            exitProcess(0)
        }
    }

    companion object {
        const val TAG = "MoreFragment"
    }

}