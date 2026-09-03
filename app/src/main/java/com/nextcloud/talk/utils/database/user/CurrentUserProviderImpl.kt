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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrentUserProviderImpl @Inject constructor(private val userManager: UserManager) : CurrentUserProvider {

    // only emit non-null users
    override val currentUserFlow: Flow<User> = userManager.currentUserFlow.filterNotNull()

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
