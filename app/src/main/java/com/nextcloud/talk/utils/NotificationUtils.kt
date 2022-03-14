/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.io.IOException

object NotificationUtils {

    // Notification channel IDs commented below are no longer used.
    // All old notification channels get deleted when the app is upgraded to the current version.
    //
    // val NOTIFICATION_CHANNEL_MESSAGES = "NOTIFICATION_CHANNEL_MESSAGES"
    // val NOTIFICATION_CHANNEL_MESSAGES_V2 = "NOTIFICATION_CHANNEL_MESSAGES_V2"
    // val NOTIFICATION_CHANNEL_MESSAGES_V3 = "NOTIFICATION_CHANNEL_MESSAGES_V3"
    // val NOTIFICATION_CHANNEL_CALLS = "NOTIFICATION_CHANNEL_CALLS"
    // val NOTIFICATION_CHANNEL_CALLS_V2 = "NOTIFICATION_CHANNEL_CALLS_V2"
    // val NOTIFICATION_CHANNEL_CALLS_V3 = "NOTIFICATION_CHANNEL_CALLS_V3"

    const val NOTIFICATION_CHANNEL_MESSAGES_V4 = "NOTIFICATION_CHANNEL_MESSAGES_V4"
    const val NOTIFICATION_CHANNEL_CALLS_V4 = "NOTIFICATION_CHANNEL_CALLS_V4"

    const val DEFAULT_CALL_RINGTONE_URI =
        "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/librem_by_feandesign_call"
    const val DEFAULT_MESSAGE_RINGTONE_URI =
        "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/librem_by_feandesign_message"

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(
        context: Context,
        notificationChannel: Channel,
        sound: Uri?,
        audioAttributes: AudioAttributes
    ) {

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(notificationChannel.id) == null
        ) {
            val channel = NotificationChannel(
                notificationChannel.id, notificationChannel.name,
                NotificationManager.IMPORTANCE_HIGH
            )

            channel.description = notificationChannel.description
            channel.enableLights(true)
            channel.lightColor = R.color.colorPrimary
            channel.setSound(sound, audioAttributes)
            channel.setBypassDnd(false)

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
            Channel(
                NOTIFICATION_CHANNEL_CALLS_V4,
                context.resources.getString(R.string.nc_notification_channel_calls),
                context.resources.getString(R.string.nc_notification_channel_calls_description)
            ),
            soundUri,
            audioAttributes
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
            Channel(
                NOTIFICATION_CHANNEL_MESSAGES_V4,
                context.resources.getString(R.string.nc_notification_channel_messages),
                context.resources.getString(R.string.nc_notification_channel_messages_description)
            ),
            soundUri,
            audioAttributes
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
    fun removeOldNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Current version does not use notification channel groups - delete all groups
            for (channelGroup in notificationManager.notificationChannelGroups) {
                notificationManager.deleteNotificationChannelGroup(channelGroup.id)
            }

            // Delete all notification channels created by previous versions
            for (channel in notificationManager.notificationChannels) {
                if (
                    channel.id != NOTIFICATION_CHANNEL_CALLS_V4 &&
                    channel.id != NOTIFICATION_CHANNEL_MESSAGES_V4
                ) {
                    notificationManager.deleteNotificationChannel(channel.id)
                }
            }
        }
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

    fun cancelExistingNotificationWithId(context: Context?, conversationUser: UserEntity, notificationId: Long?) {
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
    ): Uri? {
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
                return ringtoneSettings.ringtoneUri
            } catch (exception: IOException) {
                return Uri.parse(defaultRingtoneUri)
            }
        }
    }

    fun getCallRingtoneUri(
        context: Context,
        appPreferences: AppPreferences
    ): Uri? {
        return getRingtoneUri(
            context,
            appPreferences.callRingtoneUri, DEFAULT_CALL_RINGTONE_URI, NOTIFICATION_CHANNEL_CALLS_V4
        )
    }

    fun getMessageRingtoneUri(
        context: Context,
        appPreferences: AppPreferences
    ): Uri? {
        return getRingtoneUri(
            context,
            appPreferences.messageRingtoneUri, DEFAULT_MESSAGE_RINGTONE_URI, NOTIFICATION_CHANNEL_MESSAGES_V4
        )
    }

    private data class Channel(
        val id: String,
        val name: String,
        val description: String
    )
}
