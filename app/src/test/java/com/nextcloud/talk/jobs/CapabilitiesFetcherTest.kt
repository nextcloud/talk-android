/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.CapabilitiesList
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOCS
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.capabilities.ServerVersion
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import retrofit2.Retrofit

class CapabilitiesFetcherTest {

    private val userManager: UserManager = mock()
    private val eventBus: EventBus = mock()
    private lateinit var fetcher: CapabilitiesFetcher

    @Before
    fun setUp() {
        fetcher = CapabilitiesFetcher(userManager, mock<Retrofit>(), eventBus, mock<OkHttpClient>())
    }

    private fun user(id: Long = USER_ID) = User(id = id, username = "alice", baseUrl = "https://example.com")

    private fun overall(capabilities: Capabilities?, serverVersion: ServerVersion? = ServerVersion()) =
        CapabilitiesOverall(CapabilitiesOCS(meta = null, data = CapabilitiesList(serverVersion, capabilities)))

    @Test
    fun `stores capabilities and posts success when the response is well-formed`() =
        runTest {
            val testUser = user()
            val capabilities = Capabilities()
            val serverVersion = ServerVersion(major = 30)
            wheneverBlocking { userManager.updateOrCreateUser(testUser) }.thenReturn(1)

            val result = fetcher.updateUser(overall(capabilities, serverVersion), testUser)

            assertTrue(result)
            assertEquals(capabilities, testUser.capabilities)
            assertEquals(serverVersion, testUser.serverVersion)
            verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, true))
        }

    @Test
    fun `returns false and posts failure when no row was updated`() =
        runTest {
            val testUser = user()
            wheneverBlocking { userManager.updateOrCreateUser(testUser) }.thenReturn(0)

            val result = fetcher.updateUser(overall(Capabilities()), testUser)

            assertFalse(result)
            verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
        }

    @Test
    fun `returns false and posts failure when persisting the user throws`() =
        runTest {
            val testUser = user()
            wheneverBlocking { userManager.updateOrCreateUser(testUser) }.thenThrow(RuntimeException("db error"))

            val result = fetcher.updateUser(overall(Capabilities()), testUser)

            assertFalse(result)
            verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
        }

    @Test
    fun `returns false and posts failure when the response has no ocs block`() =
        runTest {
            val testUser = user()

            val result = fetcher.updateUser(CapabilitiesOverall(ocs = null), testUser)

            assertFalse(result)
            verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
            verifyBlocking(userManager, never()) { updateOrCreateUser(testUser) }
        }

    @Test
    fun `returns false and posts failure when the response has no data block`() =
        runTest {
            val testUser = user()

            val result = fetcher.updateUser(CapabilitiesOverall(CapabilitiesOCS(meta = null, data = null)), testUser)

            assertFalse(result)
            verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
            verifyBlocking(userManager, never()) { updateOrCreateUser(testUser) }
        }

    @Test
    fun `returns false and posts failure when the response has no capabilities`() =
        runTest {
            val testUser = user()

            val result = fetcher.updateUser(overall(capabilities = null), testUser)

            assertFalse(result)
            verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
            verifyBlocking(userManager, never()) { updateOrCreateUser(testUser) }
        }

    companion object {
        private const val USER_ID = 42L
    }
}
