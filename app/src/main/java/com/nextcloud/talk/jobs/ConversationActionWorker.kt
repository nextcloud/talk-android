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
import com.nextcloud.talk.utils.isTransientFailure
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

    /** What to do to a conversation, together with the value it sets. */
    sealed interface ConversationAction {
        data class MarkRead(val lastReadMessage: Int?) : ConversationAction
        data object MarkUnread : ConversationAction
        data class Favorite(val favorite: Boolean) : ConversationAction
        data class Archive(val archived: Boolean) : ConversationAction
    }

    /** One change to send: [action] on the conversation [roomToken] of [userId]. */
    data class Change(val userId: Long, val roomToken: String, val action: ConversationAction) {
        val internalConversationId: String get() = "$userId@$roomToken"
    }

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)

        val change = changeOf(inputData)
        if (change == null) {
            Log.e(TAG, "Missing user id, room token or action, dropping the conversation action")
            return Result.failure()
        }

        return send(change)
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
        val failure = try {
            perform(user, credentials, change)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "${change.action} for room ${change.roomToken} could not be sent: $e")
            e
        }

        return when {
            failure == null -> {
                Log.d(TAG, "${change.action} sent for room ${change.roomToken}")
                // a read marker stays guarded until a sync shows it, like the one ReadMarkerSyncWorker sends
                if (change.action !is ConversationAction.MarkRead) {
                    releaseGuard(change)
                }
                Result.success()
            }

            // an answer the server gave will be the same next time, so only a connection problem, a rate
            // limit or a server error is worth another attempt
            isTransientFailure(failure) && runAttemptCount < MAX_RUN_ATTEMPTS - 1 -> Result.retry()

            else -> giveUp(change)
        }
    }

    private suspend fun perform(user: User, credentials: String, change: Change) {
        val roomToken = change.roomToken

        when (val action = change.action) {
            is ConversationAction.MarkRead ->
                conversationsRepository.markConversationAsRead(
                    credentials,
                    readMarkerUrl(user, roomToken),
                    action.lastReadMessage
                )

            ConversationAction.MarkUnread ->
                conversationsRepository.markConversationAsUnread(credentials, readMarkerUrl(user, roomToken))

            is ConversationAction.Favorite -> {
                val url = ApiUtils.getUrlForRoomFavorite(conversationApiVersion(user), user.baseUrl, roomToken)
                if (action.favorite) {
                    conversationsRepository.addConversationToFavorites(credentials, url)
                } else {
                    conversationsRepository.removeConversationFromFavorites(credentials, url)
                }
            }

            is ConversationAction.Archive -> {
                val url = ApiUtils.getUrlForArchive(conversationApiVersion(user), user.baseUrl, roomToken)
                if (action.archived) {
                    conversationsRepository.archiveConversation(credentials, url)
                } else {
                    conversationsRepository.unarchiveConversation(credentials, url)
                }
            }
        }
    }

    /**
     * Only favoriting and archiving speak the conversation api. Asking for its version up front would
     * fail marking read and unread on a server that has only the chat api, which is all they need.
     */
    private fun conversationApiVersion(user: User): Int =
        ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))

    private fun readMarkerUrl(user: User, roomToken: String): String {
        val chatApiVersion = ApiUtils.getChatApiVersion(
            user.capabilities!!.spreedCapability!!,
            intArrayOf(ApiUtils.API_V1)
        )
        return ApiUtils.getUrlForChatReadMarker(chatApiVersion, user.baseUrl, roomToken)
    }

    private fun releaseGuard(change: Change) {
        val internalConversationId = change.internalConversationId
        when (val action = change.action) {
            is ConversationAction.MarkRead -> action.lastReadMessage?.let {
                conversationListUpdater.clearPendingReadMarker(internalConversationId, it)
            }
            ConversationAction.MarkUnread -> conversationListUpdater.clearPendingUnread(internalConversationId)
            is ConversationAction.Favorite ->
                conversationListUpdater.clearPendingFavorite(internalConversationId, action.favorite)
            is ConversationAction.Archive ->
                conversationListUpdater.clearPendingArchived(internalConversationId, action.archived)
        }
    }

    private fun giveUp(change: Change): Result {
        releaseGuard(change)
        return Result.failure()
    }

    companion object {
        private val TAG: String = ConversationActionWorker::class.java.simpleName
        private const val MAX_RUN_ATTEMPTS = 3

        // the work input of enqueued work outlives an app update, so these must not change
        private const val KEY_ACTION = "KEY_ACTION"
        private const val KEY_ENABLED = "KEY_ENABLED"
        private const val KEY_LAST_READ_MESSAGE = "KEY_LAST_READ_MESSAGE"
        private const val ACTION_MARK_READ = "MARK_READ"
        private const val ACTION_MARK_UNREAD = "MARK_UNREAD"
        private const val ACTION_FAVORITE = "FAVORITE"
        private const val ACTION_ARCHIVE = "ARCHIVE"

        /**
         * Hands the change over and returns the id of the work that carries it, so a caller that is
         * still on screen can tell the user when it finally did not go through.
         */
        fun enqueue(context: Context, change: Change): UUID {
            val work = OneTimeWorkRequest.Builder(ConversationActionWorker::class.java)
                .setInputData(inputDataOf(change))
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
         * an older intent whatever value it sets, while a different action on it is carried separately -
         * except for read and unread, which supersede each other and the chat's read marker.
         */
        fun uniqueWorkName(userId: Long, roomToken: String, action: ConversationAction): String =
            when (action) {
                is ConversationAction.MarkRead,
                ConversationAction.MarkUnread -> ReadMarkerSyncWorker.uniqueWorkName(userId, roomToken)
                is ConversationAction.Favorite,
                is ConversationAction.Archive -> "conversation-action-${nameOf(action)}-$userId@$roomToken"
            }

        private fun nameOf(action: ConversationAction): String =
            when (action) {
                is ConversationAction.MarkRead -> ACTION_MARK_READ
                ConversationAction.MarkUnread -> ACTION_MARK_UNREAD
                is ConversationAction.Favorite -> ACTION_FAVORITE
                is ConversationAction.Archive -> ACTION_ARCHIVE
            }

        @VisibleForTesting
        internal fun inputDataOf(change: Change): Data =
            Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, change.userId)
                .putString(KEY_ROOM_TOKEN, change.roomToken)
                .putString(KEY_ACTION, nameOf(change.action))
                .apply {
                    when (val action = change.action) {
                        is ConversationAction.MarkRead ->
                            action.lastReadMessage?.let { putInt(KEY_LAST_READ_MESSAGE, it) }
                        ConversationAction.MarkUnread -> Unit
                        is ConversationAction.Favorite -> putBoolean(KEY_ENABLED, action.favorite)
                        is ConversationAction.Archive -> putBoolean(KEY_ENABLED, action.archived)
                    }
                }
                .build()

        /** The change enqueued work carries, or null when it is incomplete or carries an action we do not know. */
        @VisibleForTesting
        internal fun changeOf(data: Data): Change? {
            val userId = data.getLong(KEY_INTERNAL_USER_ID, -1)
            val roomToken = data.getString(KEY_ROOM_TOKEN)
            val action = when (data.getString(KEY_ACTION)) {
                ACTION_MARK_READ ->
                    ConversationAction.MarkRead(data.getInt(KEY_LAST_READ_MESSAGE, -1).takeIf { it >= 0 })
                ACTION_MARK_UNREAD -> ConversationAction.MarkUnread
                ACTION_FAVORITE -> ConversationAction.Favorite(data.getBoolean(KEY_ENABLED, true))
                ACTION_ARCHIVE -> ConversationAction.Archive(data.getBoolean(KEY_ENABLED, true))
                else -> null
            }

            return if (userId < 0 || roomToken.isNullOrEmpty() || action == null) {
                null
            } else {
                Change(userId, roomToken, action)
            }
        }
    }
}
