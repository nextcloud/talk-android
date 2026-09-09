/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.utils.SpreedFeatures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which requests [ConversationCreator] makes for a new conversation, and what it reports back when
 * one of them fails.
 */
class ConversationCreatorTest {

    private fun user(vararg spreedFeatures: SpreedFeatures) =
        User(
            username = "alice",
            token = "token",
            baseUrl = "https://cloud.example.com",
            capabilities = Capabilities().apply {
                spreedCapability = SpreedCapability().apply {
                    features = spreedFeatures.map { it.value } + CONVERSATION_V4
                }
            }
        )

    private fun newConversation(
        password: String = "",
        roomType: Int = CreateConversationParams.ROOM_TYPE_GROUP,
        description: String = "",
        participants: List<AutocompleteUser> = emptyList()
    ) = NewConversation(
        name = "Team",
        description = description,
        preset = ConversationPresetId.DEFAULT,
        params = CreateConversationParams(roomType = roomType),
        password = password,
        participants = participants
    )

    @Test
    fun `a server that takes every parameter is asked once`() =
        runTest {
            val repository = FakeConversationCreationRepository()
            val creator = ConversationCreator(repository)

            creator.create(user(SpreedFeatures.CONVERSATION_CREATION_ALL), newConversation(description = "Ours"))

            assertEquals("Team", repository.bodyRequest?.roomName)
            assertEquals("Ours", repository.bodyRequest?.description)
            assertNull(repository.formRequest)
            assertEquals(0, repository.descriptionCalls)
            assertEquals(0, repository.listableCalls)
        }

    @Test
    fun `a server without that capability is served by follow up requests`() =
        runTest {
            val repository = FakeConversationCreationRepository()
            val creator = ConversationCreator(repository)

            creator.create(user(), newConversation(description = "Ours"))

            assertNull(repository.bodyRequest)
            assertEquals("Team", repository.formRequest?.queryMap?.get("roomName"))
            assertEquals(1, repository.descriptionCalls)
        }

    @Test
    fun `the password travels in the creation request only where the server takes it`() =
        runTest {
            val repository = FakeConversationCreationRepository()
            val creator = ConversationCreator(repository)

            creator.create(
                user(SpreedFeatures.CONVERSATION_CREATION_ALL, SpreedFeatures.CONVERSATION_CREATION_PASSWORD),
                newConversation(password = "hunter2", roomType = CreateConversationParams.ROOM_TYPE_PUBLIC)
            )

            assertEquals("hunter2", repository.bodyRequest?.password)
            assertEquals(0, repository.passwordCalls)
        }

    @Test
    fun `without that capability the password follows the creation request`() =
        runTest {
            val repository = FakeConversationCreationRepository()
            val creator = ConversationCreator(repository)

            val conversation = creator.create(
                user(SpreedFeatures.CONVERSATION_CREATION_ALL),
                newConversation(password = "hunter2", roomType = CreateConversationParams.ROOM_TYPE_PUBLIC)
            )

            assertNull(repository.bodyRequest?.password)
            assertEquals(1, repository.passwordCalls)
            assertTrue(conversation?.hasPassword == true)
        }

    @Test
    fun `a conversation without a password never asks for one`() =
        runTest {
            val repository = FakeConversationCreationRepository()
            val creator = ConversationCreator(repository)

            creator.create(user(SpreedFeatures.CONVERSATION_CREATION_ALL), newConversation())

            assertEquals(0, repository.passwordCalls)
        }

    @Test
    fun `a rejected password leaves the conversation without one instead of losing it`() =
        runTest {
            val repository = FakeConversationCreationRepository().apply { failPassword = true }
            val creator = ConversationCreator(repository)

            val conversation = creator.create(
                user(SpreedFeatures.CONVERSATION_CREATION_ALL),
                newConversation(password = "hunter2", roomType = CreateConversationParams.ROOM_TYPE_PUBLIC)
            )

            assertEquals("abc123", conversation?.token)
            assertFalse(conversation?.hasPassword == true)
        }

    @Test
    fun `participants the server refuses are reported on the conversation`() =
        runTest {
            val repository = FakeConversationCreationRepository().apply { failingParticipants = setOf("bob") }
            val creator = ConversationCreator(repository)

            val conversation = creator.create(
                user(),
                newConversation(
                    participants = listOf(
                        AutocompleteUser("alice", "Alice", ParticipantSource.USERS),
                        AutocompleteUser("bob", "Bob", ParticipantSource.USERS)
                    )
                )
            )

            assertEquals(listOf("alice"), repository.addedParticipants)
            assertEquals(listOf("bob"), conversation?.invalidParticipants?.get(ParticipantSource.USERS))
        }

    private companion object {
        const val CONVERSATION_V4 = "conversation-v4"
    }
}
