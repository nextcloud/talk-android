/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.isPowerSaveMode
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Periodic worker that syncs the conversation list, and the messages that sync prefetches, for
 * every configured account.
 *
 * Accounts are synced one after another. The run is skipped in battery saver mode and while the app
 * is in the foreground, and a run in which any account failed is retried up to [MAX_RUN_ATTEMPTS]
 * times. Network availability is enforced by the [NetworkType.CONNECTED] constraint on the request
 * rather than checked here.
 */
@AutoInjector(NextcloudTalkApplication::class)
class ConversationsSyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var conversationsRepository: OfflineConversationsRepository

    override suspend fun doWork(): Result {
        sharedApplication!!.componentApplication.inject(this)
        return sync()
    }

    /** Runs the sync, or reports success without syncing when a guard stands the run down. */
    @VisibleForTesting
    internal suspend fun sync(): Result =
        when {
            applicationContext.isPowerSaveMode() -> {
                Log.d(TAG, "Battery saver is active, skipping the background conversation sync")
                Result.success()
            }

            isAppInForeground() -> {
                Log.d(TAG, "App is in the foreground, skipping the background conversation sync")
                Result.success()
            }

            else -> syncAccounts()
        }

    private suspend fun syncAccounts(): Result {
        val accounts = runCatching { userManager.getUsers() }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.e(TAG, "Could not read the accounts to sync", throwable)
            return retryOrFail()
        }

        if (accounts.isEmpty()) {
            Log.d(TAG, "No account to sync")
            return Result.success()
        }

        // WorkManager stops a run after ten minutes, so a fixed order would let slow accounts at
        // the front keep the ones behind them from ever being reached
        val longestWaitingFirst = accounts.sortedBy { conversationsRepository.lastFullSyncAt(it.id!!) ?: 0L }

        val failed = longestWaitingFirst.count { !syncAccount(it) }

        return if (failed == 0) {
            Result.success()
        } else {
            Log.w(TAG, "$failed of ${accounts.size} accounts did not sync (attempt ${runAttemptCount + 1})")
            retryOrFail()
        }
    }

    /**
     * Syncs a single account, returning whether it succeeded. Failures are logged; the run being
     * cancelled propagates.
     *
     * The sync is given at most [ACCOUNT_TIMEOUT_MINUTES] minutes. An account that cannot finish
     * would otherwise be able to spend the whole execution window WorkManager grants the run, and
     * since its stored timestamp never advances it would keep sorting first and keep the accounts
     * behind it from ever being reached.
     */
    private suspend fun syncAccount(user: User): Boolean =
        runCatching {
            withTimeout(TimeUnit.MINUTES.toMillis(ACCOUNT_TIMEOUT_MINUTES)) {
                conversationsRepository.syncRooms(user)
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException && throwable !is TimeoutCancellationException) throw throwable
            if (throwable is TimeoutCancellationException) {
                Log.w(TAG, "Background conversation sync for account ${user.id} ran out of its time budget")
            } else {
                Log.e(TAG, "Background conversation sync failed for account ${user.id}", throwable)
            }
            false
        }

    private fun retryOrFail(): Result = if (runAttemptCount < MAX_RUN_ATTEMPTS - 1) Result.retry() else Result.failure()

    private suspend fun isAppInForeground(): Boolean =
        withContext(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }

    companion object {
        private val TAG: String = ConversationsSyncWorker::class.java.simpleName
        private const val MAX_RUN_ATTEMPTS = 3
        private const val ACCOUNT_TIMEOUT_MINUTES = 2L
        private const val REPEAT_INTERVAL_MINUTES = 15L
        const val UNIQUE_WORK_NAME = "PeriodicConversationsSync"

        /**
         * Schedules the worker to run every [REPEAT_INTERVAL_MINUTES] minutes while a network is
         * available, leaving an already scheduled run in place.
         */
        fun schedule(context: Context) {
            val work = PeriodicWorkRequest.Builder(
                ConversationsSyncWorker::class.java,
                REPEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }
}
