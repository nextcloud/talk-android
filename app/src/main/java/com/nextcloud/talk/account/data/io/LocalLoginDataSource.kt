/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data.io

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.OneTimeWorkRequest
import com.nextcloud.talk.utils.setExpeditedIfSupported
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

// local datasource for communicating with room through account manager
// crucial for making sure the login process interacts with the db as expected.
class LocalLoginDataSource(val userManager: UserManager, val appPreferences: AppPreferences, val context: Context) {

    companion object {
        private const val REMOVAL_TIMEOUT_MILLIS = 30_000L
        private const val REMOVAL_POLL_INTERVAL_MILLIS = 200L
    }

    /**
     * Stores the new credentials of a reauthorized account. That is the account that logged in, found by its login
     * name and server, as the browser login flow grants an app password for whoever is logged in there. Returns false
     * without any change if it is not the account with the internal id [accountToReauthorize], or if nothing was
     * stored, e.g. because the account was removed meanwhile.
     */
    suspend fun updateUser(loginData: LoginCompletion, accountToReauthorize: Long?): Boolean {
        val user = userManager.getUserWithUsernameAndServer(loginData.loginName, loginData.server)
            ?.takeIf { accountToReauthorize == null || it.id == accountToReauthorize }
            ?: return false
        return userManager.updateCredentials(
            user.id!!,
            loginData.appPassword,
            appPreferences.temporaryClientCertAlias
        ) > 0
    }

    fun startAccountRemovalWorker(): LiveData<WorkInfo?> {
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java)
            .setExpeditedIfSupported()
            .build()
        WorkManager.getInstance(context).enqueue(accountRemovalWork)

        return WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
    }

    /**
     * Waits until the account of [data] is not scheduled for deletion anymore, i.e. the account removal finished, for
     * at most [timeoutMillis]. Returns false if it still is. The removal also contacts the server and the push proxy,
     * so it can take a while.
     */
    suspend fun awaitUserRemoval(data: LoginCompletion, timeoutMillis: Long = REMOVAL_TIMEOUT_MILLIS): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            while (checkIfUserIsScheduledForDeletion(data)) {
                delay(REMOVAL_POLL_INTERVAL_MILLIS)
            }
            true
        } ?: false

    suspend fun checkIfUserIsScheduledForDeletion(data: LoginCompletion): Boolean =
        userManager.checkIfUserIsScheduledForDeletion(data.loginName, data.server)

    suspend fun checkIfUserExists(data: LoginCompletion): Boolean =
        userManager.checkIfUserExists(data.loginName, data.server)
}
