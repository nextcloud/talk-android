/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.annotation.WorkerThread
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.UnifiedPushUtils
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.power.PowerManagerUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject

/** State of the device and its system settings that the settings screen shows. */
data class DeviceState(
    val isGooglePlayServicesAvailable: Boolean,
    val hasEmbeddedDistributor: Boolean,
    val externalDistributorCount: Int,
    val acknowledgedDistributor: String?,
    val notificationPermissionGranted: Boolean?,
    val batteryOptimizationIgnored: Boolean,
    val fullScreenIntentGranted: Boolean?,
    val callRingtone: RingtoneState,
    val messageRingtone: RingtoneState,
    val isKeyguardSecure: Boolean
)

/** Reads the [DeviceState], which can change any time the app is in the background. */
class DeviceStateReader @Inject constructor(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val platformPermissionUtil: PlatformPermissionUtil
) {
    @WorkerThread
    fun read(): DeviceState =
        DeviceState(
            isGooglePlayServicesAvailable = ClosedInterfaceImpl().isGooglePlayServicesAvailable,
            hasEmbeddedDistributor = UnifiedPushUtils.hasEmbeddedDistributor(context),
            externalDistributorCount = UnifiedPushUtils.getExternalDistributors(context).size,
            acknowledgedDistributor = UnifiedPush.getAckDistributor(context),
            notificationPermissionGranted = readNotificationPermission(),
            batteryOptimizationIgnored = PowerManagerUtils().isIgnoringBatteryOptimizations(),
            fullScreenIntentGranted = readFullScreenIntentPermission(),
            callRingtone = readRingtone(
                NotificationUtils.isCallsNotificationChannelEnabled(context),
                NotificationUtils.getCallRingtoneUri(context, appPreferences)
            ),
            messageRingtone = readRingtone(
                NotificationUtils.isMessagesNotificationChannelEnabled(context),
                NotificationUtils.getMessageRingtoneUri(context, appPreferences)
            ),
            isKeyguardSecure = context.getSystemService(KeyguardManager::class.java).isKeyguardSecure
        )

    private fun readNotificationPermission(): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            platformPermissionUtil.isPostNotificationsPermissionGranted()
        } else {
            null
        }

    private fun readFullScreenIntentPermission(): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            null
        }

    private fun readRingtone(channelEnabled: Boolean, ringtoneUri: Uri?): RingtoneState =
        when {
            !channelEnabled -> RingtoneState.Disabled
            ringtoneUri == null -> RingtoneState.Silent
            ringtoneUri.toString() == NotificationUtils.DEFAULT_CALL_RINGTONE_URI ||
                ringtoneUri.toString() == NotificationUtils.DEFAULT_MESSAGE_RINGTONE_URI -> RingtoneState.Default
            else -> RingtoneState.Custom(
                title = RingtoneManager.getRingtone(context, ringtoneUri)?.getTitle(context)
                    ?: ringtoneUri.lastPathSegment
                    ?: ringtoneUri.toString(),
                uri = ringtoneUri.toString()
            )
        }
}
