/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class RemoteWipeSuccessWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    @Inject
    lateinit var ncApiCoroutines: NcApiCoroutines

    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val baseUrl = inputData.getString(KEY_BASE_URL)
        val token = inputData.getString(KEY_TOKEN)
        return if (baseUrl == null || token == null) Result.failure() else reportWipeSuccess(baseUrl, token)
    }

    private fun reportWipeSuccess(baseUrl: String, token: String): Result =
        try {
            val url = ApiUtils.getUrlForRemoteWipeSuccess(baseUrl)
            val response = runBlocking { ncApiCoroutines.reportRemoteWipeSuccess(url, token) }
            when {
                response.isSuccessful -> Result.success()

                response.code() == HTTP_TOO_MANY_REQUESTS || response.code() >= HTTP_INTERNAL_SERVER_ERROR -> {
                    Log.w(TAG, "Server could not take the remote wipe success now: ${response.code()}")
                    retryOrFail()
                }

                else -> {
                    // e.g. 404 when the server does not know the wipe of the token any more, so trying again won't help
                    Log.e(TAG, "Server refused the remote wipe success: ${response.code()}")
                    Result.failure()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to report remote wipe success to server", e)
            retryOrFail()
        }

    private fun retryOrFail(): Result = if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()

    companion object {
        const val TAG = "RemoteWipeSuccessWorker"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_TOKEN = "token"
        private const val MAX_ATTEMPTS = 10
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_INTERNAL_SERVER_ERROR = 500

        /**
         * Reports to the server at [baseUrl] that the wipe it requested for the app password [token] is done. It is
         * sent once the device is online, and tried again later if it failed for a reason that may pass.
         */
        fun workRequest(baseUrl: String, token: String): OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(RemoteWipeSuccessWorker::class.java)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_BASE_URL, baseUrl)
                        .putString(KEY_TOKEN, token)
                        .build()
                )
                .build()
    }
}
