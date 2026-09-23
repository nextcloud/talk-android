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
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SignalingSettingsWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var eventBus: EventBus

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val internalUserId = inputData.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1)
        val internalUser = userManager.getUserWithInternalId(internalUserId)

        val users = if (internalUserId == -1L || internalUser == null) {
            userManager.getUsers()
        } else {
            listOf(internalUser)
        }

        for (user in users) {
            fetchSignalingSettings(user)
        }

        return Result.success()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchSignalingSettings(user: User) {
        val apiVersion = ApiUtils.getSignalingApiVersion(user, intArrayOf(ApiUtils.API_V3, 2, 1))

        val signalingSettingsOverall: SignalingSettingsOverall = try {
            withContext(Dispatchers.IO) {
                ncApi.getSignalingSettings(
                    ApiUtils.getCredentials(user.username, user.token),
                    ApiUtils.getUrlForSignalingSettings(apiVersion, user.baseUrl)
                ).blockingFirst()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            postSignalingSettingsEvent(user, false)
            return
        }

        val externalSignalingServer = ExternalSignalingServer()
        signalingSettingsOverall.ocs?.settings?.let { settings ->
            externalSignalingServer.externalSignalingServer = settings.externalSignalingServer
            externalSignalingServer.externalSignalingTicket = settings.externalSignalingTicket
        }
        user.externalSignalingServer = externalSignalingServer

        val saved = try {
            userManager.saveUser(user) > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        postSignalingSettingsEvent(user, saved)
    }

    private fun postSignalingSettingsEvent(user: User, allGood: Boolean) {
        eventBus.post(
            EventStatus(
                UserIdUtils.getIdForUser(user),
                EventStatus.EventType.SIGNALING_SETTINGS,
                allGood
            )
        )
    }
}
