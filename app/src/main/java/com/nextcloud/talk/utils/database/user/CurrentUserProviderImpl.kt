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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrentUserProviderImpl @Inject constructor(private val userManager: UserManager) : CurrentUserProviderNew {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentUser: StateFlow<User?> = userManager.currentUserObservable
        .asFlow()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    // only emit non-null users
    val currentUserFlow: Flow<User> = _currentUser.filterNotNull()

    // function for safe one-shot access
    override suspend fun getCurrentUser(): User {
        // suspends until a non-null user is available
        return currentUserFlow.first()
    }
}
