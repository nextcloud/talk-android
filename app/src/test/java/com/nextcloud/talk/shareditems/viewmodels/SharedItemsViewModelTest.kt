/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.viewmodels

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import com.nextcloud.talk.shareditems.model.SharedOtherItem
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

class SharedItemsViewModelTest {

    private val repository: SharedItemsRepository = mock()
    private lateinit var viewModel: SharedItemsViewModel

    private val user = User(username = "user", token = "token", baseUrl = "https://example.com")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        wheneverBlocking { repository.availableTypes(any()) }
            .thenReturn(setOf(SharedItemType.FILE, SharedItemType.POLL))
        wheneverBlocking { repository.media(any(), eq(SharedItemType.FILE), isNull()) }
            .thenReturn(page(SharedItemType.FILE, 1..3, lastSeenId = 1))
        viewModel = SharedItemsViewModel(repository)
        viewModel.initialize(user, "roomToken")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `next page is requested only once while a request is running`() {
        val nextPage = CompletableDeferred<SharedItems>()
        wheneverBlocking { repository.media(any(), eq(SharedItemType.FILE), eq(1)) }
            .doSuspendableAnswer { nextPage.await() }

        viewModel.loadNextItems()
        viewModel.loadNextItems()
        nextPage.complete(page(SharedItemType.FILE, 4..6, lastSeenId = 4))

        verifyBlocking(repository, times(1)) { media(any(), eq(SharedItemType.FILE), eq(1)) }
        assertEquals(listOf("3", "2", "1", "6", "5", "4"), loadedItemIds())
    }

    @Test
    fun `page of a previously selected type is discarded`() {
        val nextPage = CompletableDeferred<SharedItems>()
        wheneverBlocking { repository.media(any(), eq(SharedItemType.FILE), eq(1)) }
            .doSuspendableAnswer { nextPage.await() }
        wheneverBlocking { repository.media(any(), eq(SharedItemType.POLL), isNull()) }
            .thenReturn(page(SharedItemType.POLL, 10..11, lastSeenId = 10))

        viewModel.loadNextItems()
        viewModel.initialLoadItems(SharedItemType.POLL)
        nextPage.complete(page(SharedItemType.FILE, 4..6, lastSeenId = 4))

        val state = viewModel.viewState.value as SharedItemsViewModel.LoadedState
        assertEquals(SharedItemType.POLL, state.selectedType)
        assertEquals(listOf("11", "10"), loadedItemIds())
    }

    @Test
    fun `removed item is not shown again after loading the next page`() {
        wheneverBlocking { repository.media(any(), eq(SharedItemType.FILE), eq(1)) }
            .thenReturn(page(SharedItemType.FILE, 4..4, lastSeenId = 4, moreItemsExisting = false))
        val removed = (viewModel.viewState.value as SharedItemsViewModel.LoadedState).items.items.first()

        viewModel.removeItem(removed)
        viewModel.loadNextItems()

        assertEquals(listOf("2", "1", "4"), loadedItemIds())
        assertFalse((viewModel.viewState.value as SharedItemsViewModel.LoadedState).items.moreItemsExisting)
    }

    @Test
    fun `initializing again keeps the loaded pages`() {
        wheneverBlocking { repository.media(any(), eq(SharedItemType.FILE), eq(1)) }
            .thenReturn(page(SharedItemType.FILE, 4..6, lastSeenId = 4))
        viewModel.loadNextItems()

        viewModel.initialize(user, "roomToken")

        verifyBlocking(repository, times(1)) { availableTypes(any()) }
        assertEquals(listOf("3", "2", "1", "6", "5", "4"), loadedItemIds())
    }

    private fun loadedItemIds(): List<String> =
        (viewModel.viewState.value as SharedItemsViewModel.LoadedState).items.items.map(SharedItem::id)

    private fun page(
        type: SharedItemType,
        ids: IntRange,
        lastSeenId: Int,
        moreItemsExisting: Boolean = true
    ): SharedItems =
        SharedItems(
            ids.reversed().map { SharedOtherItem(it.toString(), "item $it", "actor", "Actor", "") },
            type,
            lastSeenId,
            moreItemsExisting
        )
}
