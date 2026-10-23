/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.ssl

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The accounts [KeyManager] chooses client certificates from. They are kept up to date from the database, so TLS
 * handshakes don't have to query it.
 */
class ClientCertificateUsers @JvmOverloads constructor(
    private val userManager: UserManager,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    @Volatile
    private var users: List<User>? = null

    init {
        scope.launch {
            userManager.usersFlow().collect { users = it }
        }
    }

    /**
     * The users not scheduled for deletion. Until the database emitted them for the first time, they are queried
     * directly.
     */
    fun getUsers(): List<User> = users ?: runBlocking { userManager.getUsers() }
}
