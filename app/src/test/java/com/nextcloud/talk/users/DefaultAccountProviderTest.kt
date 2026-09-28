/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.users

import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.model.User
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

class DefaultAccountProviderTest {

    private val usersRepository: UsersRepository = mock()
    private val userManager = UserManager(usersRepository)
    private val provider = DefaultAccountProvider(userManager)

    private val userA = User(id = 1, username = "userA", baseUrl = "https://example.com", current = true)

    @Before
    fun setUp() {
        wheneverBlocking { usersRepository.getActiveUserFlow() }.thenReturn(emptyFlow())
    }

    @Test
    fun `getDefaultUserBlocking loads the active user when none is known yet`() {
        wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(userA)

        assertEquals(userA, provider.getDefaultUserBlocking())
    }

    @Test
    fun `getDefaultUserBlocking returns the known default user without querying the database`() =
        runTest {
            wheneverBlocking { usersRepository.setUserAsActiveWithId(1L) }.thenReturn(true)
            wheneverBlocking { usersRepository.getUserWithId(1L) }.thenReturn(userA)
            userManager.setUserAsActive(userA)

            assertEquals(userA, provider.getDefaultUserBlocking())
            verifyBlocking(usersRepository, never()) { getActiveUser() }
        }

    @Test
    fun `getDefaultUserBlocking is null when there is no account`() {
        wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(null)
        wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(emptyList())

        assertNull(provider.getDefaultUserBlocking())
    }
}
