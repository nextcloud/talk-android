/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
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
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import kotlinx.coroutines.CancellationException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Sends a change the user made in the conversation list - read, unread, favorite, archive - to the server.
 * It waits for a connection and retries, so the change survives being offline and the process being killed.
 *
 * Each action sets a value, so a retry is always safe. Work is unique per conversation and action, so a newer
 * tap replaces one that was not sent yet; read and unread share the read marker slot of [ReadMarkerSyncWorker],
 * so the last of them wins. When the attempts are used up, the guard is released and the next room list sync
 * brings the server state back.
 */
@AutoInjector(NextcloudTalkApplication::class)
class ConversationActionWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var conversationsRepository: ConversationsRepository

    @Inject
    lateinit var conversationListUpdater: ConversationListUpdater

    enum class ConversationAction {
        MARK_READ,
        MARK_UNREAD,
        FAVORITE,
        ARCHIVE
    }

    /**
     * One change to send: [action] on the conversation [roomToken] of [userId]. [enabled] is the value a
     * favorite or archive is set to, [lastReadMessage] the message marking read sets the read marker to.
     */
    data class Change(
        val userId: Long,
        val roomToken: String,
        val action: ConversationAction,
        val enabled: Boolean = true,
        val lastReadMessage: Int? = null
    ) {
        val internalConversationId: String get() = "$userId@$roomToken"
    }

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val userId = inputData.getLong(KEY_INTERNAL_USER_ID, -1)
        val roomToken = inputData.getString(KEY_ROOM_TOKEN)
        val action = actionOf(inputData.getString(KEY_ACTION))

        if (userId < 0 || roomToken.isNullOrEmpty() || action == null) {
            Log.e(TAG, "Missing user id, room token or action, dropping the conversation action")
            return Result.failure()
        }

        return send(
            Change(
                userId = userId,
                roomToken = roomToken,
                action = action,
                enabled = inputData.getBoolean(KEY_ENABLED, true),
                lastReadMessage = inputData.getInt(KEY_LAST_READ_MESSAGE, -1).takeIf { it >= 0 }
            )
        )
    }

    @VisibleForTesting
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun send(change: Change): Result {
        val user = userManager.getUserWithId(change.userId)
        val credentials = user?.let { ApiUtils.getCredentials(it.username, it.token) }
        if (user == null || credentials == null) {
            Log.e(TAG, "No user or credentials found for user id ${change.userId}, dropping the conversation action")
            // a missing user is not worth another attempt, it will still be missing
            return giveUp(change)
        }

        // not runCatching: it would swallow the CancellationException that WorkManager throws
        // when this work is replaced, and turn being superseded into a failure
        val sent = try {
            perform(user, credentials, change)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "${change.action} for room ${change.roomToken} could not be sent: $e")
            false
        }

        return when {
            sent -> {
                Log.d(TAG, "${change.action} sent for room ${change.roomToken}")
                // a read marker stays guarded until a sync shows it, like the one ReadMarkerSyncWorker sends
                if (change.action != ConversationAction.MARK_READ) {
                    releaseGuard(change)
                }
                Result.success()
            }

            runAttemptCount < MAX_RUN_ATTEMPTS - 1 -> Result.retry()

            else -> giveUp(change)
        }
    }

    private suspend fun perform(user: User, credentials: String, change: Change) {
        val roomToken = change.roomToken
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))

        when (change.action) {
            ConversationAction.MARK_READ ->
                conversationsRepository.markConversationAsRead(
                    credentials,
                    readMarkerUrl(user, roomToken),
                    change.lastReadMessage
                )

            ConversationAction.MARK_UNREAD ->
                conversationsRepository.markConversationAsUnread(credentials, readMarkerUrl(user, roomToken))

            ConversationAction.FAVORITE -> {
                val url = ApiUtils.getUrlForRoomFavorite(apiVersion, user.baseUrl, roomToken)
                if (change.enabled) {
                    conversationsRepository.addConversationToFavorites(credentials, url)
                } else {
                    conversationsRepository.removeConversationFromFavorites(credentials, url)
                }
            }

            ConversationAction.ARCHIVE -> {
                val url = ApiUtils.getUrlForArchive(apiVersion, user.baseUrl, roomToken)
                if (change.enabled) {
                    conversationsRepository.archiveConversation(credentials, url)
                } else {
                    conversationsRepository.unarchiveConversation(credentials, url)
                }
            }
        }
    }

    private fun readMarkerUrl(user: User, roomToken: String): String {
        val chatApiVersion = ApiUtils.getChatApiVersion(
            user.capabilities!!.spreedCapability!!,
            intArrayOf(ApiUtils.API_V1)
        )
        return ApiUtils.getUrlForChatReadMarker(chatApiVersion, user.baseUrl, roomToken)
    }

    private fun releaseGuard(change: Change) {
        val internalConversationId = change.internalConversationId
        when (change.action) {
            ConversationAction.MARK_READ -> change.lastReadMessage?.let {
                conversationListUpdater.clearPendingReadMarker(internalConversationId, it)
            }
            ConversationAction.MARK_UNREAD -> conversationListUpdater.clearPendingUnread(internalConversationId)
            ConversationAction.FAVORITE ->
                conversationListUpdater.clearPendingFavorite(internalConversationId, change.enabled)
            ConversationAction.ARCHIVE ->
                conversationListUpdater.clearPendingArchived(internalConversationId, change.enabled)
        }
    }

    private fun giveUp(change: Change): Result {
        releaseGuard(change)
        return Result.failure()
    }

    companion object {
        private val TAG: String = ConversationActionWorker::class.java.simpleName
        private const val KEY_ACTION = "KEY_ACTION"
        private const val KEY_ENABLED = "KEY_ENABLED"
        private const val KEY_LAST_READ_MESSAGE = "KEY_LAST_READ_MESSAGE"
        private const val MAX_RUN_ATTEMPTS = 3

        /**
         * Hands the change over and returns the id of the work that carries it, so a caller that is
         * still on screen can tell the user when it finally did not go through.
         */
        fun enqueue(context: Context, change: Change): UUID {
            val data = Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, change.userId)
                .putString(KEY_ROOM_TOKEN, change.roomToken)
                .putString(KEY_ACTION, change.action.name)
                .putBoolean(KEY_ENABLED, change.enabled)
                .apply { change.lastReadMessage?.let { putInt(KEY_LAST_READ_MESSAGE, it) } }
                .build()

            val work = OneTimeWorkRequest.Builder(ConversationActionWorker::class.java)
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(change.userId, change.roomToken, change.action),
                ExistingWorkPolicy.REPLACE,
                work
            )

            return work.id
        }

        /**
         * The name that decides what replaces what: the same action on the same conversation supersedes
         * an older intent, while a different action on it is carried separately - except for read and
         * unread, which supersede each other and the chat's read marker.
         */
        fun uniqueWorkName(userId: Long, roomToken: String, action: ConversationAction): String =
            when (action) {
                ConversationAction.MARK_READ,
                ConversationAction.MARK_UNREAD -> ReadMarkerSyncWorker.uniqueWorkName(userId, roomToken)
                else -> "conversation-action-${action.name}-$userId@$roomToken"
            }

        /** The action an enqueued work item carries, or null when it carries nothing we know. */
        fun actionOf(name: String?): ConversationAction? = ConversationAction.entries.firstOrNull { it.name == name }
    }
}
