/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.net.Uri
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil.executeBlocking
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.ui.toDrawable
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Suppress("TooManyFunctions")
object NotificationUtils {

    val TAG = NotificationUtils::class.java.simpleName

    enum class NotificationChannels {
        NOTIFICATION_CHANNEL_MESSAGES_V4,
        NOTIFICATION_CHANNEL_CALLS_V5,
        NOTIFICATION_CHANNEL_CALLS_ONGOING_V1,
        NOTIFICATION_CHANNEL_UPLOADS
    }

    const val DEFAULT_CALL_RINGTONE_URI =
        "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/librem_by_feandesign_call"
    const val DEFAULT_MESSAGE_RINGTONE_URI =
        "android.resource://" + BuildConfig.APPLICATION_ID + "/raw/librem_by_feandesign_message"

    // RemoteInput key - used for replies sent directly from notification
    const val KEY_DIRECT_REPLY = "key_direct_reply"

    // notification group keys
    const val KEY_UPLOAD_GROUP = "com.nextcloud.talk.utils.KEY_UPLOAD_GROUP"
    const val GROUP_SUMMARY_NOTIFICATION_ID = -1

    private const val BITMAP_COMPRESSION_QUALITY = 100

    /**
     * Separates a channel base id from a timestamp, e.g. "NOTIFICATION_CHANNEL_CALLS_V5__1762950000000". The app
     * can't change the sound of an existing channel, so the channel is replaced under a new id, see [replaceChannel].
     */
    private const val CHANNEL_ID_VERSION_SEPARATOR = "__"

    const val CALLS_AUDIO_USAGE = AudioAttributes.USAGE_NOTIFICATION_RINGTONE
    const val MESSAGES_AUDIO_USAGE = AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT

    private fun createNotificationChannel(
        context: Context,
        notificationChannel: Channel,
        sound: Uri?,
        audioAttributes: AudioAttributes?
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (
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

    private fun createCallsNotificationChannel(context: Context, channelId: String, soundUri: Uri?) {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(CALLS_AUDIO_USAGE)
                .build()

        createNotificationChannel(
            context,
            Channel(
                channelId,
                context.resources.getString(R.string.nc_notification_channel_calls),
                context.resources.getString(R.string.nc_notification_channel_calls_description),
                true
            ),
            soundUri,
            audioAttributes
        )
    }

    private fun channelVersion(channelId: String, baseId: String): Long? =
        when {
            channelId == baseId -> 0L
            channelId.startsWith(baseId + CHANNEL_ID_VERSION_SEPARATOR) ->
                channelId.removePrefix(baseId + CHANNEL_ID_VERSION_SEPARATOR).toLongOrNull()
            else -> null
        }

    private fun getVersionedChannelId(context: Context, baseId: String): String {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.notificationChannels
            .mapNotNull { channel -> channelVersion(channel.id, baseId)?.let { version -> version to channel.id } }
            .maxByOrNull { it.first }
            ?.second
            ?: baseId
    }

    fun getCallsNotificationChannelId(context: Context): String =
        getVersionedChannelId(context, NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V5.name)

    fun getMessagesNotificationChannelId(context: Context): String =
        getVersionedChannelId(context, NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name)

    /**
     * @param ringtoneUri null for silent
     * @return false if the ringtone could not be set
     */
    fun updateCallRingtone(context: Context, appPreferences: AppPreferences, ringtoneUri: Uri?): Boolean {
        val baseId = NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V5.name
        val replaced = replaceChannel(context, baseId, ringtoneUri) { channelId ->
            createCallsNotificationChannel(context, channelId, ringtoneUri)
        }
        if (replaced) {
            appPreferences.callRingtoneUri = LoganSquare.serialize(RingtoneSettings(ringtoneUri, null))
        }
        return replaced
    }

    /**
     * @param ringtoneUri null for silent
     * @return false if the sound could not be set
     */
    fun updateMessageRingtone(context: Context, appPreferences: AppPreferences, ringtoneUri: Uri?): Boolean {
        val baseId = NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name
        val replaced = replaceChannel(context, baseId, ringtoneUri) { channelId ->
            createMessagesNotificationChannel(context, channelId, ringtoneUri)
        }
        if (replaced) {
            appPreferences.messageRingtoneUri = LoganSquare.serialize(RingtoneSettings(ringtoneUri, null))
        }
        return replaced
    }

    /**
     * Creates a new channel with a new id and then deletes the old ones. Other settings the user changed in the
     * system settings for the old channel are not kept.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun replaceChannel(
        context: Context,
        baseId: String,
        sound: Uri?,
        createChannel: (String) -> Unit
    ): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val oldChannels = notificationManager.notificationChannels.filter { channelVersion(it.id, baseId) != null }
        if (oldChannels.size == 1 && oldChannels.first().sound == sound) {
            return true
        }

        val newChannelId = baseId + CHANNEL_ID_VERSION_SEPARATOR + System.currentTimeMillis()
        val created = try {
            createChannel(newChannelId)
            true
        } catch (e: RuntimeException) {
            // e.g. SecurityException for sounds the app is not allowed to read, or invalid sound uris
            Log.e(TAG, "Failed to create channel $newChannelId with sound $sound", e)
            false
        }
        if (created) {
            oldChannels.forEach { notificationManager.deleteNotificationChannel(it.id) }
            Log.i(TAG, "Replaced channels ${oldChannels.map { it.id }} with $newChannelId")
        }
        return created
    }

    /**
     * Separate from [NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V5] (the incoming-ring channel,
     * which must stay IMPORTANCE_HIGH) so the persistent "call in progress" foreground-service
     * notification never heads-ups/peeks - it should only be visible by pulling down the status bar.
     */
    private fun createOngoingCallNotificationChannel(context: Context) {
        createNotificationChannel(
            context,
            Channel(
                NotificationChannels.NOTIFICATION_CHANNEL_CALLS_ONGOING_V1.name,
                context.resources.getString(R.string.nc_notification_channel_calls_ongoing),
                context.resources.getString(R.string.nc_notification_channel_calls_ongoing_description),
                false
            ),
            null,
            null
        )
    }

    private fun createMessagesNotificationChannel(context: Context, channelId: String, soundUri: Uri?) {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(MESSAGES_AUDIO_USAGE)
                .build()

        createNotificationChannel(
            context,
            Channel(
                channelId,
                context.resources.getString(R.string.nc_notification_channel_messages),
                context.resources.getString(R.string.nc_notification_channel_messages_description),
                true
            ),
            soundUri,
            audioAttributes
        )
    }

    private fun createUploadsNotificationChannel(context: Context) {
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

    fun registerNotificationChannels(context: Context, appPreferences: AppPreferences) {
        createCallsNotificationChannel(
            context,
            getCallsNotificationChannelId(context),
            getCallRingtoneUri(context, appPreferences)
        )
        createOngoingCallNotificationChannel(context)
        createMessagesNotificationChannel(
            context,
            getMessagesNotificationChannelId(context),
            getMessageRingtoneUri(context, appPreferences)
        )
        createUploadsNotificationChannel(context)
    }

    fun removeOldNotificationChannels(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Current version does not use notification channel groups - delete all groups
        for (channelGroup in notificationManager.notificationChannelGroups) {
            notificationManager.deleteNotificationChannelGroup(channelGroup.id)
        }

        val channelsToKeep = NotificationChannels.values().map { it.name }

        // Delete all notification channels created by previous versions
        for (channel in notificationManager.notificationChannels) {
            if (channelsToKeep.none { channelVersion(channel.id, it) != null }) {
                notificationManager.deleteNotificationChannel(channel.id)
            }
        }
    }

    private fun getNotificationChannel(context: Context, channelId: String): NotificationChannel? {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.getNotificationChannel(channelId)
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
        if (conversationUser.id == -1L || context == null) {
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

    fun cancelNotification(context: Context?, conversationUser: User, notificationId: Long?) {
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
            if (roomTokenOrId == notification.extras.getString(BundleKeys.KEY_ROOM_TOKEN) &&
                !notification.extras.getBoolean(BundleKeys.KEY_NOTIFICATION_RESTRICT_DELETION)
            ) {
                notificationManager.cancel(statusBarNotification.id)
            }
        }
    }

    fun isNotificationVisible(context: Context?, notificationId: Int): Boolean {
        var isVisible = false

        val notificationManager = context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = notificationManager.activeNotifications
        for (notification in notifications) {
            if (notification.id == notificationId) {
                isVisible = true
                break
            }
        }
        return isVisible
    }

    fun isCallsNotificationChannelEnabled(context: Context): Boolean {
        val channel = getNotificationChannel(context, getCallsNotificationChannelId(context))
        if (channel != null) {
            return isNotificationChannelEnabled(channel)
        }
        return false
    }

    fun isMessagesNotificationChannelEnabled(context: Context): Boolean {
        val channel = getNotificationChannel(context, getMessagesNotificationChannelId(context))
        if (channel != null) {
            return isNotificationChannelEnabled(channel)
        }
        return false
    }

    private fun isNotificationChannelEnabled(channel: NotificationChannel): Boolean =
        channel.importance != NotificationManager.IMPORTANCE_NONE

    private fun getRingtoneUri(
        context: Context,
        ringtonePreferencesString: String?,
        defaultRingtoneUri: String,
        channelId: String
    ): Uri? {
        val channel = getNotificationChannel(context, channelId)
        if (channel != null) {
            return channel.sound
        }
        // Notification channel will not be available when starting the application for the first time.
        // Ringtone uris are required to register the notification channels -> get uri from preferences.

        return if (TextUtils.isEmpty(ringtonePreferencesString)) {
            defaultRingtoneUri.toUri()
        } else {
            try {
                val ringtoneSettings =
                    LoganSquare.parse(ringtonePreferencesString, RingtoneSettings::class.java)
                ringtoneSettings.ringtoneUri
            } catch (exception: IOException) {
                NextcloudTalkApplication.sharedApplication?.logger?.w(
                    TAG,
                    "Failed to parse stored ringtone settings, falling back to default ringtone",
                    exception
                )
                defaultRingtoneUri.toUri()
            }
        }
    }

    fun getCallRingtoneUri(context: Context, appPreferences: AppPreferences): Uri? =
        getRingtoneUri(
            context,
            appPreferences.callRingtoneUri,
            DEFAULT_CALL_RINGTONE_URI,
            getCallsNotificationChannelId(context)
        )

    fun getMessageRingtoneUri(context: Context, appPreferences: AppPreferences): Uri? =
        getRingtoneUri(
            context,
            appPreferences.messageRingtoneUri,
            DEFAULT_MESSAGE_RINGTONE_URI,
            getMessagesNotificationChannelId(context)
        )

    fun loadAvatarSync(avatarUrl: String, context: Context, credentials: String? = null): IconCompat? {
        val bitmap = loadAvatarBitmapSync(avatarUrl, context, credentials)
        return bitmap?.let { IconCompat.createWithBitmap(it) }
    }

    fun loadAvatarBitmapSync(
        avatarUrl: String,
        context: Context,
        credentials: String? = null,
        useCache: Boolean = true
    ): Bitmap? {
        var avatarBitmap: Bitmap? = null

        val requestBuilder = ImageRequest.Builder(context)
            .data(avatarUrl)
            .transformations(CircleCropTransformation())
            .placeholder(R.drawable.account_circle_96dp)
            .listener(
                onError = { _, result ->
                    Log.w(TAG, "Can't load avatar for URL: $avatarUrl", result.throwable)
                }
            )
            .target(
                onSuccess = { result ->
                    avatarBitmap = (result as BitmapDrawable).bitmap
                },
                onError = { error ->
                    error?.let {
                        avatarBitmap = (error as BitmapDrawable).bitmap
                    }
                }
            )

        if (credentials != null) {
            requestBuilder.addHeader("Authorization", credentials)
        }
        if (!useCache) {
            requestBuilder
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
        }

        context.imageLoader.executeBlocking(requestBuilder.build())

        return avatarBitmap
    }

    /**
     * The conversation-avatar endpoint answers a one-to-one room with the other participant of the
     * requesting account, so the same URL returns a different image per account on the same server.
     * Those avatars bypass the caches, which are keyed by URL only.
     */
    fun loadConversationAvatarBitmapSync(
        baseUrl: String?,
        roomToken: String,
        credentials: String?,
        context: Context,
        isOneToOne: Boolean
    ): Bitmap? {
        val avatarUrl = ApiUtils.getUrlForConversationAvatar(ApiUtils.API_V1, baseUrl, roomToken)
        return loadAvatarBitmapSync(avatarUrl, context, credentials, useCache = !isOneToOne)
    }

    fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val sharedAttachmentsDir = FileUtils.getSharedAttachmentsDirectory(context.cacheDir) ?: return null
        val cacheFile = File(sharedAttachmentsDir, fileName)
        return try {
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, BITMAP_COMPRESSION_QUALITY, out)
            }
            FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, cacheFile)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save bitmap to cache", e)
            null
        }
    }

    /**
     * Notification avatar for actors without an avatar on the server, drawn by the client as
     * resolved by [CharacterAvatarUtils] instead of being requested.
     */
    fun characterAvatarBitmap(context: Context, avatar: ActorAvatar.Character): Bitmap =
        avatar.toDrawable(context).toBitmap(CHARACTER_AVATAR_ICON_SIZE, CHARACTER_AVATAR_ICON_SIZE)

    /**
     * Pixel size the character avatar is rasterized to for notifications, which cannot scale a
     * drawable themselves.
     */
    private const val CHARACTER_AVATAR_ICON_SIZE = 128

    private data class Channel(val id: String, val name: String, val description: String, val isImportant: Boolean)
}
