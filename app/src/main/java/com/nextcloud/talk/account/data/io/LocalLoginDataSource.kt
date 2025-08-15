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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.preferences.AppPreferences

// local datasource for communicating with room through account manager
// crucial for making sure the login process interacts with the db as expected.
class LocalLoginDataSource(val userManager: UserManager, val appPreferences: AppPreferences, val context: Context) {

    fun updateUser(loginData: LoginCompletion) {
        val currentUser = userManager.currentUser.blockingGet()
        if (currentUser != null) {
            currentUser.clientCertificate = appPreferences.temporaryClientCertAlias
            currentUser.token = loginData.appPassword
            userManager.updateOrCreateUser(currentUser)
        }
    }

    fun startAccountRemovalWorker(): LiveData<WorkInfo?> {
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        WorkManager.getInstance(context).enqueue(accountRemovalWork)

        return WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
    }

    fun checkIfUserIsScheduledForDeletion(data: LoginCompletion): Boolean =
        userManager.checkIfUserIsScheduledForDeletion(data.loginName, data.server).blockingGet()

    fun checkIfUserExists(data: LoginCompletion): Boolean =
        userManager.checkIfUserExists(data.loginName, data.server).blockingGet()
}
