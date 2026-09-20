/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.users.UserManager
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import retrofit2.Retrofit
import javax.inject.Inject

/**
 * Best-effort capabilities refresh for every stored account. Used by the app-start work chain and
 * the periodic (every [com.nextcloud.talk.application.NextcloudTalkApplication.HALF_DAY]h) sync, as
 * well as a manual refresh from settings.
 *
 * A failure for one account must not fail the whole job or block dependent work in the app-start
 * chain, so this always returns success - the next periodic run picks failures back up.
 */
@AutoInjector(NextcloudTalkApplication::class)
class CapabilitiesSyncWorker(context: Context, workerParams: WorkerParameters) :
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

        val fetcher = CapabilitiesFetcher(userManager, retrofit, eventBus, okHttpClient)
        userManager.users.blockingGet().forEach { user ->
            fetcher.fetchAndStoreCapabilities(user)
        }

        return Result.success()
    }
}
