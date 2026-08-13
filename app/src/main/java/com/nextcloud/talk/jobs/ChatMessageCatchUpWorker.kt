/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <andy.scherzinger@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.network.ChatMessageSyncer
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_THREAD_ID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Prefetches a pushed room's messages into the local database so they are instantly visible when
 * the chat is opened from the notification (or later). Enqueued by [NotificationWorker] after the
 * notification is displayed, so the fetch never delays or suppresses the notification and
 * transient failures are retried with backoff instead of being lost with the notification worker.
 *
 * Push bursts for the same room enqueue one worker each, but the per-room coalescing of the
 * [ChatMessageSyncer] singleton collapses overlapping catch-ups into few actual fetches. Skipped
 * in battery saver mode; the chat-keep-notifications capability gate and the offline check are
 * handled inside [ChatMessageSyncer.catchUpRoom].
 */
@AutoInjector(NextcloudTalkApplication::class)
class ChatMessageCatchUpWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var chatMessageSyncer: ChatMessageSyncer

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val userId = inputData.getLong(KEY_INTERNAL_USER_ID, -1)
        val roomToken = inputData.getString(KEY_ROOM_TOKEN)
        val threadId = inputData.getLong(KEY_THREAD_ID, NO_THREAD).takeIf { it != NO_THREAD }

        return when {
            userId < 0 || roomToken.isNullOrEmpty() -> {
                Log.e(TAG, "Missing user id or room token, dropping message catch-up")
                Result.failure()
            }

            isPowerSaveMode() -> {
                Log.d(TAG, "Battery saver is active, skipping message catch-up for room $roomToken")
                Result.success()
            }

            else -> catchUpRoom(userId, roomToken, threadId)
        }
    }

    private suspend fun catchUpRoom(userId: Long, roomToken: String, threadId: Long?): Result {
        val user = userManager.getUserWithId(userId).blockingGet()
        val credentials = user?.let { ApiUtils.getCredentials(it.username, it.token) }
        if (user == null || credentials == null) {
            Log.e(TAG, "No user or credentials found for user id $userId, dropping message catch-up")
            return Result.failure()
        }

        val target = ChatMessageSyncer.SyncTarget(
            user = user,
            roomToken = roomToken,
            threadId = threadId,
            credentials = credentials,
            urlForChatting = ApiUtils.getUrlForChat(CHAT_API_VERSION, user.baseUrl!!, roomToken)
        )

        val outcome = runCatching { chatMessageSyncer.catchUpRoom(target) }.getOrElse { throwable ->
            Log.e(TAG, "Message catch-up failed for room $roomToken", throwable)
            null
        }

        return if (outcome == null || outcome.syncFailed) {
            Log.w(TAG, "Message catch-up for room $roomToken did not complete (attempt ${runAttemptCount + 1})")
            retryOrFail()
        } else {
            Result.success()
        }
    }

    private fun retryOrFail(): Result = if (runAttemptCount < MAX_RUN_ATTEMPTS - 1) Result.retry() else Result.failure()

    private fun isPowerSaveMode(): Boolean {
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }

    companion object {
        private val TAG: String = ChatMessageCatchUpWorker::class.java.simpleName
        private const val CHAT_API_VERSION = 1
        private const val NO_THREAD = -1L
        private const val MAX_RUN_ATTEMPTS = 3

        fun enqueue(context: Context, userId: Long, roomToken: String, threadId: Long?) {
            val data = Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, userId)
                .putString(KEY_ROOM_TOKEN, roomToken)
                .apply { threadId?.let { putLong(KEY_THREAD_ID, it) } }
                .build()

            val catchUpWork = OneTimeWorkRequest.Builder(ChatMessageCatchUpWorker::class.java)
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(catchUpWork)
        }
    }
}
