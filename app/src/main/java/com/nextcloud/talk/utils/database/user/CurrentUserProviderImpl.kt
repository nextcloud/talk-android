/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.database.user

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrentUserProviderImpl @Inject constructor(private val userManager: UserManager) : CurrentUserProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val currentUser: StateFlow<User?> = userManager.currentUserObservable
        .asFlow()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    // only emit non-null users
    val currentUserFlow: Flow<User> = currentUser.filterNotNull()

    // function for safe one-shot access
    override suspend fun getCurrentUser(timeout: Long): Result<User> {
        val user = withTimeoutOrNull(timeout) {
            currentUserFlow.first()
        }

        return if (user != null) {
            Result.success(user)
        } else {
            Result.failure(IllegalStateException("No current user available"))
        }
    }
}
