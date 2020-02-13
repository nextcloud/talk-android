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
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.io.IOException
import java.util.*

object NotificationUtils {
    val NOTIFICATION_CHANNEL_CALLS = "NOTIFICATION_CHANNEL_CALLS"
    val NOTIFICATION_CHANNEL_MESSAGES = "NOTIFICATION_CHANNEL_MESSAGES"
    val NOTIFICATION_CHANNEL_CALLS_V2 = "NOTIFICATION_CHANNEL_CALLS_V2"
    val NOTIFICATION_CHANNEL_MESSAGES_V2 = "NOTIFICATION_CHANNEL_MESSAGES_V2"
    val NOTIFICATION_CHANNEL_MESSAGES_V3 = "NOTIFICATION_CHANNEL_MESSAGES_V3"
    val NOTIFICATION_CHANNEL_CALLS_V3 = "NOTIFICATION_CHANNEL_CALLS_V3"

    fun getVibrationEffect(appPreferences: AppPreferences): LongArray? {
        return if (appPreferences.shouldVibrateSetting) {
            longArrayOf(0L, 400L, 800L, 600L, 800L, 800L, 800L, 1000L)
        } else {
            null
        }
    }

    fun getCallSoundUri(context: Context, appPreferences: AppPreferences) : Uri? {
        val ringtonePreferencesString: String? = appPreferences.callRingtoneUri

        return if (TextUtils.isEmpty(ringtonePreferencesString)) {
            Uri.parse("android.resource://" + context.packageName +
                    "/raw/librem_by_feandesign_call")
        } else {
            try {
                val ringtoneSettings = LoganSquare.parse(ringtonePreferencesString, RingtoneSettings::class.java)
                ringtoneSettings.ringtoneUri
            } catch (exception: IOException) {
                Uri.parse("android.resource://" + context.packageName + "/raw/librem_by_feandesign_call")
            }
        }
    }

    fun getMessageSoundUri(context: Context, appPreferences: AppPreferences) : Uri? {
        val ringtonePreferencesString: String? = appPreferences.messageRingtoneUri

        return if (TextUtils.isEmpty(ringtonePreferencesString)) {
            Uri.parse("android.resource://" + context.packageName + "/raw/librem_by_feandesign_message")
        } else {
            try {
                val ringtoneSettings = LoganSquare.parse(ringtonePreferencesString, RingtoneSettings::class.java)
                ringtoneSettings.ringtoneUri
            } catch (exception: IOException) {
                Uri.parse("android.resource://" + context.packageName + "/raw/librem_by_feandesign_message")
            }
        }
    }

    fun getNotificationChannelId(context: Context, channelName: String,
                                 channelDescription: String, enableLights: Boolean,
                                 importance: Int, sound: Uri, audioAttributes: AudioAttributes, vibrationPattern: LongArray?, bypassDnd: Boolean, lockScreenVisibility: Int?): String {
        val channelId = Objects.hash(channelName, channelDescription, enableLights, importance, sound, audioAttributes, vibrationPattern, bypassDnd, lockScreenVisibility).toString()

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            createNotificationChannel(context, channelId, channelName, channelDescription, enableLights, importance, sound, audioAttributes, vibrationPattern, bypassDnd, lockScreenVisibility)
        }

        return channelId
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(context: Context,
                                  channelId: String, channelName: String,
                                  channelDescription: String, enableLights: Boolean,
                                  importance: Int, sound: Uri, audioAttributes: AudioAttributes,
                                  vibrationPattern: LongArray?, bypassDnd: Boolean = false, lockScreenVisibility: Int?) {

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        if (notificationManagerCompat.getNotificationChannel(channelId) == null) {

            val channel = NotificationChannel(channelId, channelName, importance)

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
            if (lockScreenVisibility != null) {
                channel.lockscreenVisibility = lockScreenVisibility
            }

            notificationManagerCompat.createNotificationChannel(channel)
        }
    }
    @TargetApi(Build.VERSION_CODES.O)
    fun createNotificationChannelGroup(
            context: Context,
            groupId: String,
            groupName: CharSequence
    ) {
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notificationChannelGroup = NotificationChannelGroup(groupId, groupName)
            if (!notificationManager.notificationChannelGroups.contains(notificationChannelGroup)) {
                notificationManager.createNotificationChannelGroup(notificationChannelGroup)
            }
        }
    }

    fun cancelAllNotificationsForAccount(
            context: Context?,
            conversationUser: UserNgEntity
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && conversationUser.id != -1L && context != null) {

            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

    fun cancelExistingNotificationWithId(
            context: Context?,
            conversationUser: UserNgEntity,
            notificationId: Long
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && conversationUser.id != -1L &&
                context != null
        ) {

            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusBarNotifications = notificationManager.activeNotifications
            var notification: Notification?
            for (statusBarNotification in statusBarNotifications) {
                notification = statusBarNotification.notification

                if (notification != null && !notification.extras.isEmpty) {
                    if (conversationUser.id == notification.extras.getLong(
                                    BundleKeys.KEY_INTERNAL_USER_ID
                            ) && notificationId == notification.extras.getLong(BundleKeys.KEY_NOTIFICATION_ID)
                    ) {
                        notificationManager.cancel(statusBarNotification.id)
                    }
                }
            }
        }
    }

    fun findNotificationForRoom(
            context: Context?,
            conversationUser: UserNgEntity,
            roomTokenOrId: String
    ): StatusBarNotification? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && conversationUser.id != -1L &&
                context != null
        ) {

            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusBarNotifications = notificationManager.activeNotifications
            var notification: Notification?
            for (statusBarNotification in statusBarNotifications) {
                notification = statusBarNotification.notification

                if (notification != null && !notification.extras.isEmpty) {
                    if (conversationUser.id == notification.extras.getLong(
                                    BundleKeys.KEY_INTERNAL_USER_ID
                            ) && roomTokenOrId == statusBarNotification.notification.extras.getString(
                                    BundleKeys.KEY_CONVERSATION_TOKEN
                            )
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
            conversationUser: UserNgEntity,
            roomTokenOrId: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && conversationUser.id != -1L &&
                context != null
        ) {

            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusBarNotifications = notificationManager.activeNotifications
            var notification: Notification?
            for (statusBarNotification in statusBarNotifications) {
                notification = statusBarNotification.notification

                if (notification != null && !notification.extras.isEmpty) {
                    if (conversationUser.id == notification.extras.getLong(
                                    BundleKeys.KEY_INTERNAL_USER_ID
                            ) && roomTokenOrId == statusBarNotification.notification.extras.getString(
                                    BundleKeys.KEY_CONVERSATION_TOKEN
                            )
                    ) {
                        notificationManager.cancel(statusBarNotification.id)
                    }
                }
            }
        }
    }
}
