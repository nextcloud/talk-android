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

class UserManagerTest {

    private val usersRepository: UsersRepository = mock()
    private val userManager = UserManager(usersRepository)

    private fun user(id: Long, username: String, baseUrl: String, current: Boolean = false) =
        User(id = id, username = username, baseUrl = baseUrl, current = current)

    @Before
    fun setUp() {
        // No row resolves as "the" active user unless a test overrides this.
        wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(null)
        // activeUserStateFlow lazily collects this on init; an empty flow lets
        // that background collection finish without racing the synchronous updates asserted below.
        wheneverBlocking { usersRepository.getActiveUserFlow() }.thenReturn(emptyFlow())
    }

    @Test
    fun `getCurrentUser returns the active user without touching any fallback`() =
        runTest {
            val active = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
            wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(active)

            val result = userManager.getCurrentUser()

            assertEquals(active, result)
            verifyBlocking(usersRepository, never()) { getUsersNotScheduledForDeletion() }
        }

    @Test
    fun `getCurrentUser falls back to any non-deleted user and sets it active when none is active`() =
        runTest {
            val fallback = user(id = 1, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(listOf(fallback))
            wheneverBlocking { usersRepository.setUserAsActiveWithId(fallback.id!!) }.thenReturn(true)
            // getActiveUser() is re-queried after setUserAsActiveWithId() succeeds, simulating the DB
            // now reporting the freshly-activated row.
            wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(null, fallback)

            val result = userManager.getCurrentUser()

            assertEquals(fallback, result)
            verifyBlocking(usersRepository) { setUserAsActiveWithId(fallback.id!!) }
        }

    @Test
    fun `getCurrentUser is null when there is no active user and none to fall back to`() =
        runTest {
            wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(emptyList())

            assertNull(userManager.getCurrentUser())
        }

    @Test
    fun `deleteUser does nothing and returns 0 when the user does not exist`() =
        runTest {
            wheneverBlocking { usersRepository.getUserWithId(42L) }.thenReturn(null)

            val result = userManager.deleteUser(42L)

            assertEquals(0, result)
            verify(usersRepository, never()).deleteUser(any())
        }

    @Test
    fun `deleteUser deletes the user when it exists`() =
        runTest {
            val existing = user(id = 42, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUserWithId(42L) }.thenReturn(existing)
            wheneverBlocking { usersRepository.deleteUser(existing) }.thenReturn(1)

            val result = userManager.deleteUser(42L)

            assertEquals(1, result)
            verify(usersRepository).deleteUser(existing)
        }

    @Test
    fun `checkIfUserIsScheduledForDeletion reflects the matching user's flag`() =
        runTest {
            val scheduled = user(id = 1, username = "userA", baseUrl = "https://example.com")
                .apply { scheduledForDeletion = true }
            wheneverBlocking {
                usersRepository.getUserWithUsernameAndServer("userA", "https://example.com")
            }.thenReturn(scheduled)

            assertTrue(userManager.checkIfUserIsScheduledForDeletion("userA", "https://example.com"))
        }

    @Test
    fun `checkIfUserIsScheduledForDeletion is false when the user does not exist`() =
        runTest {
            wheneverBlocking {
                usersRepository.getUserWithUsernameAndServer("userA", "https://example.com")
            }.thenReturn(null)

            assertFalse(userManager.checkIfUserIsScheduledForDeletion("userA", "https://example.com"))
        }

    @Test
    fun `checkIfUserExists is true only when a matching user is found`() =
        runTest {
            wheneverBlocking {
                usersRepository.getUserWithUsernameAndServer("userA", "https://example.com")
            }.thenReturn(user(id = 1, username = "userA", baseUrl = "https://example.com"))
            wheneverBlocking {
                usersRepository.getUserWithUsernameAndServer("userB", "https://example.com")
            }.thenReturn(null)

            assertTrue(userManager.checkIfUserExists("userA", "https://example.com"))
            assertFalse(userManager.checkIfUserExists("userB", "https://example.com"))
        }

    @Test
    fun `scheduleUserForDeletionWithId returns false when the user does not exist`() =
        runTest {
            wheneverBlocking { usersRepository.getUserWithId(99L) }.thenReturn(null)

            assertFalse(userManager.scheduleUserForDeletionWithId(99L))
            verify(usersRepository, never()).updateUser(any())
        }

    @Test
    fun `scheduleUserForDeletionWithId marks the user deleted and returns false with nobody left to activate`() =
        runTest {
            val target = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
            wheneverBlocking { usersRepository.getUserWithId(1L) }.thenReturn(target)
            wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(emptyList())

            val result = userManager.scheduleUserForDeletionWithId(1L)

            assertFalse(result)
            assertTrue(target.scheduledForDeletion)
            assertFalse(target.current)
            verify(usersRepository).updateUser(target)
        }

    @Test
    fun `scheduleUserForDeletionWithId returns true and activates another user when one remains`() =
        runTest {
            val target = user(id = 1, username = "userA", baseUrl = "https://example.com", current = true)
            val other = user(id = 2, username = "userB", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.getUserWithId(1L) }.thenReturn(target)
            wheneverBlocking { usersRepository.getUsersNotScheduledForDeletion() }.thenReturn(listOf(other))
            wheneverBlocking { usersRepository.setUserAsActiveWithId(other.id!!) }.thenReturn(true)
            wheneverBlocking { usersRepository.getActiveUser() }.thenReturn(other)

            val result = userManager.scheduleUserForDeletionWithId(1L)

            assertTrue(result)
            assertTrue(target.scheduledForDeletion)
            verify(usersRepository).setUserAsActiveWithId(other.id!!)
        }

    @Test
    fun `updateExternalSignalingServer throws when the user does not exist`() =
        runTest {
            wheneverBlocking { usersRepository.getUserWithId(7L) }.thenReturn(null)

            try {
                userManager.updateExternalSignalingServer(7L, ExternalSignalingServer())
                fail("Expected NoSuchElementException")
            } catch (expected: NoSuchElementException) {
                // expected
            }
        }

    @Test
    fun `updateExternalSignalingServer updates the matching user`() =
        runTest {
            val existing = user(id = 7, username = "userA", baseUrl = "https://example.com")
            val server = ExternalSignalingServer(externalSignalingServer = "https://signaling.example.com")
            wheneverBlocking { usersRepository.getUserWithId(7L) }.thenReturn(existing)
            wheneverBlocking { usersRepository.updateUser(existing) }.thenReturn(1)

            val result = userManager.updateExternalSignalingServer(7L, server)

            assertEquals(1, result)
            assertEquals(server, existing.externalSignalingServer)
        }

    @Test
    fun `updateOrCreateUser inserts a user without an id`() =
        runTest {
            val newUser = User(id = null, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.insertUser(newUser) }.thenReturn(5L)

            val result = userManager.updateOrCreateUser(newUser)

            assertEquals(5, result)
            verify(usersRepository, never()).updateUser(any())
        }

    @Test
    fun `updateOrCreateUser updates a user that already has an id`() =
        runTest {
            val existing = user(id = 3, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.updateUser(existing) }.thenReturn(1)

            val result = userManager.updateOrCreateUser(existing)

            assertEquals(1, result)
            verify(usersRepository, never()).insertUser(any())
        }

    @Test
    fun `setUserAsActive publishes the new user on currentUserFlow only when it succeeds`() =
        runTest {
            val target = user(id = 1, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.setUserAsActiveWithId(1L) }.thenReturn(true)

            val result = userManager.setUserAsActive(target)

            assertTrue(result)
            assertEquals(target, userManager.currentUserFlow.value)
        }

    @Test
    fun `setUserAsActive leaves currentUserFlow untouched when it fails`() =
        runTest {
            val target = user(id = 1, username = "userA", baseUrl = "https://example.com")
            wheneverBlocking { usersRepository.setUserAsActiveWithId(1L) }.thenReturn(false)

            val result = userManager.setUserAsActive(target)

            assertFalse(result)
            assertNull(userManager.currentUserFlow.value)
        }

    @Test
    fun `storeProfile creates a new user when the attributes carry no id`() =
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

            val result = userManager.storeProfile("userA", attributes)

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
    fun `storeProfile updates the existing user resolved from the attributes' id`() =
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

            val result = userManager.storeProfile("userA", attributes)

            assertEquals("https://new.example.com", existing.baseUrl)
            assertEquals("newToken", existing.token)
            assertEquals("New Display Name", existing.displayName)
            assertEquals(existing, result)
        }
}
