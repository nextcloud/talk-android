/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.viewmodel

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationinfo.ConversationInfoUiEvent
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.repositories.passwordpolicy.PasswordPolicyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
        capabilities = CapabilitiesDto().apply {
            spreedCapability = SpreedCapabilityDto().apply {
                features = listOf("conversation-v4")
            }
        }
    )

    private fun viewModel(repository: FakeConversationsRepository) =
        ConversationInfoViewModel(
            chatNetworkDataSource = mock<ChatNetworkDataSource>(),
            conversationsRepository = repository,
            ncApi = mock<NcApi>(),
            passwordPolicyRepository = mock<PasswordPolicyRepository>(),
            logger = mock<Logger>()
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
    fun `an important toggle that the server never accepts goes back to what it was`() =
        runTest(dispatcher) {
            val repository = FakeConversationsRepository().apply { failingImportantRequests = Int.MAX_VALUE }
            val model = viewModel(repository)

            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.runCurrent()
            assertEquals(true, model.uiState.value.importantConversation)

            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(model.uiState.value.importantConversation)
        }

    @Test
    fun `an important toggle survives a single connection problem`() =
        runTest(dispatcher) {
            val repository = FakeConversationsRepository().apply { failingImportantRequests = 1 }
            val model = viewModel(repository)

            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, repository.importantRequests)
            assertEquals(true, model.uiState.value.importantConversation)
        }

    // undoIfNewest(): a setting can have several changes in flight at once, and an older one failing
    // must not put its value back over a newer one the server already accepted. The rule is asserted
    // on the guard itself: a two-state toggle cannot show the difference, because the older change's
    // undo value is the newer change's applied value, and the multi-value settings read their labels
    // from Android resources that a plain unit test has no access to.

    @Test
    fun `the newest change to a setting can be undone`() =
        runTest(dispatcher) {
            val model = viewModel(FakeConversationsRepository())

            val change = model.startSettingChange("level")

            model.undoIfNewest("level", change) { it.copy(notificationLevel = "restored") }.invoke()

            assertEquals("restored", model.uiState.value.notificationLevel)
        }

    @Test
    fun `an older change cannot undo a newer one, even back to the same value`() =
        runTest(dispatcher) {
            val model = viewModel(FakeConversationsRepository())
            val levelBefore = model.uiState.value.notificationLevel

            // A, B, A, B on one setting: the first B fails last, and the screen shows B again
            val first = model.startSettingChange("level")
            val firstUndo = model.undoIfNewest("level", first) { it.copy(notificationLevel = "restored") }
            model.startSettingChange("level")
            firstUndo.invoke()

            assertEquals(levelBefore, model.uiState.value.notificationLevel)
        }

    @Test
    fun `a change to one setting does not hold back the undo of another`() =
        runTest(dispatcher) {
            val model = viewModel(FakeConversationsRepository())

            val level = model.startSettingChange("level")
            val levelUndo = model.undoIfNewest("level", level) { it.copy(notificationLevel = "restored") }
            model.startSettingChange("expiration")
            levelUndo.invoke()

            assertEquals("restored", model.uiState.value.notificationLevel)
        }

    @Test
    fun `a toggle the server refuses in the payload is taken back`() =
        runTest(dispatcher) {
            val repository = FakeConversationsRepository().apply { importantResponseStatusCode = HTTP_FORBIDDEN }
            val model = viewModel(repository)

            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(model.uiState.value.importantConversation)
        }

    @Test
    fun `a toggle the server refuses in the payload tells the user`() =
        runTest(dispatcher) {
            val repository = FakeConversationsRepository().apply { importantResponseStatusCode = HTTP_FORBIDDEN }
            val model = viewModel(repository)
            val seen = mutableListOf<ConversationInfoUiEvent>()
            val collector = launch { model.uiEvent.collect { seen.add(it) } }
            dispatcher.scheduler.runCurrent()

            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(seen.any { it is ConversationInfoUiEvent.ShowSnackbar })
            collector.cancel()
        }

    @Test
    fun `two changes that both fail go back to the value the server confirmed`() =
        runTest(dispatcher) {
            val repository = FakeConversationsRepository().apply { failingImportantRequests = Int.MAX_VALUE }
            val model = viewModel(repository)

            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.runCurrent()
            assertEquals(true, model.uiState.value.importantConversation)

            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.runCurrent()
            assertFalse(model.uiState.value.importantConversation)

            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(model.uiState.value.importantConversation)
        }

    @Test
    fun `an older accepted toggle does not become the value a later failure restores`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeConversationsRepository().apply { importantGate = gate }
            val model = viewModel(repository)

            // the first change is still in flight when the second is made and accepted
            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.runCurrent()
            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.advanceUntilIdle()

            // the older one is accepted last, and must not become the confirmed value
            gate.complete(Unit)
            dispatcher.scheduler.advanceUntilIdle()

            repository.failingImportantRequests = Int.MAX_VALUE
            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(model.uiState.value.importantConversation)
        }

    @Test
    fun `an acceptance that arrives while a newer change is in flight still becomes the baseline`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeConversationsRepository().apply { importantGate = gate }
            val model = viewModel(repository)

            // the first change is accepted, so the server holds its value, but only after the second
            // change was started and failed
            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.runCurrent()
            repository.failingImportantRequests = ATTEMPTS_PER_REQUEST
            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.advanceUntilIdle()

            gate.complete(Unit)
            dispatcher.scheduler.advanceUntilIdle()

            // a later failure has to go back to what the server accepted, not to what it held before
            repository.failingImportantRequests = ATTEMPTS_PER_REQUEST
            model.toggleImportantConversation("credentials", user.baseUrl!!, "token")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(model.uiState.value.importantConversation)
        }

    private companion object {
        const val HTTP_FORBIDDEN = 403

        /** One attempt plus the single retry `optimisticAction` makes. */
        const val ATTEMPTS_PER_REQUEST = 2
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
