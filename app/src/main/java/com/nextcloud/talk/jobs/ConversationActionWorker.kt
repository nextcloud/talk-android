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
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Sends a conversation change the user made in the list - marking it unread, favoriting it,
 * archiving it - to the server, the way [ReadMarkerSyncWorker] sends the read marker.
 *
 * The caller writes the change to the local conversation entry first, so the list reacts to the tap,
 * and hands the request over here. That buys two things the caller cannot do on its own: the change
 * outlives the process, and a change made offline waits for a connection instead of being reverted
 * within a second of a tap the user meant.
 *
 * Every action here sets a value rather than moving one, so repeating it changes nothing and a retry
 * is always safe. Work is unique per conversation and action with [ExistingWorkPolicy.REPLACE], so
 * favoriting and unfavoriting in quick succession leaves only the last intent to be sent.
 *
 * When the attempts are used up, the pending guard is released and the server state applies again at
 * the next room list sync - the same fallback [ReadMarkerSyncWorker] uses, rather than a local revert
 * that would have to guess what the entry looked like before.
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
        MARK_UNREAD,
        FAVORITE,
        ARCHIVE
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

        return send(userId, roomToken, action, inputData.getBoolean(KEY_ENABLED, true))
    }

    private suspend fun send(userId: Long, roomToken: String, action: ConversationAction, enabled: Boolean): Result {
        val user = userManager.getUserWithId(userId)
        val credentials = user?.let { ApiUtils.getCredentials(it.username, it.token) }

        val sent = when {
            user == null || credentials == null -> {
                Log.e(TAG, "No user or credentials found for user id $userId, dropping the conversation action")
                false
            }

            else -> runCatching {
                perform(user, credentials, roomToken, action, enabled)
            }.onFailure { throwable ->
                Log.w(TAG, "$action for room $roomToken could not be sent: $throwable")
            }.isSuccess
        }

        return when {
            sent -> {
                Log.d(TAG, "$action sent for room $roomToken")
                releaseGuard(userId, roomToken, action, enabled)
                Result.success()
            }

            // a missing user is not worth another attempt, it will still be missing
            credentials != null && runAttemptCount < MAX_RUN_ATTEMPTS - 1 -> Result.retry()

            else -> giveUp(userId, roomToken, action, enabled)
        }
    }

    private suspend fun perform(
        user: User,
        credentials: String,
        roomToken: String,
        action: ConversationAction,
        enabled: Boolean
    ) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))

        when (action) {
            ConversationAction.MARK_UNREAD -> {
                val chatApiVersion = ApiUtils.getChatApiVersion(
                    user.capabilities!!.spreedCapability!!,
                    intArrayOf(ApiUtils.API_V1)
                )
                val url = ApiUtils.getUrlForChatReadMarker(chatApiVersion, user.baseUrl, roomToken)
                conversationsRepository.markConversationAsUnread(credentials, url)
            }

            ConversationAction.FAVORITE -> {
                val url = ApiUtils.getUrlForRoomFavorite(apiVersion, user.baseUrl, roomToken)
                if (enabled) {
                    conversationsRepository.addConversationToFavorites(credentials, url)
                } else {
                    conversationsRepository.removeConversationFromFavorites(credentials, url)
                }
            }

            ConversationAction.ARCHIVE -> {
                val url = ApiUtils.getUrlForArchive(apiVersion, user.baseUrl, roomToken)
                if (enabled) {
                    conversationsRepository.archiveConversation(credentials, url)
                } else {
                    conversationsRepository.unarchiveConversation(credentials, url)
                }
            }
        }
    }

    private fun releaseGuard(userId: Long, roomToken: String, action: ConversationAction, enabled: Boolean) {
        val internalConversationId = "$userId@$roomToken"
        when (action) {
            ConversationAction.MARK_UNREAD -> conversationListUpdater.clearPendingUnread(internalConversationId)
            ConversationAction.FAVORITE ->
                conversationListUpdater.clearPendingFavorite(internalConversationId, enabled)
            ConversationAction.ARCHIVE ->
                conversationListUpdater.clearPendingArchived(internalConversationId, enabled)
        }
    }

    private fun giveUp(userId: Long, roomToken: String, action: ConversationAction, enabled: Boolean): Result {
        releaseGuard(userId, roomToken, action, enabled)
        return Result.failure()
    }

    companion object {
        private val TAG: String = ConversationActionWorker::class.java.simpleName
        private const val KEY_ACTION = "KEY_ACTION"
        private const val KEY_ENABLED = "KEY_ENABLED"
        private const val MAX_RUN_ATTEMPTS = 3

        /**
         * Hands the change over and returns the id of the work that carries it, so a caller that is
         * still on screen can tell the user when it finally did not go through.
         */
        fun enqueue(
            context: Context,
            userId: Long,
            roomToken: String,
            action: ConversationAction,
            enabled: Boolean = true
        ): UUID {
            val data = Data.Builder()
                .putLong(KEY_INTERNAL_USER_ID, userId)
                .putString(KEY_ROOM_TOKEN, roomToken)
                .putString(KEY_ACTION, action.name)
                .putBoolean(KEY_ENABLED, enabled)
                .build()

            val work = OneTimeWorkRequest.Builder(ConversationActionWorker::class.java)
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(userId, roomToken, action),
                ExistingWorkPolicy.REPLACE,
                work
            )

            return work.id
        }

        /**
         * The name that decides what replaces what: the same action on the same conversation supersedes
         * an older intent, while a different action on it is carried separately.
         */
        fun uniqueWorkName(userId: Long, roomToken: String, action: ConversationAction): String =
            "conversation-action-${action.name}-$userId@$roomToken"

        /** The action an enqueued work item carries, or null when it carries nothing we know. */
        fun actionOf(name: String?): ConversationAction? = ConversationAction.entries.firstOrNull { it.name == name }
    }
}
