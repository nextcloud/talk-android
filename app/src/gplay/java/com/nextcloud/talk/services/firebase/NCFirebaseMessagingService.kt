/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.services.firebase

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.preferences.AppPreferences
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class NCFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        sharedApplication!!.componentApplication.inject(this)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "onMessageReceived")
        sharedApplication!!.componentApplication.inject(this)

        Log.d(TAG, "remoteMessage.priority: " + remoteMessage.priority)
        Log.d(TAG, "remoteMessage.originalPriority: " + remoteMessage.originalPriority)

        val data = remoteMessage.data
        val subject = data[KEY_NOTIFICATION_SUBJECT]
        val signature = data[KEY_NOTIFICATION_SIGNATURE]

        if (!subject.isNullOrEmpty() && !signature.isNullOrEmpty()) {
            val messageData = Data.Builder()
                .putString(BundleKeys.KEY_NOTIFICATION_SUBJECT, subject)
                .putString(BundleKeys.KEY_NOTIFICATION_SIGNATURE, signature)
                .build()
            val notificationWork =
                OneTimeWorkRequest.Builder(NotificationWorker::class.java).setInputData(messageData)
                    .build()
            WorkManager.getInstance().enqueue(notificationWork)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken. token = $token")

        appPreferences.pushToken = token
        appPreferences.pushTokenLatestGeneration = System.currentTimeMillis()

        val data: Data =
            Data.Builder().putString(PushRegistrationWorker.ORIGIN, "NCFirebaseMessagingService#onNewToken").build()
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
            .setInputData(data)
            .build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)
    }

    companion object {
        private val TAG = NCFirebaseMessagingService::class.simpleName
        const val KEY_NOTIFICATION_SUBJECT = "subject"
        const val KEY_NOTIFICATION_SIGNATURE = "signature"
    }
}
