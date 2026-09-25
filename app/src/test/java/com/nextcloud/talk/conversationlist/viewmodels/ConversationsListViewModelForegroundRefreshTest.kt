/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.viewmodels

import android.app.Application
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.ConversationListUpdater
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import io.reactivex.Maybe
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ConversationsListViewModel.refreshRoomsIfIdle]: which refresh ticks reach the
 * repository and which are dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsListViewModelForegroundRefreshTest {

    private val repository: OfflineConversationsRepository = mock()
    private val currentUserProvider: CurrentUserProviderOld = mock()

    private lateinit var viewModel: ConversationsListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        wheneverBlocking { repository.isPeriodicSyncDue(any()) }.thenReturn(true)
        whenever(repository.roomListFlow).thenReturn(emptyFlow())
        whenever(repository.syncErrorFlow).thenReturn(emptyFlow())
        whenever(currentUserProvider.currentUser).thenReturn(Maybe.just(USER))

        viewModel = ConversationsListViewModel(
            repository,
            mock<ThreadsRepository>(),
            currentUserProvider,
            mock<OpenConversationsRepository>(),
            mock<ContactsRepository>(),
            mock<UnifiedSearchRepository>(),
            mock<InvitationsRepository>(),
            mock<ArbitraryStorageManager>(),
            mock<UserManager>(),
            mock<ConversationsRepository>(),
            mock<ConversationListUpdater>(),
            mock<Logger>()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a tick refreshes the list once the previous sync has finished`() {
        whenever(repository.getRooms(any(), any())).thenReturn(completedJob())
        viewModel.getRooms(USER)

        runBlocking { viewModel.refreshRoomsIfIdle(USER) }

        verify(repository, times(2)).getRooms(eq(USER), eq(false))
    }

    @Test
    fun `a tick while a sync is still running is dropped rather than queued`() {
        whenever(repository.getRooms(any(), any())).thenReturn(Job())
        viewModel.getRooms(USER)

        runBlocking { viewModel.refreshRoomsIfIdle(USER) }
        runBlocking { viewModel.refreshRoomsIfIdle(USER) }

        verify(repository, times(1)).getRooms(eq(USER), eq(false))
    }

    @Test
    fun `a tick during an open search leaves the list alone`() {
        whenever(repository.getRooms(any(), any())).thenReturn(completedJob())
        viewModel.getRooms(USER)
        viewModel.setIsSearchActive(true)

        runBlocking { viewModel.refreshRoomsIfIdle(USER) }

        verify(repository, times(1)).getRooms(eq(USER), eq(false))
    }

    @Test
    fun `the refresh resumes once the search is closed`() {
        whenever(repository.getRooms(any(), any())).thenReturn(completedJob())
        viewModel.getRooms(USER)
        viewModel.setIsSearchActive(true)
        runBlocking { viewModel.refreshRoomsIfIdle(USER) }
        viewModel.setIsSearchActive(false)

        runBlocking { viewModel.refreshRoomsIfIdle(USER) }

        verify(repository, times(2)).getRooms(eq(USER), eq(false))
    }

    @Test
    fun `a tick that would have to fetch the whole list is dropped until the cadence is due`() {
        whenever(repository.getRooms(any(), any())).thenReturn(completedJob())
        wheneverBlocking { repository.isPeriodicSyncDue(any()) }.thenReturn(false)
        viewModel.getRooms(USER)

        runBlocking { viewModel.refreshRoomsIfIdle(USER) }
        runBlocking { viewModel.refreshRoomsIfIdle(USER) }

        verify(repository, times(1)).getRooms(eq(USER), eq(false))
    }

    @Test
    fun `a tick never asks for a full sync, that is what the cadence and pull to refresh are for`() {
        whenever(repository.getRooms(any(), any())).thenReturn(completedJob())
        viewModel.getRooms(USER)

        runBlocking { viewModel.refreshRoomsIfIdle(USER) }

        verify(repository, never()).getRooms(any(), eq(true))
    }

    private fun completedJob(): CompletableJob = Job().apply { complete() }

    companion object {
        private val USER = User(
            id = 1,
            userId = "me",
            username = "me",
            token = "app-password",
            baseUrl = "https://server.example.com"
        )
    }
}
