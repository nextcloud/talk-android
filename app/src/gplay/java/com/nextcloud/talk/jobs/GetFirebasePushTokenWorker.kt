/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.preferences.AppPreferences
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class GetFirebasePushTokenWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    @Inject
    lateinit var appPreferences: AppPreferences

    @SuppressLint("LongLogTag")
    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        FirebaseMessaging.getInstance().token.addOnCompleteListener(
            OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }

                val pushToken = task.result
                Log.d(TAG, "Fetched firebase push token is: $pushToken")

                appPreferences.pushToken = pushToken
                appPreferences.pushTokenLatestFetch = System.currentTimeMillis()

                val data: Data =
                    Data.Builder().putString(PushRegistrationWorker.ORIGIN, "GetFirebasePushTokenWorker").build()
                val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(context).enqueue(pushRegistrationWork)
            }
        )

        return Result.success()
    }

    companion object {
        private val TAG = GetFirebasePushTokenWorker::class.simpleName
    }
}
