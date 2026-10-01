/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.conversationcreation.viewmodel.ConversationCreationViewModel
import com.nextcloud.talk.conversationcreation.viewmodel.PresetsUiState
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.capabilities.PasswordApiDto
import com.nextcloud.talk.models.json.capabilities.PasswordPolicyDto
import com.nextcloud.talk.passwordpolicy.FakePasswordPolicyRepository
import com.nextcloud.talk.utils.SpreedFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * What the creation screen's ViewModel does depending on the capabilities of its user.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationCreationViewModelTest {

    private val repository = FakeConversationCreationRepository()

    private val passwordPolicyRepository = FakePasswordPolicyRepository()

    private fun viewModel(user: User) =
        ConversationCreationViewModel(
            repository,
            ConversationCreator(repository, mock<Logger>()),
            passwordPolicyRepository,
            user
        )

    private fun user(vararg spreedFeatures: SpreedFeatures, passwordEnforced: Boolean = false) =
        User(
            username = "alice",
            token = "token",
            baseUrl = "https://cloud.example.com",
            capabilities = CapabilitiesDto().apply {
                spreedCapability = SpreedCapabilityDto().apply {
                    features = spreedFeatures.map { it.value }
                    config = hashMapOf("conversations" to hashMapOf("force-passwords" to passwordEnforced))
                }
                passwordPolicy = PasswordPolicyDto(
                    PasswordApiDto(
                        validatePasswordApi = "https://cloud.example.com/validate",
                        generatePasswordApi = "https://cloud.example.com/generate"
                    )
                )
            }
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the presets are requested when the server supports them`() =
        runTest {
            val model = viewModel(user(SpreedFeatures.CONVERSATION_PRESETS))

            assertEquals(1, repository.presetCalls)
            assertTrue(model.presets.value is PresetsUiState.Success)
            assertFalse(model.isLoadingPresets)
        }

    @Test
    fun `opening a conversation to guests generates the password the server enforces`() =
        runTest {
            passwordPolicyRepository.generatedPassword = "generated"
            val model = viewModel(user(passwordEnforced = true))

            model.allowGuests(true)

            assertEquals("generated", model.password.value)
        }

    @Test
    fun `a password the user typed is left alone`() =
        runTest {
            passwordPolicyRepository.generatedPassword = "generated"
            val model = viewModel(user(passwordEnforced = true))
            model.updatePassword("hunter2")

            model.allowGuests(true)

            assertEquals("hunter2", model.password.value)
        }

    @Test
    fun `no password is generated where the server does not enforce one`() =
        runTest {
            passwordPolicyRepository.generatedPassword = "generated"
            val model = viewModel(user())

            model.allowGuests(true)

            assertEquals("", model.password.value)
            assertNull(passwordPolicyRepository.generationUrl)
        }

    @Test
    fun `a server without the capability is never asked for presets`() =
        runTest {
            val model = viewModel(user())

            assertEquals(0, repository.presetCalls)
            assertFalse(model.showPresetSelection)
            assertFalse(model.isLoadingPresets)
        }
}
