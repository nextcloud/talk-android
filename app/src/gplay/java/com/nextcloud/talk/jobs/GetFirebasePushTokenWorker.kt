/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.nextcloud.talk.utils.preferences.AppPreferences
import javax.inject.Inject

class GetFirebasePushTokenWorker(val context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    @SuppressLint("LongLogTag")
    override fun doWork(): Result {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            val token = task.result

            appPreferences?.pushToken = token

            val data: Data = Data.Builder().putString(PushRegistrationWorker.ORIGIN, "GetFirebasePushTokenWorker").build()
            val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(pushRegistrationWork)
        })

        return Result.success()
    }

    companion object {
        const val TAG = "GetFirebasePushTokenWorker"
    }
}
