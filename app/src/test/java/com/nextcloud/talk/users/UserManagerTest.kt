/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.users

import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.ExternalSignalingServer
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
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
        // activeUserSubject/activeUserStateFlow lazily collect this on init; an empty flow lets
        // that background collection finish without racing the synchronous updates asserted below.
        wheneverBlocking { usersRepository.getActiveUserFlow() }.thenReturn(emptyFlow())
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

    @Test
    fun `currentUser returns the active user without touching any fallback`() {
        val active = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
        wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(active)

        val result = userManager.currentUser.blockingGet()

        assertEquals(active, result)
        verifyBlocking(usersRepository, never()) { getUsersNotScheduledForDeletion() }
    }

    @Test
    fun `currentUser falls back to any non-deleted user and sets it active when none is active`() {
        val fallback = user(id = 1, username = "userA", baseUrl = "https://example.com")
        wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(listOf(fallback))
        wheneverBlocking { usersRepository.setUserAsActiveWithId(fallback.id!!) }.thenReturn(true)
        // getActiveUser() is re-queried after setUserAsActiveWithId() succeeds, simulating the DB
        // now reporting the freshly-activated row.
        wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(null, fallback)

        val result = userManager.currentUser.blockingGet()

        assertEquals(fallback, result)
        verifyBlocking(usersRepository) { setUserAsActiveWithId(fallback.id!!) }
    }

    @Test
    fun `currentUser is empty when there is no active user and none to fall back to`() {
        wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(emptyList())

        assertTrue(userManager.currentUser.isEmpty.blockingGet())
    }

    @Test
    fun `deleteUserSuspend does nothing and returns 0 when the user does not exist`() =
        runTest {
            wheneverBlocking { usersRepository.getUserWithId(42L) }.thenReturn(null)

            val result = userManager.deleteUserSuspend(42L)

            assertEquals(0, result)
            verify(usersRepository, never()).deleteUser(any())
        }

    @Test
    fun `deleteUserSuspend deletes the user when it exists`() =
        runTest {
            val existing = user(id = 42, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUserWithId(42L) }.thenReturn(existing)
            wheneverBlocking { usersRepository.deleteUser(existing) }.thenReturn(1)

            val result = userManager.deleteUserSuspend(42L)

            assertEquals(1, result)
            verify(usersRepository).deleteUser(existing)
        }

    @Test
    fun `checkIfUserIsScheduledForDeletionSuspend reflects the matching user's flag`() =
        runTest {
            val scheduled = user(id = 1, username = "userA", baseUrl = "https://example.com")
                .apply { scheduledForDeletion = true }
            wheneverBlocking {
                usersRepository.getUserWithUsernameAndServer("userA", "https://example.com")
            }.thenReturn(scheduled)

            assertTrue(userManager.checkIfUserIsScheduledForDeletionSuspend("userA", "https://example.com"))
        }

    @Test
    fun `checkIfUserIsScheduledForDeletionSuspend is false when the user does not exist`() =
        runTest {
            wheneverBlocking {
                usersRepository.getUserWithUsernameAndServer("userA", "https://example.com")
            }.thenReturn(null)

            assertFalse(userManager.checkIfUserIsScheduledForDeletionSuspend("userA", "https://example.com"))
        }

    @Test
    fun `checkIfUserExistsSuspend is true only when a matching user is found`() =
        runTest {
            wheneverBlocking {
                usersRepository.getUserWithUsernameAndServer("userA", "https://example.com")
            }.thenReturn(user(id = 1, username = "userA", baseUrl = "https://example.com"))
            wheneverBlocking {
                usersRepository.getUserWithUsernameAndServer("userB", "https://example.com")
            }.thenReturn(null)

            assertTrue(userManager.checkIfUserExistsSuspend("userA", "https://example.com"))
            assertFalse(userManager.checkIfUserExistsSuspend("userB", "https://example.com"))
        }

    @Test
    fun `scheduleUserForDeletionWithIdSuspend returns false when the user does not exist`() =
        runTest {
            wheneverBlocking { usersRepository.getUserWithId(99L) }.thenReturn(null)

            assertFalse(userManager.scheduleUserForDeletionWithIdSuspend(99L))
            verify(usersRepository, never()).updateUser(any())
        }

    @Test
    fun `scheduleUserForDeletionWithIdSuspend marks the user deleted and returns false with nobody left to activate`() =
        runTest {
            val target = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
            wheneverBlocking { usersRepository.getUserWithId(1L) }.thenReturn(target)
            wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(emptyList())

            val result = userManager.scheduleUserForDeletionWithIdSuspend(1L)

            assertFalse(result)
            assertTrue(target.scheduledForDeletion)
            assertFalse(target.current)
            verify(usersRepository).updateUser(target)
        }

    @Test
    fun `scheduleUserForDeletionWithIdSuspend returns true and activates another user when one remains`() =
        runTest {
            val target = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
            val other = user(id = 2, username = "userB", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUserWithId(1L) }.thenReturn(target)
            wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(listOf(other))
            wheneverBlocking { usersRepository.setUserAsActiveWithId(other.id!!) }.thenReturn(true)
            wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(other)

            val result = userManager.scheduleUserForDeletionWithIdSuspend(1L)

            assertTrue(result)
            assertTrue(target.scheduledForDeletion)
            verify(usersRepository).setUserAsActiveWithId(other.id!!)
        }

    @Test
    fun `updateExternalSignalingServerSuspend throws when the user does not exist`() =
        runTest {
            wheneverBlocking { usersRepository.getUserWithId(7L) }.thenReturn(null)

            try {
                userManager.updateExternalSignalingServerSuspend(7L, ExternalSignalingServer())
                fail("Expected NoSuchElementException")
            } catch (expected: NoSuchElementException) {
                // expected
            }
        }

    @Test
    fun `updateExternalSignalingServerSuspend updates the matching user`() =
        runTest {
            val existing = user(id = 7, username = "userA", baseUrl = "https://example.com")
            val server = ExternalSignalingServer(externalSignalingServer = "https://signaling.example.com")
            wheneverBlocking { usersRepository.getUserWithId(7L) }.thenReturn(existing)
            wheneverBlocking { usersRepository.updateUser(existing) }.thenReturn(1)

            val result = userManager.updateExternalSignalingServerSuspend(7L, server)

            assertEquals(1, result)
            assertEquals(server, existing.externalSignalingServer)
        }

    @Test
    fun `updateOrCreateUserSuspend inserts a user without an id`() =
        runTest {
            val newUser = User(id = null, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.insertUser(newUser) }.thenReturn(5L)

            val result = userManager.updateOrCreateUserSuspend(newUser)

            assertEquals(5, result)
            verify(usersRepository, never()).updateUser(any())
        }

    @Test
    fun `updateOrCreateUserSuspend updates a user that already has an id`() =
        runTest {
            val existing = user(id = 3, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.updateUser(existing) }.thenReturn(1)

            val result = userManager.updateOrCreateUserSuspend(existing)

            assertEquals(1, result)
            verify(usersRepository, never()).insertUser(any())
        }

    @Test
    fun `setUserAsActiveSuspend publishes the new user on currentUserFlow only when it succeeds`() =
        runTest {
            val target = user(id = 1, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.setUserAsActiveWithId(1L) }.thenReturn(true)

            val result = userManager.setUserAsActiveSuspend(target)

            assertTrue(result)
            assertEquals(target, userManager.currentUserFlow.value)
        }

    @Test
    fun `setUserAsActiveSuspend leaves currentUserFlow untouched when it fails`() =
        runTest {
            val target = user(id = 1, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.setUserAsActiveWithId(1L) }.thenReturn(false)

            val result = userManager.setUserAsActiveSuspend(target)

            assertFalse(result)
            assertNull(userManager.currentUserFlow.value)
        }

    @Test
    fun `storeProfileSuspend creates a new user when the attributes carry no id`() =
        runTest {
            val attributes = UserManager.UserAttributes(
                id = null,
                serverUrl = "https://example.com",
                currentUser = true,
                userId = "userId",
                token = "token",
                displayName = "Display Name",
                pushConfigurationState = null,
                // createUser() guards these with TextUtils.isEmpty(), which this project's unit
                // tests stub to always return false (testOptions.unitTests.isReturnDefaultValues),
                // so a null value here would still hit LoganSquare.parse(null, ...) and NPE.
                capabilities = "{}",
                serverVersion = "{}",
                certificateAlias = null,
                externalSignalingServer = "{}"
            )
            val stored = user(id = 10, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.insertUser(any()) }.thenReturn(10L)
            wheneverBlocking { usersRepository.getUserWithId(10L) }.thenReturn(stored)

            val result = userManager.storeProfileSuspend("userA", attributes)

            assertEquals(stored, result)
            verify(usersRepository).insertUser(
                check {
                    assertEquals("userA", it.username)
                    assertEquals("https://example.com", it.baseUrl)
                    assertEquals("token", it.token)
                    assertEquals("Display Name", it.displayName)
                }
            )
        }

    @Test
    fun `storeProfileSuspend updates the existing user resolved from the attributes' id`() =
        runTest {
            val existing = user(id = 10, username = "userA", baseUrl = "https://old.example.com")
            val attributes = UserManager.UserAttributes(
                id = 10,
                serverUrl = "https://new.example.com",
                currentUser = true,
                userId = "userId",
                token = "newToken",
                displayName = "New Display Name",
                pushConfigurationState = null,
                capabilities = null,
                serverVersion = null,
                certificateAlias = null,
                externalSignalingServer = null
            )
            wheneverBlocking { usersRepository.getUserWithId(10L) }.thenReturn(existing)
            wheneverBlocking { usersRepository.insertUser(existing) }.thenReturn(10L)

            val result = userManager.storeProfileSuspend("userA", attributes)

            assertEquals("https://new.example.com", existing.baseUrl)
            assertEquals("newToken", existing.token)
            assertEquals("New Display Name", existing.displayName)
            assertEquals(existing, result)
        }
}
