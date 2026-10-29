/*
 * SPDX-FileCopyrightText: 2016 Richard Wagler <riwag@posteo.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.os.Environment
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect_tp.R
import java.io.File
import androidx.core.content.edit
import androidx.core.net.toUri

class ShareSettingsFragment : PluginSettingsFragment() {

    private lateinit var filePicker: Preference
    private lateinit var backgroundLinkPermission: Preference

    private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            saveStorageLocationPreference(requireContext(), uri)
            filePicker.summary = uri.path
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        val preferenceScreen = preferenceScreen
        val customDownloads = preferenceScreen.findPreference<SwitchPreference>("share_destination_custom")!!
        filePicker = preferenceScreen.findPreference("share_destination_folder_preference")!!
        val openLinks = preferenceScreen.findPreference<SwitchPreference>(PREFERENCE_OPEN_LINKS_DIRECTLY)!!
        backgroundLinkPermission = preferenceScreen.findPreference("share_background_link_permission")!!
        openLinks.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !Settings.canDrawOverlays(requireContext())) {
                openBackgroundLinkPermissionSettings()
            }
            true
        }
        backgroundLinkPermission.setOnPreferenceClickListener {
            openBackgroundLinkPermissionSettings()
            true
        }

        customDownloads.setOnPreferenceChangeListener { _, newValue ->
            updateFilePickerStatus(newValue as Boolean)
            true
        }
        filePicker.setOnPreferenceClickListener {
            openDocumentTreeLauncher.launch(null)
            true
        }

        val customized = PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false)

        updateFilePickerStatus(customized)
    }

    override fun onResume() {
        super.onResume()
        backgroundLinkPermission.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        backgroundLinkPermission.setSummary(
            if (Settings.canDrawOverlays(requireContext())) R.string.share_background_link_permission_granted
            else R.string.share_background_link_permission_required
        )
    }

    private fun openBackgroundLinkPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${requireContext().packageName}".toUri())
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.share_background_link_permission_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateFilePickerStatus(enabled: Boolean) {
        filePicker.isEnabled = enabled
        val path = PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .getString(PREFERENCE_DESTINATION, null)

        if (enabled && path != null) {
            filePicker.summary = path.toUri().path
        } else {
            filePicker.summary = getDefaultDestinationDirectory().absolutePath
        }
    }

    companion object {
        const val PREFERENCE_OPEN_LINKS_DIRECTLY = "share_open_links_directly"
        private const val PREFERENCE_CUSTOMIZE_DESTINATION = "share_destination_custom"
        private const val PREFERENCE_DESTINATION = "share_destination_folder_uri"

        fun newInstance(pluginKey: String, deviceId: String, layout: Int): ShareSettingsFragment {
            val fragment = ShareSettingsFragment()
            fragment.setArguments(pluginKey, deviceId, layout)
            return fragment
        }

        fun getDefaultDestinationDirectory(): File {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }

        fun isCustomDestinationEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false)
        }

        // Will return the appropriate directory, whether it is customized or not
        fun getDestinationDirectory(context: Context): DocumentFile {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false)) {
                val path = PreferenceManager.getDefaultSharedPreferences(context).getString(PREFERENCE_DESTINATION, null)
                if (path != null) {
                    val treeDocumentFile = DocumentFile.fromTreeUri(context, path.toUri())
                    if (treeDocumentFile != null && treeDocumentFile.canWrite()) { // Checks for FLAG_DIR_SUPPORTS_CREATE on directories
                        return treeDocumentFile
                    } else {
                        // Maybe permission was revoked
                        Log.w("SharePlugin", "Share destination is not writable, falling back to default path.")
                    }
                }
            }
            try {
                getDefaultDestinationDirectory().mkdirs()
            } catch (e: Exception) {
                Log.e("KDEConnect", "Exception", e)
            }
            return DocumentFile.fromFile(getDefaultDestinationDirectory())
        }

        fun saveStorageLocationPreference(context: Context, uri: Uri) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit { putString(PREFERENCE_DESTINATION, uri.toString()) }
            prefs.edit { putBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, true) }
        }
    }
}
