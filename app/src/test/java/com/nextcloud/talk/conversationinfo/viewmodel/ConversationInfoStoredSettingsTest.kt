/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.viewmodel

import android.content.res.Resources
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationinfo.ConversationInfoUiEvent
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.repositories.passwordpolicy.PasswordPolicyRepository
import com.nextcloud.talk.utils.preferences.preferencestorage.DatabaseStorageModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.io.IOException

/**
 * The settings [ConversationInfoViewModel] saves through [DatabaseStorageModule]: call notifications,
 * the notification level and the message expiration. A change that does not reach the server has to
 * go back to the value the server last confirmed, and tell the user.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationInfoStoredSettingsTest {

    private val dispatcher = StandardTestDispatcher()

    private val storage = mock<DatabaseStorageModule>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // the notification level and the message expiration read their choices from the app resources
        val resources = mock<Resources> {
            on { getStringArray(R.array.message_notification_levels_entry_values) } doReturn LEVEL_VALUES
            on { getStringArray(R.array.message_notification_levels) } doReturn LEVEL_DESCRIPTIONS
            on { getStringArray(R.array.message_expiring_values) } doReturn EXPIRATION_VALUES
            on { getStringArray(R.array.message_expiring_descriptions) } doReturn EXPIRATION_DESCRIPTIONS
        }
        setSharedApplication(mock<NextcloudTalkApplication> { on { this.resources } doReturn resources })
    }

    @After
    fun tearDown() {
        setSharedApplication(null)
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        ConversationInfoViewModel(
            chatNetworkDataSource = mock<ChatNetworkDataSource>(),
            conversationsRepository = FakeConversationsRepository(),
            ncApi = mock<NcApi>(),
            passwordPolicyRepository = mock<PasswordPolicyRepository>(),
            logger = mock<Logger>()
        ).apply { databaseStorageModule = storage }

    private fun failEverySave() {
        storage.stub {
            onBlocking { saveBoolean(any(), any()) } doSuspendableAnswer { throw IOException("no connection") }
            onBlocking { saveString(any(), any()) } doSuspendableAnswer { throw IOException("no connection") }
        }
    }

    @Test
    fun `call notifications that cannot be saved go back and say so`() =
        runTest(dispatcher) {
            failEverySave()
            val model = viewModel()
            val seen = mutableListOf<ConversationInfoUiEvent>()
            val collector = launch { model.uiEvent.collect { seen.add(it) } }
            dispatcher.scheduler.runCurrent()

            model.toggleCallNotifications()
            dispatcher.scheduler.runCurrent()
            assertFalse(model.uiState.value.callNotificationsEnabled)

            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(model.uiState.value.callNotificationsEnabled)
            assertTrue(seen.contains(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry)))
            collector.cancel()
        }

    @Test
    fun `call notifications that are saved stay changed`() =
        runTest(dispatcher) {
            val model = viewModel()

            model.toggleCallNotifications()
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(model.uiState.value.callNotificationsEnabled)
        }

    @Test
    fun `two call notification changes that both fail go back to the value the server confirmed`() =
        runTest(dispatcher) {
            failEverySave()
            val model = viewModel()

            model.toggleCallNotifications()
            dispatcher.scheduler.runCurrent()
            model.toggleCallNotifications()
            dispatcher.scheduler.runCurrent()

            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(model.uiState.value.callNotificationsEnabled)
        }

    @Test
    fun `a notification level that cannot be saved goes back to the previous one`() =
        runTest(dispatcher) {
            failEverySave()
            val model = viewModel()

            model.saveNotificationLevel(ALWAYS)
            dispatcher.scheduler.runCurrent()
            assertEquals(LEVEL_DESCRIPTIONS[ALWAYS], model.uiState.value.notificationLevel)

            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("", model.uiState.value.notificationLevel)
        }

    @Test
    fun `two notification levels that both fail go back to the level the server confirmed`() =
        runTest(dispatcher) {
            failEverySave()
            val model = viewModel()

            model.saveNotificationLevel(NEVER)
            dispatcher.scheduler.runCurrent()
            model.saveNotificationLevel(ALWAYS)
            dispatcher.scheduler.runCurrent()

            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("", model.uiState.value.notificationLevel)
        }

    @Test
    fun `a message expiration that cannot be saved goes back to the previous one`() =
        runTest(dispatcher) {
            failEverySave()
            val model = viewModel()

            model.saveMessageExpiration(ONE_DAY)
            dispatcher.scheduler.runCurrent()
            assertEquals(EXPIRATION_DESCRIPTIONS[ONE_DAY], model.uiState.value.messageExpirationLabel)

            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("", model.uiState.value.messageExpirationLabel)
        }

    @Test
    fun `a message expiration that is saved stays changed`() =
        runTest(dispatcher) {
            val model = viewModel()

            model.saveMessageExpiration(ONE_DAY)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(EXPIRATION_DESCRIPTIONS[ONE_DAY], model.uiState.value.messageExpirationLabel)
        }

    // NextcloudTalkApplication.sharedApplication's setter is `protected` (only meant to be set
    // from onCreate()/onTerminate()); reflection is the only way in from a test.
    private fun setSharedApplication(application: NextcloudTalkApplication?) {
        NextcloudTalkApplication.Companion::class.java
            .getDeclaredMethod("setSharedApplication", NextcloudTalkApplication::class.java)
            .apply { isAccessible = true }
            .invoke(NextcloudTalkApplication.Companion, application)
    }

    private companion object {
        const val NEVER = 0
        const val ALWAYS = 2
        const val ONE_DAY = 3

        val LEVEL_VALUES = arrayOf("never", "mention", "always")
        val LEVEL_DESCRIPTIONS = arrayOf("Never", "Mentions", "Always")
        val EXPIRATION_VALUES = arrayOf("expire_0", "expire_2419200", "expire_604800", "expire_86400")
        val EXPIRATION_DESCRIPTIONS = arrayOf("Off", "4 weeks", "1 week", "1 day")
    }
}
