/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.PermissionChecker
import com.nextcloud.talk.BuildConfig

class PlatformPermissionUtilImpl(private val context: Context) : PlatformPermissionUtil {
    override val privateBroadcastPermission: String =
        "${BuildConfig.APPLICATION_ID}.${BuildConfig.PERMISSION_LOCAL_BROADCAST}"

    override fun isCameraPermissionGranted(): Boolean {
        return PermissionChecker.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PermissionChecker.PERMISSION_GRANTED
    }

    override fun isFilesPermissionGranted(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                if (
                    PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                    == PermissionChecker.PERMISSION_GRANTED ||
                    PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
                    == PermissionChecker.PERMISSION_GRANTED ||
                    PermissionChecker.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                    == PermissionChecker.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Permission is granted (SDK 33 or greater)")
                    true
                } else {
                    Log.d(TAG, "Permission is revoked (SDK 33 or greater)")
                    false
                }
            }
            Build.VERSION.SDK_INT > Build.VERSION_CODES.Q -> {
                if (PermissionChecker.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PermissionChecker.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Permission is granted (SDK 30 or greater)")
                    true
                } else {
                    Log.d(TAG, "Permission is revoked (SDK 30 or greater)")
                    false
                }
            }
            else -> {
                if (PermissionChecker.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PermissionChecker.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Permission is granted")
                    true
                } else {
                    Log.d(TAG, "Permission is revoked")
                    false
                }
            }
        }
    }

    companion object {
        private val TAG = PlatformPermissionUtilImpl::class.simpleName
    }
}
