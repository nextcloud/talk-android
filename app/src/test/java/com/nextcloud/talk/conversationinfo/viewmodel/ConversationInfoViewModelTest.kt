/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.viewmodel

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.repositories.passwordpolicy.PasswordPolicyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationInfoViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val user = User(
        username = "alice",
        token = "token",
        baseUrl = "https://cloud.example.com",
        capabilities = Capabilities().apply {
            spreedCapability = SpreedCapability().apply {
                features = listOf("conversation-v4")
            }
        }
    )

    private fun viewModel(repository: FakeConversationsRepository) =
        ConversationInfoViewModel(
            chatNetworkDataSource = mock<ChatNetworkDataSource>(),
            conversationsRepository = repository,
            ncApi = mock<NcApi>(),
            passwordPolicyRepository = mock<PasswordPolicyRepository>()
        )

    @Test
    fun `turning guests off clears hasPassword so a later enable asks for one again`() =
        runTest(dispatcher) {
            val repository = FakeConversationsRepository()
            val model = viewModel(repository)

            model.allowGuests(user, "token", true, "hunter2")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, model.uiState.value.hasPassword)

            model.allowGuests(user, "token", false)
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(model.uiState.value.hasPassword)
        }

    @Test
    fun `turning guests off hides the password protection entry immediately`() =
        runTest(dispatcher) {
            val repository = FakeConversationsRepository()
            val model = viewModel(repository)

            model.allowGuests(user, "token", true, "hunter2")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, model.uiState.value.showPasswordProtection)

            model.allowGuests(user, "token", false)

            assertFalse(model.uiState.value.showPasswordProtection)
        }

    @Test
    fun `re-enabling guests without a password fails against an enforcing server and reverts the switch`() =
        runTest(dispatcher) {
            val repository = FakeConversationsRepository().apply { failAllowGuests = true }
            val model = viewModel(repository)

            model.allowGuests(user, "token", true, "hunter2")
            dispatcher.scheduler.advanceUntilIdle()
            model.allowGuests(user, "token", false)
            dispatcher.scheduler.advanceUntilIdle()

            model.allowGuests(user, "token", true)
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(model.uiState.value.guestsAllowed)
        }

    @Test
    fun `createConversationNameByParticipants should combine names correctly`() {
        val original = listOf("Dave", null, "Charlie")
        val all = listOf("Bob", "Charlie", "Dave", "Alice", null, "Simon")

        val expectedName = "Charlie, Dave, Alice, Bob, Simon"
        val result = ConversationInfoViewModel.createConversationNameByParticipants(original, all)

        assertEquals(expectedName, result)
    }
}
