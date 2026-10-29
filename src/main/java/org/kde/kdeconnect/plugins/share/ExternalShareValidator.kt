/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import org.kde.kdeconnect.helpers.IntentHelper

/** Validates only external share intents; internal file selection uses sendFiles directly. */
internal object ExternalShareValidator {
    fun isValid(context: Context, intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return false

        return try {
            if (!intent.hasExtra(Intent.EXTRA_STREAM)) return true
            val streams = IntentHelper.streamsFromIntent(intent)
            // An invalid file share must not fall through to its text or URL extras.
            if (streams.isEmpty()) return false
            if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return false
            val clip = intent.clipData
            val selectedUris = clip?.let { data ->
                (0 until data.itemCount).mapNotNull { data.getItemAt(it).uri }.toSet()
            }
            streams.all { uri ->
                if (uri.scheme != "content") return@all false
                if (selectedUris != null && uri !in selectedUris) return@all false
                // Android content URIs may prefix an authority with a user ID.
                val authority = uri.authority?.substringAfterLast('@') ?: return@all false
                if (authority == context.packageName || authority.startsWith("${context.packageName}.")) return@all false
                val provider = context.packageManager.resolveContentProvider(authority, 0) ?: return@all false
                if (provider.applicationInfo.uid == Process.myUid()) return@all false
                context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: RuntimeException) {
            // Malformed extras or inaccessible providers are untrusted input, not text shares.
            false
        }
    }
}
