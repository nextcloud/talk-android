/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
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
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class AddParticipantsToConversationWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var eventBus: EventBus

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        val selectedUserIds = inputData.getStringArray(BundleKeys.KEY_SELECTED_USERS)
        val selectedGroupIds = inputData.getStringArray(BundleKeys.KEY_SELECTED_GROUPS)
        val selectedCircleIds = inputData.getStringArray(BundleKeys.KEY_SELECTED_CIRCLES)
        val selectedEmails = inputData.getStringArray(BundleKeys.KEY_SELECTED_EMAILS)
        val internalUserId = inputData.getLong(BundleKeys.KEY_INTERNAL_USER_ID, -1)
        val user = userManager.getUserWithInternalId(internalUserId) ?: return Result.failure()

        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))

        val conversationToken = inputData.getString(BundleKeys.KEY_TOKEN)
        val credentials = ApiUtils.getCredentials(user.username, user.token)

        val failedParticipants = withContext(Dispatchers.IO) {
            addParticipants(apiVersion, user, credentials, conversationToken, null, selectedUserIds) +
                addParticipants(apiVersion, user, credentials, conversationToken, "groups", selectedGroupIds) +
                addParticipants(apiVersion, user, credentials, conversationToken, "circles", selectedCircleIds) +
                addParticipants(apiVersion, user, credentials, conversationToken, "emails", selectedEmails)
        }

        if (failedParticipants.isEmpty()) {
            return Result.success()
        }

        val output = Data.Builder()
            .putStringArray(KEY_FAILED_PARTICIPANTS, failedParticipants.toTypedArray())
            .build()
        val requested = listOf(selectedUserIds, selectedGroupIds, selectedCircleIds, selectedEmails)
            .sumOf { it?.size ?: 0 }
        return if (failedParticipants.size == requested) Result.failure(output) else Result.success(output)
    }

    /**
     * Invites every given id, one request each, and returns those the server refused.
     */
    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    private fun addParticipants(
        apiVersion: Int,
        user: User,
        credentials: String?,
        conversationToken: String?,
        source: String?,
        ids: Array<String>?
    ): List<String> {
        if (ids == null) {
            return emptyList()
        }

        val failed = mutableListOf<String>()
        for (id in ids) {
            val retrofitBucket = if (source == null) {
                ApiUtils.getRetrofitBucketForAddParticipant(apiVersion, user.baseUrl, conversationToken, id)
            } else {
                ApiUtils.getRetrofitBucketForAddParticipantWithSource(
                    apiVersion,
                    user.baseUrl,
                    conversationToken,
                    source,
                    id
                )
            }
            try {
                ncApi.addParticipant(credentials, retrofitBucket.url, retrofitBucket.queryMap)
                    .blockingSubscribe()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Adding a participant of source ${source ?: "users"} failed", e)
                failed.add(id)
            }
        }
        return failed
    }

    companion object {
        /**
         * The ids the server refused, in the output data of both a partial success and a failure.
         */
        const val KEY_FAILED_PARTICIPANTS = "KEY_FAILED_PARTICIPANTS"

        private val TAG = AddParticipantsToConversationWorker::class.java.simpleName
    }
}
