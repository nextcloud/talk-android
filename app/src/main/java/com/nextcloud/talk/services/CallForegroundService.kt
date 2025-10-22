/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.content.ContextCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO

class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val conversationName = intent?.getStringExtra(EXTRA_CONVERSATION_NAME)
        val callExtras = intent?.getBundleExtra(EXTRA_CALL_INTENT_EXTRAS)
        val notification = buildNotification(conversationName, callExtras)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = resolveForegroundServiceType(callExtras)
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(conversationName: String?, callExtras: Bundle?): Notification {
        val channelId = NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name
        ensureNotificationChannel()

        val contentTitle = conversationName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.nc_call_ongoing_notification_default_title)
        val pendingIntent = createContentIntent(callExtras)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(contentTitle)
            .setContentText(getString(R.string.nc_call_ongoing_notification_content))
            .setSmallIcon(R.drawable.ic_call_white_24dp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .build()
    }

    private fun ensureNotificationChannel() {
        val app = NextcloudTalkApplication.sharedApplication ?: return
        NotificationUtils.registerNotificationChannels(applicationContext, app.appPreferences)
    }

    private fun createContentIntent(callExtras: Bundle?): PendingIntent {
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            callExtras?.let { putExtras(Bundle(it)) }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun resolveForegroundServiceType(callExtras: Bundle?): Int {
        var serviceType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

            val isVoiceOnlyCall = callExtras?.getBoolean(KEY_CALL_VOICE_ONLY, false) ?: false
            val canPublishVideo = callExtras?.getBoolean(
                KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO,
                false
            ) ?: false

            if (!isVoiceOnlyCall && canPublishVideo) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        }
        return serviceType
    }

    companion object {
        private const val NOTIFICATION_ID = 47001
        private const val EXTRA_CONVERSATION_NAME = "extra_conversation_name"
        private const val EXTRA_CALL_INTENT_EXTRAS = "extra_call_intent_extras"

        fun start(context: Context, conversationName: String?, callIntentExtras: Bundle?) {
            val serviceIntent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_CONVERSATION_NAME, conversationName)
                callIntentExtras?.let { putExtra(EXTRA_CALL_INTENT_EXTRAS, Bundle(it)) }
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
