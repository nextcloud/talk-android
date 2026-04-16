/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.content.ContextCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.receivers.EndCallReceiver
import com.nextcloud.talk.receivers.EndCallReceiver.Companion.END_CALL_ACTION
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder

class CallForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentNotificationId: Int = NOTIFICATION_ID

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        handler.removeCallbacksAndMessages(null)

        val conversationName = intent?.getStringExtra(EXTRA_CONVERSATION_NAME)
        val callExtras = intent?.getBundleExtra(EXTRA_CALL_INTENT_EXTRAS)
        val notification = buildNotification(conversationName, callExtras)

        val foregroundServiceType = resolveForegroundServiceType(callExtras)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && foregroundServiceType != FOREGROUND_SERVICE_TYPE_ZERO) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startTimeBasedNotificationUpdates()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(conversationName: String?, callExtras: Bundle?): Notification {
        val channelId = NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name
        ensureNotificationChannel()

        val contentTitle = conversationName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.nc_call_ongoing_notification_default_title)
        val pendingIntent = createContentIntent(callExtras)
        
        // Create action to return to call
        val returnToCallAction = NotificationCompat.Action.Builder(
            R.drawable.ic_call_white_24dp,
            getString(R.string.nc_call_ongoing_notification_return_action),
            pendingIntent
        ).build()
        
        // Create action to end call
        val endCallPendingIntent = createEndCallIntent(callExtras)
        
        val endCallAction = NotificationCompat.Action.Builder(
            R.drawable.ic_baseline_close_24,
            getString(R.string.nc_call_ongoing_notification_end_action),
            endCallPendingIntent
        ).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return buildCallStyleNotification(contentTitle, pendingIntent)
        }

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
            .addAction(returnToCallAction)
            .addAction(endCallAction)
            .setAutoCancel(false)
            .build()
    }

    @SuppressLint("NewApi")
    private fun buildCallStyleNotification(
        contentTitle: String,
        pendingIntent: PendingIntent
    ): Notification {
        val caller = Person.Builder()
            .setName(contentTitle)
            .setIcon(Icon.createWithResource(this, R.drawable.ic_call_white_24dp))
            .setImportant(true)
            .build()

        val callStyle = Notification.CallStyle.forOngoingCall(
            caller,
            createHangupPendingIntent()
        )

        val channelId = NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_CALLS_V4.name

        val callStartTime = ApplicationWideCurrentRoomHolder.getInstance().callStartTime

        return Notification.Builder(this, channelId)
            .setStyle(callStyle)
            .setSmallIcon(R.drawable.ic_call_white_24dp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(false)
            .also { builder ->
                if (callStartTime != null && callStartTime > 0) {
                    builder.setWhen(callStartTime)
                    builder.setShowWhen(true)
                }
            }
            .build()
    }

    @SuppressLint("NewApi", "ForegroundServiceType")
    private fun startTimeBasedNotificationUpdates() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val updateRunnable = object : Runnable {
            override fun run() {
                val callStartTime = ApplicationWideCurrentRoomHolder.getInstance().callStartTime
                if (callStartTime != null && callStartTime > 0) {
                    val conversationName = ApplicationWideCurrentRoomHolder.getInstance()
                        .userInRoom?.displayName
                        ?: getString(R.string.nc_call_ongoing_notification_default_title)
                    val pendingIntent = createContentIntent(null)
                    val notification = buildCallStyleNotification(conversationName, pendingIntent)

                    startForeground(NOTIFICATION_ID, notification)
                }
                handler.postDelayed(this, CALL_DURATION_UPDATE_INTERVAL)
            }
        }
        handler.postDelayed(updateRunnable, CALL_DURATION_UPDATE_INTERVAL)
    }

    private fun ensureNotificationChannel() {
        val app = NextcloudTalkApplication.sharedApplication ?: return
        NotificationUtils.registerNotificationChannels(applicationContext, app.appPreferences)
    }

    private fun createContentIntent(callExtras: Bundle?): PendingIntent {
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            callExtras?.let { putExtras(Bundle(it)) }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }
    
    private fun createEndCallIntent(callExtras: Bundle?): PendingIntent {
        val intent = Intent(this, EndCallReceiver::class.java).apply {
            action = END_CALL_ACTION
            callExtras?.let { putExtras(Bundle(it)) }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(this, 1, intent, flags)
    }

    private fun createHangupPendingIntent(): PendingIntent {
        val intent = Intent(ACTION_HANGUP).apply {
            setPackage(packageName)
        }
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        private val TAG = CallForegroundService::class.java.simpleName
        private const val NOTIFICATION_ID = 47001
        private const val FOREGROUND_SERVICE_TYPE_ZERO = 0
        private const val EXTRA_CONVERSATION_NAME = "extra_conversation_name"
        private const val EXTRA_CALL_INTENT_EXTRAS = "extra_call_intent_extras"
        private const val ACTION_HANGUP = "com.nextcloud.talk.ACTION_HANGUP"
        private const val CALL_DURATION_UPDATE_INTERVAL = 1000L

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
