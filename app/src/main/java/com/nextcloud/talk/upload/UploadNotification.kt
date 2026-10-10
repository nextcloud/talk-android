/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.receivers.UploadCancelReceiver
import com.nextcloud.talk.utils.NotificationUtils

/** The ongoing notification of the foreground service that keeps an upload running. */
object UploadNotification {

    private const val MAX_PERCENT = 100

    @Suppress("LongParameterList")
    fun build(
        context: Context,
        notificationId: Int,
        fileName: String,
        percent: Int,
        workId: String,
        referenceId: String?,
        internalConversationId: String?
    ): Notification {
        val cancelIntent = Intent(context, UploadCancelReceiver::class.java)
            .putExtra(UploadCancelReceiver.KEY_WORK_ID, workId)
            .putExtra(UploadCancelReceiver.KEY_REFERENCE_ID, referenceId)
            .putExtra(UploadCancelReceiver.KEY_INTERNAL_CONVERSATION_ID, internalConversationId)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(
            context,
            NotificationUtils.NotificationChannels.NOTIFICATION_CHANNEL_UPLOADS.name
        )
            .setContentTitle(context.getString(R.string.nc_upload_in_progess))
            .setContentText(fileName)
            .setSmallIcon(R.drawable.upload_white)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(MAX_PERCENT, percent, percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_baseline_close_24, context.getString(R.string.nc_cancel), cancelPendingIntent)
            .build()
    }
}
