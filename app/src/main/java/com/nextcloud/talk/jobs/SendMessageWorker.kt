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
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Sends a chat text message to the server, retrying transient failures with backoff.
 *
 * Enqueued via WorkManager instead of running in a viewModelScope coroutine, so the send survives
 * leaving the conversation, the app going to background, or process death - unlike a plain
 * viewModelScope coroutine, which is cancelled the moment ChatActivity's ViewModel is cleared,
 * potentially leaving a message stuck as PENDING with nothing left running to retry it. Work is
 * chained per conversation with [ExistingWorkPolicy.APPEND_OR_REPLACE] (mirroring
 * [UploadAndShareFilesWorker]) so messages typed in a row still arrive in the order they were
 * sent, and a failed/cancelled message ahead in the queue doesn't block the ones behind it.
 */
@AutoInjector(NextcloudTalkApplication::class)
class SendMessageWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var chatNetworkDataSource: ChatNetworkDataSource

    @Inject
    lateinit var chatDao: ChatMessagesDao

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val internalConversationId = inputData.getString(KEY_INTERNAL_CONVERSATION_ID)
        val referenceId = inputData.getString(KEY_REFERENCE_ID)
        val threadTitle = inputData.getString(KEY_THREAD_TITLE)

        if (internalConversationId.isNullOrEmpty() || referenceId.isNullOrEmpty()) {
            Log.e(TAG, "Missing required input data, dropping message send")
            return Result.failure()
        }

        return sendMessage(internalConversationId, referenceId, threadTitle)
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun sendMessage(internalConversationId: String, referenceId: String, threadTitle: String?): Result {
        // Re-checked on every attempt (including retries): the user may have deleted this
        // temporary message - e.g. from the message actions sheet while offline - after it was
        // enqueued but before a connection was available to actually send it. Without this check
        // the message would still be posted to the server even though it no longer exists locally.
        // The message text and its other send parameters are also read fresh from this row rather
        // than passed in via WorkManager's input data, so an edit made to a still-queued message
        // while offline (editTempChatMessage only updates this row) is picked up instead of sending
        // stale data.
        val tempMessage = chatDao.getTempMessageForConversation(internalConversationId, referenceId, null)
            .firstOrNull()
        if (tempMessage == null) {
            Log.d(TAG, "Temporary message $referenceId no longer exists, skipping send")
            return Result.success()
        }

        val user = userManager.getUserWithId(tempMessage.accountId)
        val credentials = user?.let { ApiUtils.getCredentials(it.username, it.token) }
        if (user == null || credentials == null) {
            Log.e(TAG, "No user or credentials found for account id ${tempMessage.accountId}, failing message send")
            return failMessage(tempMessage)
        }

        val apiVersion = ApiUtils.getChatApiVersion(user.capabilities!!.spreedCapability!!, intArrayOf(ApiUtils.API_V1))
        val url = ApiUtils.getUrlForChat(apiVersion, user.baseUrl!!, tempMessage.token)

        return try {
            chatNetworkDataSource.sendChatMessage(
                credentials,
                url,
                tempMessage.message,
                tempMessage.actorDisplayName,
                tempMessage.parentMessageId?.toInt() ?: 0,
                tempMessage.silent,
                referenceId,
                threadTitle
            )
            updateStatus(tempMessage, SendStatus.SENT_PENDING_ACK)
            Log.d(TAG, "sending chat message succeeded: ${tempMessage.message}")
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Network error while sending message (attempt ${runAttemptCount + 1}/$MAX_SEND_ATTEMPTS)", e)
            retryOrFail(tempMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when sending message", e)
            failMessage(tempMessage)
        }
    }

    private fun retryOrFail(tempMessage: ChatMessageEntity): Result =
        if (runAttemptCount < MAX_SEND_ATTEMPTS - 1) {
            Result.retry()
        } else {
            failMessage(tempMessage)
        }

    private fun failMessage(tempMessage: ChatMessageEntity): Result {
        updateStatus(tempMessage, SendStatus.FAILED)
        return Result.failure()
    }

    private fun updateStatus(tempMessage: ChatMessageEntity, status: SendStatus) {
        tempMessage.sendStatus = status
        chatDao.updateChatMessage(tempMessage)
    }

    companion object {
        private val TAG = SendMessageWorker::class.simpleName
        private const val KEY_INTERNAL_CONVERSATION_ID = "INTERNAL_CONVERSATION_ID"
        private const val KEY_REFERENCE_ID = "REFERENCE_ID"
        private const val KEY_THREAD_TITLE = "THREAD_TITLE"

        // Total attempts allowed for a single message (1 initial run + retries) before giving up on
        // a transient network failure and marking it FAILED so the user can resend manually.
        private const val MAX_SEND_ATTEMPTS = 4

        // userId, roomToken, message text, displayName, replyTo and sendWithoutNotification are
        // deliberately not passed in here: they're all already persisted on the temporary message
        // row (accountId, token, actorDisplayName, parentMessageId, silent), which doWork() reads
        // fresh instead, so a later edit or deletion of that row is always reflected. threadTitle
        // isn't persisted on that row, so it still has to travel through the work request.
        fun enqueue(internalConversationId: String, referenceId: String, threadTitle: String?) {
            val data = Data.Builder()
                .putString(KEY_INTERNAL_CONVERSATION_ID, internalConversationId)
                .putString(KEY_REFERENCE_ID, referenceId)
                .putString(KEY_THREAD_TITLE, threadTitle)
                .build()

            val sendWork = OneTimeWorkRequest.Builder(SendMessageWorker::class.java)
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()

            // Chained per conversation (not enqueueUniqueWork(referenceId, ...), which would run every
            // message fully independently) so several messages typed in a row still arrive in the
            // order they were sent. APPEND_OR_REPLACE rather than APPEND: if the message ahead in the
            // queue permanently failed, this starts a fresh chain instead of cascading that failure
            // onto every message queued behind it.
            WorkManager.getInstance().enqueueUniqueWork(
                sendQueueName(internalConversationId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                sendWork
            )
        }

        private fun sendQueueName(internalConversationId: String) = "send_message_queue_$internalConversationId"
    }
}
