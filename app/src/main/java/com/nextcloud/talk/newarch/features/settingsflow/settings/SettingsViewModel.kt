/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.nextcloud.talk.newarch.features.settingsflow.settings

import android.app.Application
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import com.nextcloud.talk.newarch.mvvm.BaseViewModel
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.utils.NetworkComponents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SettingsViewModel constructor(
        application: Application,
        private val usersRepository: UsersRepository,
        val networkComponents: NetworkComponents,
        private val apiErrorHandler: ApiErrorHandler,
        private val globalService: GlobalService
) : BaseViewModel<SettingsView>(application) {
    val users = usersRepository.getUsersLiveData()
    val activeUser = globalService.currentUserLiveData

    private suspend fun setUserAsActiveWithId(id: Long): Boolean {
        return usersRepository.setUserAsActiveWithId(id)
    }

    fun setUserAsActive(user: User): Boolean = runBlocking {
        var operationFinished = false
        if (user.status == UserStatus.DORMANT) {
            operationFinished = withContext(Dispatchers.Default) {
                runBlocking { setUserAsActive(user) }
            }
        }

        operationFinished
    }

    private suspend fun removeUserWithId(id: Long): Boolean {
        return usersRepository.markUserForDeletion(id)
    }

    fun removeUser(user: User): Boolean = runBlocking {
        var weHaveActiveUser = true
        if (user.status != UserStatus.PENDING_DELETE) {
            val userId = user.id
            if (userId != null) {
                weHaveActiveUser = withContext(Dispatchers.Default) {
                    runBlocking {
                        removeUserWithId(userId)
                    }
                }
            }
        }

        weHaveActiveUser
    }

}