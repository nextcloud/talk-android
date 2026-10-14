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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
 * sent.
 *
 * A message that cannot be sent is recorded as [SendStatus.FAILED] on its own row and the work
 * still finishes with [Result.success]. Reporting [Result.failure] would make WorkManager fail
 * every work request already appended behind it: those never run, so their rows would stay
 * [SendStatus.PENDING] with nothing left to send them.
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
        // Invariant: dependencies that are already set (a test worker factory) are not overwritten.
        if (!::chatDao.isInitialized) {
            sharedApplication!!.componentApplication.inject(this)
        }

        val internalConversationId = inputData.getString(KEY_INTERNAL_CONVERSATION_ID)
        val referenceId = inputData.getString(KEY_REFERENCE_ID)
        val threadTitle = inputData.getString(KEY_THREAD_TITLE)

        if (internalConversationId.isNullOrEmpty() || referenceId.isNullOrEmpty()) {
            // Not Result.failure(): that would also fail the messages queued behind this one.
            Log.e(TAG, "Missing required input data, dropping message send")
            return Result.success()
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
        val tempMessage = try {
            chatDao.getTempMessageForConversation(internalConversationId, referenceId, null).firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not read temporary message $referenceId", e)
            // The row is unreadable, so it cannot be marked FAILED here. Retry; on the last attempt
            // finish without failing, so the messages behind this one are still sent.
            return if (runAttemptCount < MAX_SEND_ATTEMPTS - 1) Result.retry() else Result.success()
        }
        return if (tempMessage == null) {
            Log.d(TAG, "Temporary message $referenceId no longer exists, skipping send")
            Result.success()
        } else {
            post(tempMessage, referenceId, threadTitle)
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun post(tempMessage: ChatMessageEntity, referenceId: String, threadTitle: String?): Result =
        try {
            val user = userManager.getUserWithId(tempMessage.accountId)
            val credentials = user?.let { ApiUtils.getCredentials(it.username, it.token) }
            if (user == null || credentials == null) {
                Log.e(TAG, "No user or credentials for account id ${tempMessage.accountId}, failing message send")
                failMessage(tempMessage)
            } else {
                val apiVersion =
                    ApiUtils.getChatApiVersion(user.capabilities!!.spreedCapability!!, intArrayOf(ApiUtils.API_V1))
                val url = ApiUtils.getUrlForChat(apiVersion, user.baseUrl!!, tempMessage.token)

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
            }
        } catch (e: CancellationException) {
            // The run was stopped (lost constraint, time limit, ...) and WorkManager reschedules it,
            // so the message is not failed; its row stays PENDING while the work request is alive.
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Network error while sending message (attempt ${runAttemptCount + 1}/$MAX_SEND_ATTEMPTS)", e)
            retryOrFail(tempMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when sending message", e)
            failMessage(tempMessage)
        }

    private fun retryOrFail(tempMessage: ChatMessageEntity): Result =
        if (runAttemptCount < MAX_SEND_ATTEMPTS - 1) {
            Result.retry()
        } else {
            failMessage(tempMessage)
        }

    // Result.success(), not failure(): the failure is recorded on the row, and a failed work request
    // would cascade to every request already queued behind it (see class doc).
    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun failMessage(tempMessage: ChatMessageEntity): Result {
        try {
            updateStatus(tempMessage, SendStatus.FAILED)
        } catch (e: Exception) {
            // Still success: a failure here would fail the messages queued behind this one as well.
            Log.e(TAG, "Could not mark message ${tempMessage.referenceId} as failed", e)
        }
        return Result.success()
    }

    // Only the status is written: the row may be edited concurrently (editTempChatMessage).
    private fun updateStatus(tempMessage: ChatMessageEntity, status: SendStatus) {
        chatDao.updateTempMessageSendStatus(
            tempMessage.internalConversationId,
            tempMessage.referenceId.orEmpty(),
            status
        )
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
            // order they were sent. APPEND_OR_REPLACE rather than APPEND: if the chain already ended
            // in a failed or cancelled request, this starts a
            // fresh chain. It does not protect requests that are already appended behind a request
            // that fails later; that is why doWork() never reports Result.failure().
            WorkManager.getInstance().enqueueUniqueWork(
                sendQueueName(internalConversationId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                sendWork
            )
        }

        /**
         * Whether a send of the conversation's queue is still waiting, running or blocked. A PENDING
         * row without such work will never be sent.
         */
        suspend fun hasActiveSend(workManager: WorkManager, internalConversationId: String): Boolean =
            workManager.getWorkInfosForUniqueWorkFlow(sendQueueName(internalConversationId)).first()
                .any { !it.state.isFinished }

        private fun sendQueueName(internalConversationId: String) = "send_message_queue_$internalConversationId"
    }
}
