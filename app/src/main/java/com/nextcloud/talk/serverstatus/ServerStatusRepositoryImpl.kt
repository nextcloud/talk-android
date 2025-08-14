/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.serverstatus

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew

class ServerStatusRepositoryImpl (private val ncApiCoroutines: NcApiCoroutines,
    private val currentUserProvider: CurrentUserProviderNew) : ServerStatusRepository {
    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser

    private var _isServerReachable: Boolean? = null
    override val isServerReachable: Boolean?
        get() = _isServerReachable

    override suspend fun getServerStatus(){
        val baseUrl = currentUser.baseUrl
        val url = baseUrl + ApiUtils.getUrlPostfixForStatus()
        _isServerReachable = try {
            ncApiCoroutines.getServerStatus(url)
            true
        } catch (e: Exception) {
            false
        }
    }

}
