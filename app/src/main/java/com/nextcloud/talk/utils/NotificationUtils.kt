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
import androidx.core.graphics.drawable.IconCompat
import com.bluelinelabs.logansquare.LoganSquare
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSources
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.image.CloseableBitmap
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.io.IOException

object NotificationUtils {

    enum class NotificationChannels {
        NOTIFICATION_CHANNEL_MESSAGES_V4,
        NOTIFICATION_CHANNEL_CALLS_V4,
        NOTIFICATION_CHANNEL_UPLOADS
    }

    const val DEFAULT_CALL_RINGTONE_URI =
        "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/librem_by_feandesign_call"
    const val DEFAULT_MESSAGE_RINGTONE_URI =
        "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/librem_by_feandesign_message"

    // RemoteInput key - used for replies sent directly from notification
    const val KEY_DIRECT_REPLY = "key_direct_reply"

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(
        context: Context,
        notificationChannel: Channel,
        sound: Uri?,
        audioAttributes: AudioAttributes?
    ) {

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(notificationChannel.id) == null
        ) {
            val importance = if (notificationChannel.isImportant) {
                NotificationManager.IMPORTANCE_HIGH
            } else {
                NotificationManager.IMPORTANCE_LOW
            }

            val channel = NotificationChannel(
                notificationChannel.id,
                notificationChannel.name,
                importance
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
                NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name,
                context.resources.getString(R.string.nc_notification_channel_calls),
                context.resources.getString(R.string.nc_notification_channel_calls_description),
                true
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
                NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name,
                context.resources.getString(R.string.nc_notification_channel_messages),
                context.resources.getString(R.string.nc_notification_channel_messages_description),
                true
            ),
            soundUri,
            audioAttributes
        )
    }

    private fun createUploadsNotificationChannel(
        context: Context
    ) {
        createNotificationChannel(
            context,
            Channel(
                NotificationChannels.NOTIFICATION_CHANNEL_UPLOADS.name,
                context.resources.getString(R.string.nc_notification_channel_uploads),
                context.resources.getString(R.string.nc_notification_channel_uploads_description),
                false
            ),
            null,
            null
        )
    }

    fun registerNotificationChannels(
        context: Context,
        appPreferences: AppPreferences
    ) {
        createCallsNotificationChannel(context, appPreferences)
        createMessagesNotificationChannel(context, appPreferences)
        createUploadsNotificationChannel(context)
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun removeOldNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Current version does not use notification channel groups - delete all groups
            for (channelGroup in notificationManager.notificationChannelGroups) {
                notificationManager.deleteNotificationChannelGroup(channelGroup.id)
            }

            val channelsToKeep = NotificationChannels.values().map { it.name }

            // Delete all notification channels created by previous versions
            for (channel in notificationManager.notificationChannels) {
                if (!channelsToKeep.contains(channel.id)) {
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

    private inline fun scanNotifications(
        context: Context?,
        conversationUser: User,
        callback: (
            notificationManager: NotificationManager,
            statusBarNotification: StatusBarNotification,
            notification: Notification
        ) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || conversationUser.id == -1L || context == null) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val statusBarNotifications = notificationManager.activeNotifications
        var notification: Notification?
        for (statusBarNotification in statusBarNotifications) {
            notification = statusBarNotification.notification

            if (
                notification != null &&
                !notification.extras.isEmpty &&
                conversationUser.id == notification.extras.getLong(BundleKeys.KEY_INTERNAL_USER_ID)
            ) {
                callback(notificationManager, statusBarNotification, notification)
            }
        }
    }

    fun cancelAllNotificationsForAccount(context: Context?, conversationUser: User) {
        scanNotifications(context, conversationUser) { notificationManager, statusBarNotification, _ ->
            notificationManager.cancel(statusBarNotification.id)
        }
    }

    fun cancelExistingNotificationWithId(context: Context?, conversationUser: User, notificationId: Long?) {
        scanNotifications(context, conversationUser) { notificationManager, statusBarNotification, notification ->
            if (notificationId == notification.extras.getLong(BundleKeys.KEY_NOTIFICATION_ID)) {
                notificationManager.cancel(statusBarNotification.id)
            }
        }
    }

    fun findNotificationForRoom(
        context: Context?,
        conversationUser: User,
        roomTokenOrId: String
    ): StatusBarNotification? {
        scanNotifications(context, conversationUser) { _, statusBarNotification, notification ->
            if (roomTokenOrId == notification.extras.getString(BundleKeys.KEY_ROOM_TOKEN)) {
                return statusBarNotification
            }
        }
        return null
    }

    fun cancelExistingNotificationsForRoom(context: Context?, conversationUser: User, roomTokenOrId: String) {
        scanNotifications(context, conversationUser) { notificationManager, statusBarNotification, notification ->
            if (roomTokenOrId == notification.extras.getString(BundleKeys.KEY_ROOM_TOKEN)) {
                notificationManager.cancel(statusBarNotification.id)
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
        return if (TextUtils.isEmpty(ringtonePreferencesString)) {
            Uri.parse(defaultRingtoneUri)
        } else {
            try {
                val ringtoneSettings =
                    LoganSquare.parse(ringtonePreferencesString, RingtoneSettings::class.java)
                ringtoneSettings.ringtoneUri
            } catch (exception: IOException) {
                Uri.parse(defaultRingtoneUri)
            }
        }
    }

    fun getCallRingtoneUri(
        context: Context,
        appPreferences: AppPreferences
    ): Uri? {
        return getRingtoneUri(
            context,
            appPreferences.callRingtoneUri,
            DEFAULT_CALL_RINGTONE_URI, NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name
        )
    }

    fun getMessageRingtoneUri(
        context: Context,
        appPreferences: AppPreferences
    ): Uri? {
        return getRingtoneUri(
            context,
            appPreferences.messageRingtoneUri,
            DEFAULT_MESSAGE_RINGTONE_URI, NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name
        )
    }

    /*
    * Load user avatar synchronously.
    * Inspired by:
    * https://frescolib.org/docs/using-image-pipeline.html
    * https://github.com/facebook/fresco/issues/830
    * https://localcoder.org/using-facebooks-fresco-to-load-a-bitmap
    */
    fun loadAvatarSync(avatarUrl: String): IconCompat? {
        // TODO - how to handle errors here?
        var avatarIcon: IconCompat? = null
        val imageRequest = DisplayUtils.getImageRequestForUrl(avatarUrl)
        val dataSource = Fresco.getImagePipeline().fetchDecodedImage(imageRequest, null)
        val closeableImageRef = DataSources.waitForFinalResult(dataSource) as CloseableReference<CloseableBitmap>?
        val bitmap = closeableImageRef?.get()?.underlyingBitmap
        if (bitmap != null) {
            avatarIcon = IconCompat.createWithBitmap(DisplayUtils.roundBitmap(bitmap))
        }
        CloseableReference.closeSafely(closeableImageRef)
        dataSource.close()
        return avatarIcon
    }

    private data class Channel(
        val id: String,
        val name: String,
        val description: String,
        val isImportant: Boolean
    )
}
