/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.users

import com.nextcloud.talk.data.user.model.User
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Provides the default account, i.e. the last used one.
 *
 * Only for code without an account context of its own, like app entry points, share-to, the account switcher and the
 * phone book integration. Screens, workers and receivers must use the account they were started for.
 */
class DefaultAccountProvider @Inject constructor(private val userManager: UserManager) {

    suspend fun getDefaultUser(): User? = userManager.getDefaultUser()

    /**
     * Returns the default account without suspending. Only loads it from the database if it is not known yet.
     */
    fun getDefaultUserBlocking(): User? = userManager.defaultUserFlow.value ?: runBlocking { getDefaultUser() }
}
