/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.permissions

interface PlatformPermissionUtil {
    val privateBroadcastPermission: String
    fun isCameraPermissionGranted(): Boolean
    fun isMicrophonePermissionGranted(): Boolean
    fun isBluetoothPermissionGranted(): Boolean
    fun isFilesPermissionGranted(): Boolean
    fun isPostNotificationsPermissionGranted(): Boolean
}
