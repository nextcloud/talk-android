/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user

import com.nextcloud.talk.data.user.model.UserEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

class UsersRepositoryImplTest {

    private val usersDao: UsersDao = mock()
    private val repository = UsersRepositoryImpl(usersDao)

    @Test
    fun `getActiveUser only reads the database`() =
        runTest {
            val entity = UserEntity(id = 1L, username = "userA", baseUrl = "https://example.com", current = true)
            wheneverBlocking { usersDao.getActiveUser() }.thenReturn(entity)

            val result = repository.getActiveUser()

            assertEquals(1L, result?.id)
            verifyBlocking(usersDao, never()) { setUserAsActiveWithId(any()) }
        }
}
