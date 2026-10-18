/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils.preferences.preferencestorage

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplicationComponent
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.generic.GenericMetaDto
import com.nextcloud.talk.models.json.generic.GenericOCS
import com.nextcloud.talk.models.json.generic.GenericOverall
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

/**
 * The conversation settings [DatabaseStorageModule] sends to the server. A refusal the server reports in
 * the payload rather than in the status code has to fail the save, and must not be remembered locally.
 */
class DatabaseStorageModuleTest {

    private val api = mock<NcApiCoroutines>()
    private val storage = mock<ArbitraryStorageManager>()

    private lateinit var module: DatabaseStorageModule

    @Before
    fun setUp() {
        // the module injects its dependencies from the application component when it is created
        val component = mock<NextcloudTalkApplicationComponent>()
        setSharedApplication(mock<NextcloudTalkApplication> { on { componentApplication } doReturn component })

        module = DatabaseStorageModule(USER, "token").apply {
            ncApiCoroutines = api
            arbitraryStorageManager = storage
        }
    }

    @After
    fun tearDown() {
        setSharedApplication(null)
    }

    @Test
    fun `call notifications the server accepts are stored`() =
        runTest {
            api.stub { onBlocking { notificationCalls(any(), any(), any()) } doReturn answer(HTTP_OK) }

            module.saveBoolean(CALL_NOTIFICATIONS, true)

            verify(storage).storeStorageSetting(any(), any(), anyOrNull(), anyOrNull())
        }

    @Test
    fun `call notifications the server refuses in the payload fail and are not stored`() =
        runTest {
            api.stub { onBlocking { notificationCalls(any(), any(), any()) } doReturn answer(HTTP_FORBIDDEN) }

            val result = runCatching { module.saveBoolean(CALL_NOTIFICATIONS, true) }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            verify(storage, never()).storeStorageSetting(any(), any(), anyOrNull(), anyOrNull())
        }

    @Test
    fun `a message expiration the server accepts is remembered`() =
        runTest {
            api.stub { onBlocking { setMessageExpiration(any(), any(), any()) } doReturn answer(HTTP_OK) }

            module.saveString(MESSAGE_EXPIRATION, ONE_HOUR)

            assertEquals(ONE_HOUR, module.getString(MESSAGE_EXPIRATION, ""))
        }

    @Test
    fun `a message expiration the server refuses in the payload fails and is not remembered`() =
        runTest {
            api.stub { onBlocking { setMessageExpiration(any(), any(), any()) } doReturn answer(HTTP_FORBIDDEN) }

            val result = runCatching { module.saveString(MESSAGE_EXPIRATION, ONE_HOUR) }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(EXPIRATION_OFF, module.getString(MESSAGE_EXPIRATION, ""))
        }

    @Test
    fun `a notification level the server refuses in the payload fails and is sent again next time`() =
        runTest {
            api.stub { onBlocking { setNotificationLevel(any(), any(), any()) } doReturn answer(HTTP_FORBIDDEN) }

            val result = runCatching { module.saveString(NOTIFICATION_LEVEL, "always") }
            runCatching { module.saveString(NOTIFICATION_LEVEL, "always") }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            verifyBlocking(api, times(2)) { setNotificationLevel(any(), any(), any()) }
        }

    @Test
    fun `a notification level the server accepts is not sent again`() =
        runTest {
            api.stub { onBlocking { setNotificationLevel(any(), any(), any()) } doReturn answer(HTTP_OK) }

            module.saveString(NOTIFICATION_LEVEL, "always")
            module.saveString(NOTIFICATION_LEVEL, "always")

            verifyBlocking(api, times(1)) { setNotificationLevel(any(), any(), any()) }
        }

    private fun answer(statusCode: Int) = GenericOverall(GenericOCS(GenericMetaDto(statusCode = statusCode)))

    // NextcloudTalkApplication.sharedApplication's setter is `protected` (only meant to be set
    // from onCreate()/onTerminate()); reflection is the only way in from a test.
    private fun setSharedApplication(application: NextcloudTalkApplication?) {
        NextcloudTalkApplication.Companion::class.java
            .getDeclaredMethod("setSharedApplication", NextcloudTalkApplication::class.java)
            .apply { isAccessible = true }
            .invoke(NextcloudTalkApplication.Companion, application)
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403

        const val CALL_NOTIFICATIONS = "call_notifications_switch"
        const val MESSAGE_EXPIRATION = "conversation_settings_dropdown"
        const val NOTIFICATION_LEVEL = "conversation_info_message_notifications_dropdown"
        const val ONE_HOUR = "expire_3600"
        const val EXPIRATION_OFF = "expire_0"

        val USER = User(
            id = 1,
            userId = "me",
            username = "me",
            token = "app-password",
            baseUrl = "https://server.example.com",
            capabilities = CapabilitiesDto().apply {
                spreedCapability = SpreedCapabilityDto().apply {
                    features = listOf("conversation-v4", "notification-levels")
                }
            }
        )
    }
}
