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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class ServerStatusRepositoryImpl @Inject constructor(
    private val ncApiCoroutines: NcApiCoroutines,
    private val currentUserProvider: CurrentUserProviderNew
) : ServerStatusRepository {
    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser

    private var _isServerReachable: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isServerReachable: StateFlow<Boolean>
        get() = _isServerReachable.asStateFlow()

    @Suppress("Detekt.TooGenericExceptionCaught")
    override suspend fun getServerStatus() {
        _currentUser.baseUrl?.let { baseUrl ->
            val url = baseUrl + ApiUtils.getUrlPostfixForStatus()
            _isServerReachable.value = try {
                ncApiCoroutines.getServerStatus(url)
                true
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                false
            }
        }
    }
}
