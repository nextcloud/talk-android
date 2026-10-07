/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.io.File
import java.util.Date
import javax.inject.Inject

/**
 * Takes one photo with the in-app camera and returns its content uri as the result data. There is no confirmation
 * step: the shutter finishes the activity. Without the camera permission the activity ends as cancelled, the caller
 * asks for the permission before it starts this screen.
 */
@AutoInjector(NextcloudTalkApplication::class)
class CaptureActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            finish()
            return
        }
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        setContent {
            PhotoCaptureScreen(
                appPreferences = appPreferences,
                newPhotoFile = ::newPhotoFile,
                onCaptured = ::returnPhoto,
                onFailed = {
                    Toast.makeText(this, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                },
                onClose = ::finish
            )
        }
    }

    private fun newPhotoFile(): File? {
        val baseName = getString(R.string.nc_picture_filename, formatCaptureTimestamp(Date()))
        return createPhotoFile(cacheDir, baseName)
    }

    private fun returnPhoto(file: File) {
        val uri = FileProvider.getUriForFile(this, packageName, file)
        setResult(RESULT_OK, Intent().setData(uri))
        finish()
    }
}
