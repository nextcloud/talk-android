/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.jobs.ExportVideoWorker
import com.nextcloud.talk.mediaviewer.model.MediaViewerItem

internal class VideoExporter(private val activity: MediaViewerActivity) {
    private var pending = activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)
    private val launcher = activity.registerForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: Context, input: String): Intent =
                super.createIntent(context, input).setType(pending?.getString(MIME_TYPE) ?: "application/octet-stream")
        },
        ::onDestinationSelected
    )

    private fun onDestinationSelected(uri: Uri?) {
        val item = pending
        pending = null
        if (uri != null && item != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val data = Data.Builder()
                    .putString(ExportVideoWorker.FILE_ID, item.getString(ExportVideoWorker.FILE_ID))
                    .putString(ExportVideoWorker.FILE_NAME, item.getString(ExportVideoWorker.FILE_NAME))
                    .putString(ExportVideoWorker.DESTINATION, uri.toString())
                    .build()
                WorkManager.getInstance(activity).enqueue(
                    OneTimeWorkRequestBuilder<ExportVideoWorker>().setInputData(data).build()
                )
                Snackbar.make(activity.window.decorView, R.string.video_saving, Snackbar.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                Snackbar.make(activity.window.decorView, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    init {
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) { pending ?: Bundle() }
    }

    fun save(item: MediaViewerItem) {
        if (pending?.containsKey(ExportVideoWorker.FILE_NAME) == true) return
        pending = Bundle().apply {
            putString(ExportVideoWorker.FILE_ID, item.fileId)
            putString(ExportVideoWorker.FILE_NAME, item.fileName)
            putString(MIME_TYPE, item.mimeType)
        }
        launcher.launch(item.fileName)
    }

    companion object {
        private const val STATE_KEY = "video-export"
        private const val MIME_TYPE = "mimeType"
    }
}
