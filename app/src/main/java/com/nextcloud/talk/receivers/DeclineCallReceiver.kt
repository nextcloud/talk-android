/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.DisconnectCause
import androidx.core.app.NotificationManagerCompat
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_NOTIFICATION_TIMESTAMP
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN

class DeclineCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(KEY_NOTIFICATION_TIMESTAMP, 0)
        NotificationManagerCompat.from(context).cancel(notificationId)
        NextcloudTalkApplication.sharedApplication?.telecomManager?.endRingingCall(
            intent.getStringExtra(KEY_ROOM_TOKEN),
            DisconnectCause.REJECTED
        )
    }
}
