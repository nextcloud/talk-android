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
package com.nextcloud.talk.services.firebase

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.newarch.services.CallService
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INCOMING_PUSH_MESSSAGE
import com.nextcloud.talk.utils.preferences.AppPreferences
import org.koin.core.KoinComponent
import org.koin.core.inject

class MagicFirebaseMessagingService : FirebaseMessagingService(), KoinComponent {
    val tag: String = "MagicFirebaseMessagingService"
    val appPreferences: AppPreferences by inject()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        appPreferences.pushToken = token
    }

    @SuppressLint("LongLogTag")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val messageData = Data.Builder()
                .putString(BundleKeys.KEY_ENCRYPTED_SUBJECT, remoteMessage.data["subject"])
                .putString(BundleKeys.KEY_ENCRYPTED_SIGNATURE, remoteMessage.data["signature"])
                .build()

        val pushNotificationWork = OneTimeWorkRequest.Builder(NotificationWorker::class.java).setInputData(messageData).build()
        WorkManager.getInstance().enqueue(pushNotificationWork)
    }
}