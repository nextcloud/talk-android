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
import androidx.work.Data
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.conversationlist.DirectShareHelper
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ApiUtils.getConversationApiVersion
import com.nextcloud.talk.utils.ApiUtils.getCredentials
import com.nextcloud.talk.utils.ApiUtils.getUrlForParticipantsSelf
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LeaveConversationWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderOld

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override suspend fun doWork(): Result {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val conversationToken = inputData.getString(BundleKeys.KEY_ROOM_TOKEN)
        val currentUser = withContext(Dispatchers.IO) { currentUserProvider.currentUser.blockingGet() }

        if (currentUser == null || conversationToken == null) {
            return Result.failure()
        }

        val credentials = getCredentials(currentUser.username, currentUser.token)
        val apiVersion = getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, 1))

        try {
            withContext(Dispatchers.IO) {
                ncApi.removeSelfFromRoom(
                    credentials,
                    getUrlForParticipantsSelf(apiVersion, currentUser.baseUrl, conversationToken)
                ).blockingSubscribe()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove self from room", e)
            val httpException = e as? HttpException
            val errorType = if (httpException?.code() == HTTP_ERROR_CODE_400) {
                ERROR_NO_OTHER_MODERATORS_OR_OWNERS_LEFT
            } else {
                ERROR_OTHER
            }
            return Result.failure(Data.Builder().putString("error_type", errorType).build())
        }

        currentUser.id?.let {
            DirectShareHelper.removeShortcutForConversation(applicationContext, it, conversationToken)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "LeaveConversationWorker"
        const val ERROR_NO_OTHER_MODERATORS_OR_OWNERS_LEFT = "NO_OTHER_MODERATORS_OR_OWNERS_LEFT"
        const val ERROR_OTHER = "ERROR_OTHER"
        const val HTTP_ERROR_CODE_400 = 400
    }
}
