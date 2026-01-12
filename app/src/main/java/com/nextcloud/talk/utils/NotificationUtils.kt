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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import coil.executeBlocking
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.transform.CircleCropTransformation
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.BubbleActivity
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("TooManyFunctions")
object NotificationUtils {

    const val TAG = "NotificationUtils"
    private const val BUBBLE_ICON_SIZE_DP = 96
    const val BUBBLE_DESIRED_HEIGHT_PX = 600
    private const val BUBBLE_ICON_CONTENT_RATIO = 0.68f
    private const val BUBBLE_SIZE_MULTIPLIER = 4
    private const val MIN_BUBBLE_CONTENT_RATIO = 0.5f
    private val bubbleIconCache = ConcurrentHashMap<String, IconCompat>()

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

    // notification group keys
    const val KEY_UPLOAD_GROUP = "com.nextcloud.talk.utils.KEY_UPLOAD_GROUP"
    const val GROUP_SUMMARY_NOTIFICATION_ID = -1

    val deviceSupportsBubbles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun areSystemBubblesEnabled(context: Context): Boolean {
        if (!deviceSupportsBubbles) {
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Settings.Secure.getInt(
                context.contentResolver,
                "notification_bubbles",
                1
            ) == 1
        } else {
            // Android 10 (Q) — bubbles always enabled
            true
        }
    }

    fun isSystemBubblePreferenceAll(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return notificationManager?.bubblePreference == NotificationManager.BUBBLE_PREFERENCE_ALL
    }

    private fun createNotificationChannel(
        context: Context,
        notificationChannel: Channel,
        sound: Uri?,
        audioAttributes: AudioAttributes?
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isMessagesChannel = notificationChannel.id == NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name
        val shouldSupportBubbles = deviceSupportsBubbles && isMessagesChannel

        val existingChannel = notificationManager.getNotificationChannel(notificationChannel.id)
        val needsRecreation = shouldSupportBubbles && existingChannel != null && !existingChannel.canBubble()

        if (existingChannel == null || needsRecreation) {
            if (needsRecreation) {
                notificationManager.deleteNotificationChannel(notificationChannel.id)
            }

            val importance = if (notificationChannel.isImportant) {
                NotificationManager.IMPORTANCE_HIGH
            } else {
                NotificationManager.IMPORTANCE_LOW
            }

            val channel = NotificationChannel(notificationChannel.id, notificationChannel.name, importance).apply {
                description = notificationChannel.description
                enableLights(true)
                lightColor = R.color.colorPrimary
                setSound(sound, audioAttributes)
                setBypassDnd(false)
                if (shouldSupportBubbles) {
                    setAllowBubbles(true)
                }
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createCallsNotificationChannel(context: Context, appPreferences: AppPreferences) {
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

    private fun createMessagesNotificationChannel(context: Context, appPreferences: AppPreferences) {
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
        createCallsNotificationChannel(context, appPreferences)
        createMessagesNotificationChannel(context, appPreferences)
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
            if (!channelsToKeep.contains(channel.id)) {
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
            val matchesId = notificationId == notification.extras.getLong(BundleKeys.KEY_NOTIFICATION_ID)

            val isBubble =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    (notification.flags and Notification.FLAG_BUBBLE) != 0

            if (matchesId && !isBubble) {
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

    private fun dismissBubbles(context: Context?, conversationUser: User, predicate: (String) -> Boolean) {
        if (context == null) return

        val shortcutsToRemove = mutableListOf<String>()

        scanNotifications(context, conversationUser) { notificationManager, statusBarNotification, notification ->
            val roomToken = notification.extras.getString(BundleKeys.KEY_ROOM_TOKEN)
            if (roomToken != null && predicate(roomToken)) {
                notificationManager.cancel(statusBarNotification.id)
                shortcutsToRemove.add("conversation_$roomToken")
            }
        }

        if (shortcutsToRemove.isNotEmpty()) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, shortcutsToRemove)
        }
    }

    fun dismissBubbleForRoom(context: Context?, conversationUser: User, roomTokenOrId: String) {
        dismissBubbles(context, conversationUser) { it == roomTokenOrId }
    }

    fun dismissAllBubbles(context: Context?, conversationUser: User) {
        dismissBubbles(context, conversationUser) { true }
    }

    fun dismissBubblesWithoutExplicitSettings(context: Context?, conversationUser: User) {
        dismissBubbles(context, conversationUser) { roomToken ->
            !com.nextcloud.talk.utils.preferences.preferencestorage.DatabaseStorageModule(
                conversationUser,
                roomToken
            ).getBoolean("bubble_switch", false)
        }
    }

    fun isNotificationVisible(context: Context?, notificationId: Int): Boolean {
        val notificationManager = context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.activeNotifications.any { it.id == notificationId }
    }

    fun isCallsNotificationChannelEnabled(context: Context): Boolean =
        getNotificationChannel(context, NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name)
            ?.let { isNotificationChannelEnabled(it) } ?: false

    fun isMessagesNotificationChannelEnabled(context: Context): Boolean =
        getNotificationChannel(context, NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name)
            ?.let { isNotificationChannelEnabled(it) } ?: false

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
                defaultRingtoneUri.toUri()
            }
        }
    }

    fun getCallRingtoneUri(context: Context, appPreferences: AppPreferences): Uri? =
        getRingtoneUri(
            context,
            appPreferences.callRingtoneUri,
            DEFAULT_CALL_RINGTONE_URI,
            NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name
        )

    fun getMessageRingtoneUri(context: Context, appPreferences: AppPreferences): Uri? =
        getRingtoneUri(
            context,
            appPreferences.messageRingtoneUri,
            DEFAULT_MESSAGE_RINGTONE_URI,
            NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name
        )

    fun loadAvatarSync(avatarUrl: String, context: Context): IconCompat? {
        var avatarIcon: IconCompat? = null

        val request = ImageRequest.Builder(context)
            .data(avatarUrl)
            .transformations(CircleCropTransformation())
            .placeholder(R.drawable.account_circle_96dp)
            .target(
                onSuccess = { result ->
                    val bitmap = (result as BitmapDrawable).bitmap
                    avatarIcon = IconCompat.createWithBitmap(bitmap)
                },
                onError = { error ->
                    error?.let {
                        val bitmap = (error as BitmapDrawable).bitmap
                        avatarIcon = IconCompat.createWithBitmap(bitmap)
                    }
                    Log.w(TAG, "Can't load avatar for URL: $avatarUrl")
                }
            )
            .build()

        context.imageLoader.executeBlocking(request)

        return avatarIcon
    }

    fun loadAvatarSyncForBubble(url: String?, context: Context, credentials: String?): IconCompat? {
        if (url.isNullOrEmpty()) {
            Log.w(TAG, "Avatar URL is null or empty for bubble")
            return null
        }

        return bubbleIconCache[url] ?: run {
            var avatarIcon: IconCompat? = null
            val bubbleSizePx = context.bubbleIconSizePx()

            val requestBuilder = ImageRequest.Builder(context)
                .data(url)
                .placeholder(R.drawable.account_circle_96dp)
                .size(bubbleSizePx * BUBBLE_SIZE_MULTIPLIER, bubbleSizePx * BUBBLE_SIZE_MULTIPLIER)
                .precision(Precision.EXACT)
                .scale(Scale.FIT)
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888)

            if (!credentials.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", credentials)
            }

            val request = requestBuilder.target(
                onSuccess = { result ->
                    avatarIcon = IconCompat.createWithAdaptiveBitmap(
                        result.toBubbleBitmap(bubbleSizePx, BUBBLE_ICON_CONTENT_RATIO)
                    )
                },
                onError = { error ->
                    (error ?: ContextCompat.getDrawable(context, R.drawable.account_circle_96dp))?.let {
                        avatarIcon = IconCompat.createWithAdaptiveBitmap(
                            it.toBubbleBitmap(bubbleSizePx, BUBBLE_ICON_CONTENT_RATIO)
                        )
                    }
                }
            )
                .build()

            context.imageLoader.executeBlocking(request)

            avatarIcon?.also { bubbleIconCache[url] = it }
        }
    }

    private data class Channel(val id: String, val name: String, val description: String, val isImportant: Boolean)

    private fun Context.bubbleIconSizePx(): Int =
        (BUBBLE_ICON_SIZE_DP * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    private fun Drawable.toBubbleBitmap(size: Int, contentRatio: Float): Bitmap {
        val safeRatio = contentRatio.coerceIn(MIN_BUBBLE_CONTENT_RATIO, 1f)
        val drawable = this.constantState?.newDrawable()?.mutate() ?: this.mutate()

        val sourceWidth = max(1, if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else size)
        val sourceHeight = max(1, if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else size)
        val sourceBitmap = drawable.toBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)

        val minDimension = min(sourceWidth, sourceHeight)
        val cropX = (sourceWidth - minDimension) / 2
        val cropY = (sourceHeight - minDimension) / 2
        val squareBitmap = Bitmap.createBitmap(sourceBitmap, cropX, cropY, minDimension, minDimension)
        if (squareBitmap != sourceBitmap) {
            sourceBitmap.recycle()
        }

        val resultBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        canvas.drawARGB(0, 0, 0, 0)
        paint.color = Color.BLACK
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val targetDiameter = (size * safeRatio).roundToInt().coerceAtLeast(1)
        val destRect = Rect(
            ((size - targetDiameter) / 2f).roundToInt(),
            ((size - targetDiameter) / 2f).roundToInt(),
            ((size + targetDiameter) / 2f).roundToInt(),
            ((size + targetDiameter) / 2f).roundToInt()
        )
        canvas.drawBitmap(squareBitmap, null, destRect, paint)
        paint.xfermode = null

        if (!squareBitmap.isRecycled) {
            squareBitmap.recycle()
        }

        return resultBitmap
    }

    suspend fun createConversationBubble(
        context: Context,
        roomToken: String,
        conversationRemoteId: String,
        conversationName: String?,
        conversationUser: User,
        isOneToOneConversation: Boolean,
        credentials: String?,
        appPreferences: AppPreferences
    ) {
        try {
            val shortcutId = "conversation_$roomToken"
            val bubbleConversationName = conversationName ?: context.getString(R.string.nc_app_name)

            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as android.app.NotificationManager
            val existingNotification = findNotificationForRoom(
                context,
                conversationUser,
                roomToken
            )
            val notificationId = existingNotification?.id
                ?: calculateCRC32(roomToken).toInt()

            notificationManager.cancel(notificationId)
            ShortcutManagerCompat.removeDynamicShortcuts(
                context,
                listOf(shortcutId)
            )

            // Load conversation avatar on background thread
            val avatarIcon = withContext(Dispatchers.IO) {
                try {
                    var avatarUrl = if (isOneToOneConversation) {
                        ApiUtils.getUrlForAvatar(
                            conversationUser.baseUrl!!,
                            conversationRemoteId,
                            true
                        )
                    } else {
                        ApiUtils.getUrlForConversationAvatar(
                            ApiUtils.API_V1,
                            conversationUser.baseUrl!!,
                            roomToken
                        )
                    }

                    if (DisplayUtils.isDarkModeOn(context)) {
                        avatarUrl = "$avatarUrl/dark"
                    }

                    loadAvatarSyncForBubble(avatarUrl, context, credentials)
                } catch (e: IOException) {
                    Log.e(TAG, "Error loading bubble avatar: IO error", e)
                    null
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Error loading bubble avatar: Invalid argument", e)
                    null
                }
            }

            val icon = avatarIcon ?: androidx.core.graphics.drawable.IconCompat.createWithResource(
                context,
                R.drawable.ic_logo
            )

            val person = androidx.core.app.Person.Builder()
                .setName(bubbleConversationName)
                .setKey(shortcutId)
                .setImportant(true)
                .setIcon(icon)
                .build()

            // Use the same request code calculation as NotificationWorker
            val bubbleRequestCode = calculateCRC32("bubble_$roomToken").toInt()

            val bubbleIntent = android.app.PendingIntent.getActivity(
                context,
                bubbleRequestCode,
                BubbleActivity.newIntent(context, roomToken, bubbleConversationName),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )

            val contentIntent = android.app.PendingIntent.getActivity(
                context,
                bubbleRequestCode,
                Intent(context, ChatActivity::class.java).apply {
                    putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                    conversationName?.let { putExtra(BundleKeys.KEY_CONVERSATION_NAME, it) }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val shortcutIntent = Intent(context, ChatActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
                conversationName?.let { putExtra(BundleKeys.KEY_CONVERSATION_NAME, it) }
            }

            val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(bubbleConversationName)
                .setLongLabel(bubbleConversationName)
                .setIcon(icon)
                .setIntent(shortcutIntent)
                .setLongLived(true)
                .setPerson(person)
                .setCategories(setOf(android.app.Notification.CATEGORY_MESSAGE))
                .setLocusId(androidx.core.content.LocusIdCompat(shortcutId))
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)

            val bubbleData = androidx.core.app.NotificationCompat.BubbleMetadata.Builder(
                bubbleIntent,
                icon
            )
                .setDesiredHeight(BUBBLE_DESIRED_HEIGHT_PX)
                .setAutoExpandBubble(false)
                .setSuppressNotification(true)
                .build()

            val messagingStyle = androidx.core.app.NotificationCompat.MessagingStyle(person)
                .setConversationTitle(bubbleConversationName)

            val notificationExtras = bundleOf(
                BundleKeys.KEY_ROOM_TOKEN to roomToken,
                BundleKeys.KEY_INTERNAL_USER_ID to conversationUser.id!!
            )

            val channelId = NotificationChannels.NOTIFICATION_CHANNEL_MESSAGES_V4.name
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle(bubbleConversationName)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                .setShortcutId(shortcutId)
                .setLocusId(androidx.core.content.LocusIdCompat(shortcutId))
                .addPerson(person)
                .setStyle(messagingStyle)
                .setBubbleMetadata(bubbleData)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .setExtras(notificationExtras)
                .build()

            // Check if notification channel supports bubbles and recreate if needed
            val channel = notificationManager.getNotificationChannel(channelId)

            if (channel == null || deviceSupportsBubbles && !channel.canBubble()) {
                registerNotificationChannels(
                    context,
                    appPreferences
                )
            }

            // Use the same notification ID calculation as NotificationWorker
            // Show notification with bubble
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Error creating bubble: Permission denied", e)
            android.widget.Toast.makeText(context, R.string.nc_common_error_sorry, android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error creating bubble: Invalid argument", e)
            android.widget.Toast.makeText(context, R.string.nc_common_error_sorry, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Calculate CRC32 hash for a string, commonly used for generating notification IDs
     */
    fun calculateCRC32(s: String): Long {
        val crc32 = CRC32()
        crc32.update(s.toByteArray())
        return crc32.value
    }
}
