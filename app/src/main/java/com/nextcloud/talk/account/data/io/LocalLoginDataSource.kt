/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data.io

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nextcloud.talk.account.data.network.NetworkLoginDataSource
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.preferences.AppPreferences

// local datasource for communicating with room through account manager
// crucial for making sure the login process interacts with the db as expected.

//  TODO test for proper account management and deletion, using robolectric for Unit tests.
//   This is helpful to know if an account management bug from
//   from the serverside, clientside network or clientside storage or client business logic.
//   For example the occasional multiple account creation error
class LocalLoginDataSource(val userManager: UserManager, val appPreferences: AppPreferences, val context: Context) {

    fun updateUserAndRestartApp(loginData: NetworkLoginDataSource.LoginCompletion) {
        val currentUser = userManager.currentUser.blockingGet()
        if (currentUser != null) {
            currentUser.clientCertificate = appPreferences.temporaryClientCertAlias
            currentUser.token = loginData.appPassword
            userManager.updateOrCreateUser(currentUser)
        }
    }

    fun startAccountRemovalWorkerAndRestartApp(): LiveData<WorkInfo?> {
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        WorkManager.getInstance(context).enqueue(accountRemovalWork)

        return WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
    }

    fun checkIfUserIsScheduledForDeletion(data: NetworkLoginDataSource.LoginCompletion): Boolean =
        userManager.checkIfUserIsScheduledForDeletion(data.loginName, data.server).blockingGet()

    fun checkIfUserExists(data: NetworkLoginDataSource.LoginCompletion): Boolean =
        userManager.checkIfUserExists(data.loginName, data.server).blockingGet()
}
