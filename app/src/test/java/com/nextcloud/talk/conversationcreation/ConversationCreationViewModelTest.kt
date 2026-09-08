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
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.passwordResult.PasswordResult
import com.nextcloud.talk.repositories.passwordpolicy.PasswordPolicyRepository
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

/**
 * What the creation screen's ViewModel does before and after the current user is known.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationCreationViewModelTest {

    private val users = MutableSharedFlow<User>(replay = 1)
    private val repository = FakeConversationCreationRepository()

    private val userProvider = object : CurrentUserProvider {
        override val currentUserFlow: Flow<User> = users
        override suspend fun getCurrentUser(timeout: Long): Result<User> =
            Result.failure(UnsupportedOperationException())
    }

    private val passwordPolicyRepository = object : PasswordPolicyRepository {
        override suspend fun validatePassword(credentials: String, url: String, password: String): PasswordResult =
            PasswordResult(passed = true, reason = null)
    }

    private fun viewModel() =
        ConversationCreationViewModel(
            repository,
            ConversationCreator(repository),
            passwordPolicyRepository,
            userProvider
        )

    private fun user(vararg spreedFeatures: SpreedFeatures) =
        User(
            username = "alice",
            token = "token",
            baseUrl = "https://cloud.example.com",
            capabilities = Capabilities().apply {
                spreedCapability = SpreedCapability().apply {
                    features = spreedFeatures.map { it.value }
                }
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
    fun `nothing is asked of the server while the current user is unknown`() =
        runTest {
            val model = viewModel()

            assertNull(model.currentUser.value)
            assertEquals(0, repository.presetCalls)
            assertTrue(model.isLoadingPresets)
        }

    @Test
    fun `creating is refused while the current user is unknown`() =
        runTest {
            val model = viewModel()

            model.createRoomAndAddParticipants()

            assertNull(repository.bodyRequest)
            assertNull(repository.formRequest)
        }

    @Test
    fun `the presets are requested once the user arrives`() =
        runTest {
            val model = viewModel()

            users.emit(user(SpreedFeatures.CONVERSATION_PRESETS))

            assertEquals(1, repository.presetCalls)
            assertTrue(model.presets.value is PresetsUiState.Success)
            assertFalse(model.isLoadingPresets)
        }

    @Test
    fun `a server without the capability is never asked for presets`() =
        runTest {
            val model = viewModel()

            users.emit(user())

            assertEquals(0, repository.presetCalls)
            assertFalse(model.showPresetSelection)
            assertFalse(model.isLoadingPresets)
        }
}
