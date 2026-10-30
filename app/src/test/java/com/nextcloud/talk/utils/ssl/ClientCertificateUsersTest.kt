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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

class ClientCertificateUsersTest {

    private val usersFlow = MutableSharedFlow<List<User>>(replay = 1)
    private val userManager: UserManager = mock()
    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the users are queried until the database emitted them`() {
        val stored = listOf(User(id = 1, clientCertificate = "certA"))
        whenever(userManager.usersFlow()).thenReturn(usersFlow)
        wheneverBlocking { userManager.getUsers() }.thenReturn(stored)

        assertEquals(stored, ClientCertificateUsers(userManager, scope).getUsers())
    }

    @Test
    fun `the emitted users are used without querying the database`() {
        val emitted = listOf(User(id = 2, clientCertificate = "certB"))
        whenever(userManager.usersFlow()).thenReturn(usersFlow)
        val clientCertificateUsers = ClientCertificateUsers(userManager, scope)

        usersFlow.tryEmit(emitted)

        assertEquals(emitted, clientCertificateUsers.getUsers())
        verifyBlocking(userManager, never()) { getUsers() }
    }

    @Test
    fun `changes of the users replace the cached ones`() {
        whenever(userManager.usersFlow()).thenReturn(usersFlow)
        val clientCertificateUsers = ClientCertificateUsers(userManager, scope)

        usersFlow.tryEmit(listOf(User(id = 1, clientCertificate = "certA")))
        usersFlow.tryEmit(emptyList())

        assertEquals(emptyList<User>(), clientCertificateUsers.getUsers())
    }
}
