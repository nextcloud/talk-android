/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import retrofit2.Retrofit
import javax.inject.Inject

/**
 * Fetches capabilities for a single account (e.g. right after login, or a manual refresh) and
 * retries with backoff until it succeeds, instead of silently waiting for the next
 * [CapabilitiesSyncWorker] periodic run - which can be hours away. Callers configure the retry
 * backoff/constraints on the enqueued [androidx.work.OneTimeWorkRequest] itself.
 */
@AutoInjector(NextcloudTalkApplication::class)
class CapabilitiesFetchWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var retrofit: Retrofit

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val internalUserId = inputData.getLong(BundleKeys.KEY_INTERNAL_USER_ID, NO_USER_ID)
        val user = if (internalUserId != NO_USER_ID) {
            userManager.getUserWithInternalId(internalUserId).blockingGet()
        } else {
            null
        }

        if (user == null) {
            Log.e(TAG, "No user found for internal id $internalUserId, dropping capabilities fetch")
            return Result.failure()
        }

        val fetcher = CapabilitiesFetcher(userManager, retrofit, eventBus, okHttpClient)
        val succeeded = fetcher.fetchAndStoreCapabilities(user)

        return if (succeeded) Result.success() else Result.retry()
    }

    companion object {
        private val TAG = CapabilitiesFetchWorker::class.java.simpleName
        private const val NO_USER_ID = -1L
    }
}
