/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.UserCapabilitiesUpdate
import com.nextcloud.talk.data.user.model.UserClientCertificateUpdate
import com.nextcloud.talk.data.user.model.UserCredentialsUpdate
import com.nextcloud.talk.data.user.model.UserDisplayNameUpdate
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.data.user.model.UserExternalSignalingServerUpdate
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.ServerVersionDto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The partial updates must only write their own columns, so a user that was read before the default account
 * changed cannot reset it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UsersDaoPartialUpdateTest {

    private lateinit var db: TalkDatabase
    private lateinit var dao: UsersDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TalkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.usersDao()
        runBlocking {
            dao.saveUser(UserEntity(id = USER_A, username = "a", baseUrl = BASE_URL, token = "tokenA", current = true))
            dao.saveUser(UserEntity(id = USER_B, username = "b", baseUrl = BASE_URL, token = "tokenB"))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `partial updates of a previously active user keep the new default account`() =
        runBlocking {
            dao.setUserAsActiveWithId(USER_B)

            dao.updateCapabilities(UserCapabilitiesUpdate(USER_A, CapabilitiesDto(), ServerVersionDto(major = 30)))
            dao.updateExternalSignalingServer(
                UserExternalSignalingServerUpdate(USER_A, ExternalSignalingServer(externalSignalingServer = "wss://s"))
            )
            dao.updateDisplayName(UserDisplayNameUpdate(USER_A, "Alice"))
            dao.updateClientCertificate(UserClientCertificateUpdate(USER_A, "alias"))
            dao.updateCredentials(UserCredentialsUpdate(USER_A, "newToken", "alias"))

            val userA = dao.getUserWithId(USER_A)!!
            assertFalse(userA.current)
            assertTrue(dao.getUserWithId(USER_B)!!.current)
            assertEquals(USER_B, dao.getActiveUser()!!.id)
            assertEquals(30, userA.serverVersion?.major)
            assertEquals("wss://s", userA.externalSignalingServer?.externalSignalingServer)
            assertEquals("Alice", userA.displayName)
            assertEquals("newToken", userA.token)
            assertEquals("alias", userA.clientCertificate)
        }

    companion object {
        private const val USER_A = 1L
        private const val USER_B = 2L
        private const val BASE_URL = "https://example.com"
    }
}
