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

        val userId = inputData.getLong(KEY_INTERNAL_USER_ID, -1)
        val roomToken = inputData.getString(KEY_ROOM_TOKEN)
        val internalConversationId = inputData.getString(KEY_INTERNAL_CONVERSATION_ID)
        val referenceId = inputData.getString(KEY_REFERENCE_ID)
        val message = inputData.getString(KEY_MESSAGE)
        val displayName = inputData.getString(KEY_DISPLAY_NAME).orEmpty()
        val replyTo = inputData.getInt(KEY_REPLY_TO, 0)
        val sendWithoutNotification = inputData.getBoolean(KEY_SEND_WITHOUT_NOTIFICATION, false)
        val threadTitle = inputData.getString(KEY_THREAD_TITLE)

        if (userId < 0 ||
            roomToken.isNullOrEmpty() ||
            internalConversationId.isNullOrEmpty() ||
            referenceId.isNullOrEmpty() ||
            message == null
        ) {
            Log.e(TAG, "Missing required input data, dropping message send")
            return Result.failure()
        }

        return sendMessage(
            userId,
            roomToken,
            internalConversationId,
            referenceId,
            message,
            displayName,
            replyTo,
            sendWithoutNotification,
            threadTitle
        )
    }

    @Suppress("LongParameterList", "Detekt.TooGenericExceptionCaught")
    private suspend fun sendMessage(
        userId: Long,
        roomToken: String,
        internalConversationId: String,
        referenceId: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        threadTitle: String?
    ): Result {
        val user = userManager.getUserWithId(userId).blockingGet()
        val credentials = user?.let { ApiUtils.getCredentials(it.username, it.token) }
        if (user == null || credentials == null) {
            Log.e(TAG, "No user or credentials found for user id $userId, failing message send")
            return failMessage(internalConversationId, referenceId)
        }

        val apiVersion = ApiUtils.getChatApiVersion(user.capabilities!!.spreedCapability!!, intArrayOf(ApiUtils.API_V1))
        val url = ApiUtils.getUrlForChat(apiVersion, user.baseUrl!!, roomToken)

        return try {
            chatNetworkDataSource.sendChatMessage(
                credentials,
                url,
                message,
                displayName,
                replyTo,
                sendWithoutNotification,
                referenceId,
                threadTitle
            )
            updateStatus(internalConversationId, referenceId, SendStatus.SENT_PENDING_ACK)
            Log.d(TAG, "sending chat message succeeded: $message")
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Network error while sending message (attempt ${runAttemptCount + 1}/$MAX_SEND_ATTEMPTS)", e)
            retryOrFail(internalConversationId, referenceId)
        } catch (e: Exception) {
            Log.e(TAG, "Something went wrong when sending message", e)
            failMessage(internalConversationId, referenceId)
        }
    }

    private suspend fun retryOrFail(internalConversationId: String, referenceId: String): Result =
        if (runAttemptCount < MAX_SEND_ATTEMPTS - 1) {
            Result.retry()
        } else {
            failMessage(internalConversationId, referenceId)
        }

    private suspend fun failMessage(internalConversationId: String, referenceId: String): Result {
        updateStatus(internalConversationId, referenceId, SendStatus.FAILED)
        return Result.failure()
    }

    private suspend fun updateStatus(internalConversationId: String, referenceId: String, status: SendStatus) {
        val entity = chatDao.getTempMessageForConversation(internalConversationId, referenceId, null).firstOrNull()
        entity?.let {
            it.sendStatus = status
            chatDao.updateChatMessage(it)
        }
    }

    companion object {
        private val TAG = SendMessageWorker::class.simpleName
        private const val KEY_INTERNAL_USER_ID = "INTERNAL_USER_ID"
        private const val KEY_ROOM_TOKEN = "ROOM_TOKEN"
        private const val KEY_INTERNAL_CONVERSATION_ID = "INTERNAL_CONVERSATION_ID"
        private const val KEY_REFERENCE_ID = "REFERENCE_ID"
        private const val KEY_MESSAGE = "MESSAGE"
        private const val KEY_DISPLAY_NAME = "DISPLAY_NAME"
        private const val KEY_REPLY_TO = "REPLY_TO"
        private const val KEY_SEND_WITHOUT_NOTIFICATION = "SEND_WITHOUT_NOTIFICATION"
        private const val KEY_THREAD_TITLE = "THREAD_TITLE"

        // Total attempts allowed for a single message (1 initial run + retries) before giving up on
        // a transient network failure and marking it FAILED so the user can resend manually.
        private const val MAX_SEND_ATTEMPTS = 4

        @Suppress("LongParameterList")
        fun enqueue(
            userId: Long,
            roomToken: String,
            internalConversationId: String,
            referenceId: String,
            message: String,
            displayName: String,
            replyTo: Int,
            sendWithoutNotification: Boolean,
            threadTitle: String?
        ) {
            val data = Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, userId)
                .putString(KEY_ROOM_TOKEN, roomToken)
                .putString(KEY_INTERNAL_CONVERSATION_ID, internalConversationId)
                .putString(KEY_REFERENCE_ID, referenceId)
                .putString(KEY_MESSAGE, message)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putInt(KEY_REPLY_TO, replyTo)
                .putBoolean(KEY_SEND_WITHOUT_NOTIFICATION, sendWithoutNotification)
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
