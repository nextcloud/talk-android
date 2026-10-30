/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.ContactsContract
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.Destination
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.Request
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.RoutingState
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.OfflineFirstConversationsRepository.ConversationResult
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.DefaultAccountProvider
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.DeepLinkHandler
import com.nextcloud.talk.utils.InternalEntryPoint
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
@Suppress("TooManyFunctions")
class DeepLinkRoutingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val alice = User(id = 1, username = "alice", baseUrl = "https://cloud.example.com")
    private val bob = User(id = 2, username = "bob", baseUrl = "https://other.example.com")

    private val userManager = mock<UserManager> {
        on { getUsersNotScheduledForDeletion() } doReturn listOf(alice, bob)
        on { getUserWithInternalId(alice.id!!) } doReturn alice
        on { getUserWithInternalId(bob.id!!) } doReturn bob
        on { setUserAsActive(any()) } doReturn true
    }
    private val defaultAccountProvider = mock<DefaultAccountProvider> {
        on { getDefaultUser() } doReturn alice
    }
    private val conversationsRepository = mock<OfflineConversationsRepository> {
        on { observeConversation(any(), any()) } doReturn flowOf(ConversationResult.NotFound)
        on { syncRooms(any(), any(), anyOrNull()) } doReturn emptyList()
    }

    private lateinit var viewModel: DeepLinkRoutingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = createViewModel(conversationsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        FakeContactsProvider.cursor = null
        FakeContactsProvider.error = null
    }

    private fun createViewModel(
        repository: OfflineConversationsRepository,
        userManager: UserManager = this.userManager
    ) = DeepLinkRoutingViewModel(
        context = RuntimeEnvironment.getApplication(),
        userManager = userManager,
        defaultAccountProvider = defaultAccountProvider,
        conversationsRepository = repository,
        ncApiCoroutines = mock(),
        logger = mock()
    ).also { it.ioDispatcher = dispatcher }

    // passed on by DeepLinkActivity
    private fun internalIntent() =
        InternalEntryPoint.createIntent(RuntimeEnvironment.getApplication(), ConversationsListActivity::class.java)
            .setAction(Intent.ACTION_VIEW)

    private val deepLink = Request.DeepLink(DeepLinkHandler.DeepLinkResult(TOKEN, "https://cloud.example.com", "alice"))

    @Test
    fun `a deep link of another app asks for nothing`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nextcloudtalk://alice@cloud.example.com/call/$TOKEN"))
            .setClass(RuntimeEnvironment.getApplication(), ConversationsListActivity::class.java)

        assertNull(Request.of(intent))
    }

    @Test
    fun `a passed on deep link asks for its conversation`() {
        val intent = internalIntent().setData(Uri.parse("nextcloudtalk://alice@cloud.example.com/call/$TOKEN"))

        assertEquals(deepLink, Request.of(intent))
    }

    @Test
    fun `an intent of the app without a uri asks for nothing`() {
        assertNull(Request.of(internalIntent().putExtra(BundleKeys.KEY_ROOM_TOKEN, TOKEN)))
    }

    @Test
    fun `a passed on contact intent of another type asks for nothing`() {
        val intent = internalIntent()
            .setDataAndType(Uri.parse("content://com.android.contacts/data/1"), "text/plain")

        assertNull(Request.of(intent))
    }

    @Test
    fun `a passed on contact chat intent asks for the chat with the contact`() {
        val uri = Uri.parse("content://com.android.contacts/data/1")
        val intent = internalIntent()
            .setDataAndType(uri, "vnd.android.cursor.item/vnd.com.nextcloud.talk2.chat")

        assertEquals(Request.ContactChat(uri), Request.of(intent))
    }

    @Test
    fun `a request is routing until what it opens was opened`() =
        runTest(dispatcher) {
            viewModel.route(deepLink)
            assertTrue(viewModel.isRouting)
            advanceUntilIdle()
            viewModel.decline()

            assertEquals(RoutingState.Open(Destination.ConversationList), viewModel.state.value)
            assertTrue(viewModel.isRouting)

            viewModel.onOpened()

            assertEquals(RoutingState.Done, viewModel.state.value)
            assertFalse(viewModel.isRouting)
        }

    @Test
    fun `a deep link to an unknown conversation asks first and opens it when confirmed`() =
        runTest(dispatcher) {
            viewModel.route(deepLink)
            advanceUntilIdle()

            assertEquals(RoutingState.AskToJoin("cloud.example.com"), viewModel.state.value)
            assertTrue(viewModel.isRouting)

            viewModel.confirm()
            advanceUntilIdle()

            assertEquals(RoutingState.Open(Destination.Chat(alice.id!!, TOKEN)), viewModel.state.value)
        }

    @Test
    fun `a declined deep link opens the conversation list`() =
        runTest(dispatcher) {
            viewModel.route(deepLink)
            advanceUntilIdle()

            viewModel.decline()

            assertEquals(RoutingState.Open(Destination.ConversationList), viewModel.state.value)
        }

    @Test
    fun `a newer request replaces one that is still being routed`() =
        runTest(dispatcher) {
            // the sync of the deep link takes a while, and would then ask whether to join its conversation
            val slowRepository = mock<OfflineConversationsRepository> {
                on { observeConversation(any(), any()) } doReturn flowOf(ConversationResult.NotFound)
                on { syncRooms(any(), any(), anyOrNull()) } doSuspendableAnswer {
                    delay(SLOW_SYNC_MS)
                    emptyList()
                }
            }
            viewModel = createViewModel(slowRepository)

            viewModel.route(deepLink)
            runCurrent()

            assertEquals(noAccountForContact, routeContactChatWith())
        }

    @Test
    fun `a newer request drops the question of an older one`() =
        runTest(dispatcher) {
            viewModel.route(deepLink)
            advanceUntilIdle()
            routeContactChatWith()
            viewModel.onOpened()

            viewModel.confirm()
            advanceUntilIdle()

            assertEquals(RoutingState.Done, viewModel.state.value)
        }

    private val contactUri = Uri.parse("content://com.android.contacts/data/1")

    // the contact chat of a uri the contacts provider can't answer, as another app can send any uri
    private fun routeContactChatWith(cursor: Cursor? = null, error: RuntimeException? = null): RoutingState {
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        FakeContactsProvider.cursor = cursor
        FakeContactsProvider.error = error
        viewModel.route(Request.ContactChat(contactUri))
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel.state.value
    }

    private val routingError = RoutingState.Open(Destination.ConversationList, R.string.nc_common_error_sorry)

    private fun databaseError(): Nothing = throw SQLiteException("database is locked")

    private val noAccountForContact =
        RoutingState.Open(
            Destination.ConversationList,
            R.string.nc_phone_book_integration_account_not_found,
            isLongMessage = true
        )

    @Test
    fun `a contact uri the provider rejects opens the start screen`() {
        assertEquals(noAccountForContact, routeContactChatWith(error = IllegalArgumentException("Unknown URI")))
    }

    @Test
    fun `a contact uri without the user id column opens the start screen`() {
        val contact = MatrixCursor(arrayOf(ContactsContract.Contacts._ID)).apply { addRow(arrayOf(1)) }

        assertEquals(noAccountForContact, routeContactChatWith(cursor = contact))
    }

    @Test
    fun `a contact without a user id opens the start screen`() {
        val contactData = MatrixCursor(arrayOf(ContactsContract.Data.DATA1)).apply { addRow(arrayOf(null)) }

        assertEquals(noAccountForContact, routeContactChatWith(cursor = contactData))
    }

    @Test
    fun `a routing step that fails shows an error instead of crashing`() =
        runTest(dispatcher) {
            val failingUserManager = mock<UserManager> {
                on { getUsersNotScheduledForDeletion() } doSuspendableAnswer { databaseError() }
            }
            viewModel = createViewModel(conversationsRepository, failingUserManager)

            viewModel.route(deepLink)
            advanceUntilIdle()

            assertEquals(routingError, viewModel.state.value)
        }

    @Test
    fun `a confirmed step that fails shows an error instead of crashing`() =
        runTest(dispatcher) {
            val failingUserManager = mock<UserManager> {
                on { getUsersNotScheduledForDeletion() } doReturn listOf(alice, bob)
                on { setUserAsActive(any()) } doSuspendableAnswer { databaseError() }
            }
            viewModel = createViewModel(conversationsRepository, failingUserManager)
            viewModel.route(deepLink)
            advanceUntilIdle()

            viewModel.confirm()
            advanceUntilIdle()

            assertEquals(routingError, viewModel.state.value)
        }

    // alice is still the active account, but scheduled for deletion, so only bob is left
    private val userManagerWithAliceDeleted = mock<UserManager> {
        on { getUsersNotScheduledForDeletion() } doReturn listOf(bob)
        on { getUserWithId(alice.id!!) } doReturn alice
        on { getUserWithInternalId(bob.id!!) } doReturn bob
        on { setUserAsActive(any()) } doReturn true
    }

    @Test
    fun `a deep link doesn't open with an account scheduled for deletion`() =
        runTest(dispatcher) {
            viewModel = createViewModel(conversationsRepository, userManagerWithAliceDeleted)

            viewModel.route(deepLink)
            advanceUntilIdle()

            val noAccountForServer = RoutingState.Open(
                Destination.ConversationList,
                R.string.nc_no_account_for_server,
                isLongMessage = true
            )
            assertEquals(noAccountForServer, viewModel.state.value)
        }

    @Test
    fun `a contact chat doesn't open with an account scheduled for deletion`() {
        viewModel = createViewModel(conversationsRepository, userManagerWithAliceDeleted)
        val contactData = MatrixCursor(arrayOf(ContactsContract.Data.DATA1)).apply {
            addRow(arrayOf("carol@cloud.example.com"))
        }

        assertEquals(noAccountForContact, routeContactChatWith(cursor = contactData))
    }

    companion object {
        private const val TOKEN = "abcd1234"
        private const val SLOW_SYNC_MS = 1000L
    }
}

/** A contacts provider that answers with [cursor] or throws [error]. */
class FakeContactsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        error?.let { throw it }
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        var cursor: Cursor? = null
        var error: RuntimeException? = null
    }
}
