/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.events.EventStatus
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.CapabilitiesList
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOCS
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.capabilities.ServerVersion
import com.nextcloud.talk.users.UserManager
import io.reactivex.Single
import org.greenrobot.eventbus.EventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class CapabilitiesWorkerTest {

    private val userManager: UserManager = mock()
    private val eventBus: EventBus = mock()
    private lateinit var worker: CapabilitiesWorker

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        worker = TestListenableWorkerBuilder<CapabilitiesWorker>(context).build()
        worker.userManager = userManager
        worker.eventBus = eventBus
    }

    private fun user(id: Long = USER_ID) = User(id = id, username = "alice", baseUrl = "https://example.com")

    private fun overall(capabilities: Capabilities?, serverVersion: ServerVersion? = ServerVersion()) =
        CapabilitiesOverall(CapabilitiesOCS(meta = null, data = CapabilitiesList(serverVersion, capabilities)))

    @Test
    fun `stores capabilities and posts success when the response is well-formed`() {
        val testUser = user()
        val capabilities = Capabilities()
        val serverVersion = ServerVersion(major = 30)
        whenever(userManager.updateOrCreateUser(testUser)).thenReturn(Single.just(1))

        val result = worker.updateUser(overall(capabilities, serverVersion), testUser)

        assertTrue(result)
        assertEquals(capabilities, testUser.capabilities)
        assertEquals(serverVersion, testUser.serverVersion)
        verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, true))
    }

    @Test
    fun `returns false and posts failure when no row was updated`() {
        val testUser = user()
        whenever(userManager.updateOrCreateUser(testUser)).thenReturn(Single.just(0))

        val result = worker.updateUser(overall(Capabilities()), testUser)

        assertFalse(result)
        verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
    }

    @Test
    fun `returns false and posts failure when persisting the user throws`() {
        val testUser = user()
        whenever(userManager.updateOrCreateUser(testUser)).thenReturn(Single.error(RuntimeException("db error")))

        val result = worker.updateUser(overall(Capabilities()), testUser)

        assertFalse(result)
        verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
    }

    @Test
    fun `returns false and posts failure when the response has no ocs block`() {
        val testUser = user()

        val result = worker.updateUser(CapabilitiesOverall(ocs = null), testUser)

        assertFalse(result)
        verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
        verify(userManager, never()).updateOrCreateUser(testUser)
    }

    @Test
    fun `returns false and posts failure when the response has no data block`() {
        val testUser = user()

        val result = worker.updateUser(CapabilitiesOverall(CapabilitiesOCS(meta = null, data = null)), testUser)

        assertFalse(result)
        verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
        verify(userManager, never()).updateOrCreateUser(testUser)
    }

    @Test
    fun `returns false and posts failure when the response has no capabilities`() {
        val testUser = user()

        val result = worker.updateUser(overall(capabilities = null), testUser)

        assertFalse(result)
        verify(eventBus).post(EventStatus(USER_ID, EventStatus.EventType.CAPABILITIES_FETCH, false))
        verify(userManager, never()).updateOrCreateUser(testUser)
    }

    companion object {
        private const val USER_ID = 42L
    }
}
