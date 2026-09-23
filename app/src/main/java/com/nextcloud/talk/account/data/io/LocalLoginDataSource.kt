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
import kotlinx.coroutines.runBlocking

// local datasource for communicating with room through account manager
// crucial for making sure the login process interacts with the db as expected.
class LocalLoginDataSource(val userManager: UserManager, val appPreferences: AppPreferences, val context: Context) {

    fun updateUser(loginData: LoginCompletion) {
        val currentUser = runBlocking { userManager.getCurrentUser() }
        if (currentUser != null) {
            currentUser.clientCertificate = appPreferences.temporaryClientCertAlias
            currentUser.token = loginData.appPassword
            runBlocking { userManager.updateOrCreateUser(currentUser) }
        }
    }

    fun startAccountRemovalWorker(): LiveData<WorkInfo?> {
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java)
            .setExpeditedIfSupported()
            .build()
        WorkManager.getInstance(context).enqueue(accountRemovalWork)

        return WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
    }

    suspend fun checkIfUserIsScheduledForDeletion(data: LoginCompletion): Boolean =
        userManager.checkIfUserIsScheduledForDeletion(data.loginName, data.server)

    suspend fun checkIfUserExists(data: LoginCompletion): Boolean =
        userManager.checkIfUserExists(data.loginName, data.server)
}
