/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class RemoteWipeSuccessWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    @Inject
    lateinit var ncApiCoroutines: NcApiCoroutines

    override fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return Result.failure()
        val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()

        return try {
            runBlocking {
                val url = ApiUtils.getUrlForRemoteWipeSuccess(baseUrl)
                ncApiCoroutines.reportRemoteWipeSuccess(url, token)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report remote wipe success to server", e)
            Result.failure()
        }
    }

    companion object {
        const val TAG = "RemoteWipeSuccessWorker"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_TOKEN = "token"
    }
}
