/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.viewmodel

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.participants.Participant
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

    private fun model(
        displayName: String,
        type: Participant.ParticipantType,
        isOnline: Boolean = true,
        actorType: Participant.ActorType = Participant.ActorType.USERS
    ) = ParticipantModel(
        participant = Participant(
            actorType = actorType,
            actorId = displayName.lowercase(),
            displayName = displayName,
            type = type
        ),
        isOnline = isOnline
    )

    private fun sortedNames(vararg items: ParticipantModel) =
        items.sortedWith(ConversationInfoViewModel.PARTICIPANT_COMPARATOR).map { it.participant.displayName }

    @Test
    fun `owners sort above moderators despite the display name`() {
        val owner = model("Zoe", Participant.ParticipantType.OWNER)
        val moderator = model("Alice", Participant.ParticipantType.MODERATOR)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(moderator, owner))
    }

    @Test
    fun `owners sort above guest moderators`() {
        val owner = model("Zoe", Participant.ParticipantType.OWNER)
        val guestModerator = model("Alice", Participant.ParticipantType.GUEST_MODERATOR)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(guestModerator, owner))
    }

    @Test
    fun `moderators sort above plain users`() {
        val moderator = model("Zoe", Participant.ParticipantType.MODERATOR)
        val user = model("Alice", Participant.ParticipantType.USER)

        assertEquals(listOf("Zoe", "Alice"), sortedNames(user, moderator))
    }

    @Test
    fun `offline participants sort below online ones regardless of rank`() {
        val offlineOwner = model("Zoe", Participant.ParticipantType.OWNER, isOnline = false)
        val onlineUser = model("Alice", Participant.ParticipantType.USER)

        assertEquals(listOf("Alice", "Zoe"), sortedNames(offlineOwner, onlineUser))
    }

    @Test
    fun `groups and teams sort last`() {
        val group = model("Aaa Team", Participant.ParticipantType.USER, actorType = Participant.ActorType.GROUPS)
        val user = model("Zoe", Participant.ParticipantType.USER)

        assertEquals(listOf("Zoe", "Aaa Team"), sortedNames(group, user))
    }

    @Test
    fun `same rank falls back to the display name`() {
        val bob = model("Bob", Participant.ParticipantType.USER)
        val alice = model("alice", Participant.ParticipantType.USER)

        assertEquals(listOf("alice", "Bob"), sortedNames(bob, alice))
    }
}
