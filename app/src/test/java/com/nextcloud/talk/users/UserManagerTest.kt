/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.users

import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.model.User
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.wheneverBlocking

@Suppress("DEPRECATION")
class UserManagerTest {

    private val usersRepository: UsersRepository = mock()
    private val userManager = UserManager(usersRepository)

    private fun user(id: Long, username: String, baseUrl: String, current: Boolean = false) =
        User(id = id, username = username, baseUrl = baseUrl, current = current)

    @Before
    fun setUp() {
        // No row resolves as "the" active user unless a test overrides this, so
        // scheduleDuplicateAccountsForDeletion() falls back to the `current` flag / oldest row,
        // matching the behavior asserted by the tests below that don't care about this priority.
        wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(null)
    }

    @Test
    fun `keeps the current user among duplicates and schedules the rest for deletion`() =
        runTest {
            val current = user(id = 2, username = "userA", baseUrl = "https://example.com", current = true)
            val duplicate = user(id = 1, username = "userA", baseUrl = "https://example.com", current = false)
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(listOf(current, duplicate))

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(1, scheduledCount)
            assertTrue(duplicate.scheduledForDeletion)
            assertFalse(current.scheduledForDeletion)
            verify(usersRepository).updateUser(duplicate)
        }

    @Test
    fun `keeps the oldest row when none of the duplicates is current`() =
        runTest {
            val oldest = user(id = 1, username = "userA", baseUrl = "https://example.com")
            val newer = user(id = 2, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(listOf(newer, oldest))

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(1, scheduledCount)
            assertTrue(newer.scheduledForDeletion)
            assertFalse(oldest.scheduledForDeletion)
        }

    @Test
    fun `does nothing when there are no duplicates`() =
        runTest {
            val userA = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
            val userB = user(id = 2, username = "userB", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(listOf(userA, userB))

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(0, scheduledCount)
            assertFalse(userA.scheduledForDeletion)
            assertFalse(userB.scheduledForDeletion)
        }

    @Test
    fun `different servers with the same username are not treated as duplicates`() =
        runTest {
            val userA = user(id = 1, username = "userA", baseUrl = "https://example.com")
            val userB = user(id = 2, username = "userA", baseUrl = "https://other.example.com")
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(listOf(userA, userB))

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(0, scheduledCount)
        }

    @Test
    fun `rows with a null or blank username or baseUrl are never grouped as duplicates`() =
        runTest {
            val nullUsername = user(id = 1, username = "userA", baseUrl = "https://example.com")
                .apply { username = null }
            val anotherNullUsername = user(id = 2, username = "userA", baseUrl = "https://example.com")
                .apply { username = null }
            val blankBaseUrl = user(id = 3, username = "userA", baseUrl = "")
            val anotherBlankBaseUrl = user(id = 4, username = "userA", baseUrl = "")
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(
                listOf(nullUsername, anotherNullUsername, blankBaseUrl, anotherBlankBaseUrl)
            )

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(0, scheduledCount)
        }

    @Test
    fun `keeps only one row out of three or more duplicates`() =
        runTest {
            val current = user(id = 3, username = "userA", baseUrl = "https://example.com", current = true)
            val duplicate1 = user(id = 1, username = "userA", baseUrl = "https://example.com")
            val duplicate2 = user(id = 2, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(listOf(duplicate1, duplicate2, current))

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(2, scheduledCount)
            assertTrue(duplicate1.scheduledForDeletion)
            assertTrue(duplicate2.scheduledForDeletion)
            assertFalse(current.scheduledForDeletion)
        }

    @Test
    fun `handles multiple independent duplicate groups in one pass`() =
        runTest {
            val userACurrent = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
            val userADuplicate = user(id = 2, username = "userA", baseUrl = "https://example.com")
            val userBOldest = user(id = 3, username = "userB", baseUrl = "https://example.com")
            val userBNewer = user(id = 4, username = "userB", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(
                listOf(userACurrent, userADuplicate, userBNewer, userBOldest)
            )

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(2, scheduledCount)
            assertTrue(userADuplicate.scheduledForDeletion)
            assertTrue(userBNewer.scheduledForDeletion)
            assertFalse(userACurrent.scheduledForDeletion)
            assertFalse(userBOldest.scheduledForDeletion)
        }

    @Test
    fun `does nothing when there are no users at all`() =
        runTest {
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(emptyList())

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(0, scheduledCount)
        }

    @Test
    fun `keeps whichever row getActiveUser resolves to, even over a different row flagged current`() =
        runTest {
            // Simulates a past bug leaving two rows marked current=true for the same account: the
            // active-user lookup (deterministically) resolves to one of them, but the other still
            // carries the current flag too. The actively-resolved row must win, since it may be the
            // one a live session/background sync is still bound to.
            val staleCurrentFlag = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
            val actuallyActive = user(id = 2, username = "userA", baseUrl = "https://example.com", current = true)
            wheneverBlocking { usersRepository.getUsers() }.thenReturn(listOf(staleCurrentFlag, actuallyActive))
            wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(actuallyActive)

            val scheduledCount = userManager.scheduleDuplicateAccountsForDeletionSuspend()

            assertEquals(1, scheduledCount)
            assertTrue(staleCurrentFlag.scheduledForDeletion)
            assertFalse(actuallyActive.scheduledForDeletion)
            verify(usersRepository).updateUser(staleCurrentFlag)
        }

    @Test
    fun `old RxJava-typed bridge still delegates to the suspend implementation`() {
        val current = user(id = 2, username = "userA", baseUrl = "https://example.com", current = true)
        val duplicate = user(id = 1, username = "userA", baseUrl = "https://example.com", current = false)
        wheneverBlocking { usersRepository.getUsers() }.thenReturn(listOf(current, duplicate))

        val scheduledCount = userManager.scheduleDuplicateAccountsForDeletion().blockingGet()

        assertEquals(1, scheduledCount)
        assertTrue(duplicate.scheduledForDeletion)
    }
}
