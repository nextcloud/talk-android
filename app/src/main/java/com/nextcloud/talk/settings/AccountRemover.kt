/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings

import android.content.Context
import android.widget.Toast
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nextcloud.talk.R
import com.nextcloud.talk.dagger.modules.ApplicationScope
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.AppTasks
import com.nextcloud.talk.utils.setExpeditedIfSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Removes an account and restarts the app afterwards. Runs in the application scope, as the screen that started it is
 * closed as soon as the account is gone.
 */
class AccountRemover @Inject constructor(
    private val context: Context,
    private val userManager: UserManager,
    private val workManager: WorkManager,
    private val logger: Logger,
    @ApplicationScope private val appScope: CoroutineScope
) {
    fun remove(userId: Long, displayName: String) {
        appScope.launch {
            userManager.scheduleUserForDeletionWithId(userId)
            val request = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java)
                .setExpeditedIfSupported()
                .build()
            workManager.enqueue(request)
            val result = workManager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true }
            val message = if (result?.state == WorkInfo.State.SUCCEEDED) {
                context.getString(R.string.nc_deleted_user, displayName)
            } else {
                logger.e(TAG, "Failed to remove the account with id $userId")
                context.getString(R.string.nc_common_error_sorry)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                context.startActivity(AppTasks.createRestartIntent(context))
            }
        }
    }

    companion object {
        private val TAG = AccountRemover::class.java.simpleName
    }
}
