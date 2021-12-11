/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.io.IOException

object NotificationUtils {
    val NOTIFICATION_CHANNEL_CALLS = "NOTIFICATION_CHANNEL_CALLS"
    val NOTIFICATION_CHANNEL_MESSAGES = "NOTIFICATION_CHANNEL_MESSAGES"
    val NOTIFICATION_CHANNEL_CALLS_V2 = "NOTIFICATION_CHANNEL_CALLS_V2"
    val NOTIFICATION_CHANNEL_MESSAGES_V2 = "NOTIFICATION_CHANNEL_MESSAGES_V2"
    val NOTIFICATION_CHANNEL_MESSAGES_V3 = "NOTIFICATION_CHANNEL_MESSAGES_V3"
    val NOTIFICATION_CHANNEL_CALLS_V3 = "NOTIFICATION_CHANNEL_CALLS_V3"
    val NOTIFICATION_CHANNEL_CALLS_V4 = "NOTIFICATION_CHANNEL_CALLS_V4"

    val DEFAULT_CALL_RINGTONE_URI =
        "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/librem_by_feandesign_call"
    val DEFAULT_MESSAGE_RINGTONE_URI =
        "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/librem_by_feandesign_message"

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        channelDescription: String,
        enableLights: Boolean,
        importance: Int,
        sound: Uri,
        audioAttributes: AudioAttributes,
        vibrationPattern: LongArray?,
        bypassDnd: Boolean = false
    ) {

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(channelId) == null
        ) {

            val channel = NotificationChannel(
                channelId, channelName,
                importance
            )

            channel.description = channelDescription
            channel.enableLights(enableLights)
            channel.lightColor = R.color.colorPrimary
            channel.setSound(sound, audioAttributes)
            if (vibrationPattern != null) {
                channel.enableVibration(true)
                channel.vibrationPattern = vibrationPattern
            } else {
                channel.enableVibration(false)
            }
            channel.setBypassDnd(bypassDnd)

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createCallsNotificationChannel(
        context: Context,
        appPreferences: AppPreferences
    ) {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)
                .build()
        val soundUri = getCallRingtoneUri(context, appPreferences)

        createNotificationChannel(
            context,
            NOTIFICATION_CHANNEL_CALLS_V4,
            context.resources.getString(R.string.nc_notification_channel_calls),
            context.resources.getString(R.string.nc_notification_channel_calls_description),
            true,
            NotificationManagerCompat.IMPORTANCE_HIGH,
            soundUri,
            audioAttributes,
            null,
            false
        )
    }

    private fun createMessagesNotificationChannel(
        context: Context,
        appPreferences: AppPreferences
    ) {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()
        val soundUri = getMessageRingtoneUri(context, appPreferences)

        createNotificationChannel(
            context,
            NOTIFICATION_CHANNEL_MESSAGES_V3,
            context.resources.getString(R.string.nc_notification_channel_messages),
            context.resources.getString(R.string.nc_notification_channel_messages_description),
            true,
            NotificationManagerCompat.IMPORTANCE_HIGH,
            soundUri,
            audioAttributes,
            null,
            false
        )
    }

    fun registerNotificationChannels(
        context: Context,
        appPreferences: AppPreferences
    ) {
        createCallsNotificationChannel(context, appPreferences)
        createMessagesNotificationChannel(context, appPreferences)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun getNotificationChannel(
        context: Context,
        channelId: String
    ): NotificationChannel? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.getNotificationChannel(channelId)
        }
        return null
    }

    fun cancelAllNotificationsForAccount(context: Context?, conversationUser: UserEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && conversationUser.id != -1L && context != null) {

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusBarNotifications = notificationManager.activeNotifications
            var notification: Notification?
            for (statusBarNotification in statusBarNotifications) {
                notification = statusBarNotification.notification

                if (notification != null && !notification.extras.isEmpty) {
                    if (conversationUser.id == notification.extras.getLong(BundleKeys.KEY_INTERNAL_USER_ID)) {
                        notificationManager.cancel(statusBarNotification.id)
                    }
                }
            }
        }
    }

    fun cancelExistingNotificationWithId(context: Context?, conversationUser: UserEntity, notificationId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && conversationUser.id != -1L &&
            context != null
        ) {

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusBarNotifications = notificationManager.activeNotifications
            var notification: Notification?
            for (statusBarNotification in statusBarNotifications) {
                notification = statusBarNotification.notification

                if (notification != null && !notification.extras.isEmpty) {
                    if (
                        conversationUser.id == notification.extras.getLong(BundleKeys.KEY_INTERNAL_USER_ID) &&
                        notificationId == notification.extras.getLong(BundleKeys.KEY_NOTIFICATION_ID)
                    ) {
                        notificationManager.cancel(statusBarNotification.id)
                    }
                }
            }
        }
    }

    fun findNotificationForRoom(
        context: Context?,
        conversationUser: UserEntity,
        roomTokenOrId: String
    ): StatusBarNotification? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && conversationUser.id != -1L &&
            context != null
        ) {

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusBarNotifications = notificationManager.activeNotifications
            var notification: Notification?
            for (statusBarNotification in statusBarNotifications) {
                notification = statusBarNotification.notification

                if (notification != null && !notification.extras.isEmpty) {
                    if (
                        conversationUser.id == notification.extras.getLong(BundleKeys.KEY_INTERNAL_USER_ID) &&
                        roomTokenOrId == statusBarNotification.notification.extras.getString(BundleKeys.KEY_ROOM_TOKEN)
                    ) {
                        return statusBarNotification
                    }
                }
            }
        }

        return null
    }

    fun cancelExistingNotificationsForRoom(
        context: Context?,
        conversationUser: UserEntity,
        roomTokenOrId: String
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            conversationUser.id != -1L &&
            context != null
        ) {

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusBarNotifications = notificationManager.activeNotifications
            var notification: Notification?
            for (statusBarNotification in statusBarNotifications) {
                notification = statusBarNotification.notification

                if (notification != null && !notification.extras.isEmpty) {
                    if (conversationUser.id == notification.extras.getLong(BundleKeys.KEY_INTERNAL_USER_ID) &&
                        roomTokenOrId == statusBarNotification.notification.extras.getString(BundleKeys.KEY_ROOM_TOKEN)
                    ) {
                        notificationManager.cancel(statusBarNotification.id)
                    }
                }
            }
        }
    }

    private fun getRingtoneUri(
        context: Context,
        ringtonePreferencesString: String?,
        defaultRingtoneUri: String,
        channelId: String
    ): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = getNotificationChannel(context, channelId)
            if (channel != null) {
                return channel.sound
            }
            // Notification channel will not be available when starting the application for the first time.
            // Ringtone uris are required to register the notification channels -> get uri from preferences.
        }
        if (TextUtils.isEmpty(ringtonePreferencesString)) {
            return Uri.parse(defaultRingtoneUri)
        } else {
            try {
                val ringtoneSettings =
                    LoganSquare.parse(ringtonePreferencesString, RingtoneSettings::class.java)
                return ringtoneSettings.ringtoneUri!!
            } catch (exception: IOException) {
                return Uri.parse(defaultRingtoneUri)
            }
        }
    }

    fun getCallRingtoneUri(
        context: Context,
        appPreferences: AppPreferences
    ): Uri {
        return getRingtoneUri(
            context,
            appPreferences.callRingtoneUri, DEFAULT_CALL_RINGTONE_URI, NOTIFICATION_CHANNEL_CALLS_V4
        )
    }

    fun getMessageRingtoneUri(
        context: Context,
        appPreferences: AppPreferences
    ): Uri {
        return getRingtoneUri(
            context,
            appPreferences.messageRingtoneUri, DEFAULT_MESSAGE_RINGTONE_URI, NOTIFICATION_CHANNEL_MESSAGES_V3
        )
    }
}
