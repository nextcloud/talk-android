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
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Sends the user's read marker to the server, retrying transient failures with backoff.
 *
 * The caller updates the local conversation entry optimistically before enqueuing this worker, so
 * the conversation list reflects the read state immediately. The server stays the authority:
 * every room list sync re-asserts the server's read state over the local entry — guarded by the
 * pending marker in [ChatMessageSyncer] only while the marker is provably not delivered yet.
 * When all attempts fail, the pending marker is released so the client falls back to the server
 * state at the next sync instead of staying diverged. Work is unique per room with
 * [ExistingWorkPolicy.REPLACE], so the newest marker for a room always wins and retries can never
 * ship a stale marker backwards.
 */
@AutoInjector(NextcloudTalkApplication::class)
class ReadMarkerSyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var chatNetworkDataSource: ChatNetworkDataSource

    @Inject
    lateinit var chatMessageSyncer: ChatMessageSyncer

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val userId = inputData.getLong(KEY_INTERNAL_USER_ID, -1)
        val roomToken = inputData.getString(KEY_ROOM_TOKEN)
        val lastReadMessage = inputData.getInt(KEY_LAST_READ_MESSAGE, -1)

        return when {
            userId < 0 || roomToken.isNullOrEmpty() || lastReadMessage < 0 -> {
                Log.e(TAG, "Missing user id, room token or read marker, dropping read marker sync")
                Result.failure()
            }

            else -> sendReadMarker(userId, roomToken, lastReadMessage)
        }
    }

    private fun sendReadMarker(userId: Long, roomToken: String, lastReadMessage: Int): Result {
        val user = userManager.getUserWithId(userId).blockingGet()
        val credentials = user?.let { ApiUtils.getCredentials(it.username, it.token) }
        if (user == null || credentials == null) {
            Log.e(TAG, "No user or credentials found for user id $userId, dropping read marker sync")
            return fail(userId, roomToken, lastReadMessage)
        }

        val sent = runCatching {
            val url = ApiUtils.getUrlForChatReadMarker(
                ApiUtils.getChatApiVersion(user.capabilities!!.spreedCapability!!, intArrayOf(ApiUtils.API_V1)),
                user.baseUrl!!,
                roomToken
            )
            chatNetworkDataSource.setChatReadMarker(credentials, url, lastReadMessage).blockingSingle()
        }.isSuccess

        return if (sent) {
            Log.d(TAG, "Read marker $lastReadMessage sent for room $roomToken")
            Result.success()
        } else {
            Log.w(TAG, "Sending read marker for room $roomToken failed (attempt ${runAttemptCount + 1})")
            retryOrFail(userId, roomToken, lastReadMessage)
        }
    }

    private fun retryOrFail(userId: Long, roomToken: String, lastReadMessage: Int): Result =
        if (runAttemptCount < MAX_RUN_ATTEMPTS - 1) {
            Result.retry()
        } else {
            fail(userId, roomToken, lastReadMessage)
        }

    private fun fail(userId: Long, roomToken: String, lastReadMessage: Int): Result {
        chatMessageSyncer.clearPendingReadMarker("$userId@$roomToken", lastReadMessage)
        return Result.failure()
    }

    companion object {
        private val TAG: String = ReadMarkerSyncWorker::class.java.simpleName
        private const val KEY_LAST_READ_MESSAGE = "KEY_LAST_READ_MESSAGE"
        private const val MAX_RUN_ATTEMPTS = 3

        fun enqueue(context: Context, userId: Long, roomToken: String, lastReadMessage: Int) {
            val data = Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, userId)
                .putString(KEY_ROOM_TOKEN, roomToken)
                .putInt(KEY_LAST_READ_MESSAGE, lastReadMessage)
                .build()

            val readMarkerWork = OneTimeWorkRequest.Builder(ReadMarkerSyncWorker::class.java)
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "read-marker-sync-$userId@$roomToken",
                ExistingWorkPolicy.REPLACE,
                readMarkerWork
            )
        }
    }
}
