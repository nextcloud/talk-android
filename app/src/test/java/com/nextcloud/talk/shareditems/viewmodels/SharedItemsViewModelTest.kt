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
import com.nextcloud.talk.viewmodels.AbstractViewModelTest
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SharedItemsViewModelTest : AbstractViewModelTest() {

    private val repository: SharedItemsRepository = mock()
    private lateinit var viewModel: SharedItemsViewModel

    private val user = User(username = "user", token = "token", baseUrl = "https://example.com")

    @Before
    fun setUp() {
        whenever(repository.availableTypes(any()))
            .thenReturn(Observable.just(setOf(SharedItemType.FILE, SharedItemType.POLL)))
        whenever(repository.media(any(), eq(SharedItemType.FILE)))
            .thenReturn(Observable.just(page(SharedItemType.FILE, 1..3, lastSeenId = 1)))
        viewModel = SharedItemsViewModel(repository)
        viewModel.initialize(user, "roomToken")
    }

    @Test
    fun `next page is requested only once while a request is running`() {
        val nextPage = PublishSubject.create<SharedItems>()
        whenever(repository.media(any(), eq(SharedItemType.FILE), eq(1))).thenReturn(nextPage)

        viewModel.loadNextItems()
        viewModel.loadNextItems()
        nextPage.onNext(page(SharedItemType.FILE, 4..6, lastSeenId = 4))
        nextPage.onComplete()

        verify(repository, times(1)).media(any(), eq(SharedItemType.FILE), eq(1))
        assertEquals(listOf("3", "2", "1", "6", "5", "4"), loadedItemIds())
    }

    @Test
    fun `page of a previously selected type is discarded`() {
        val nextPage = PublishSubject.create<SharedItems>()
        whenever(repository.media(any(), eq(SharedItemType.FILE), eq(1))).thenReturn(nextPage)
        whenever(repository.media(any(), eq(SharedItemType.POLL)))
            .thenReturn(Observable.just(page(SharedItemType.POLL, 10..11, lastSeenId = 10)))

        viewModel.loadNextItems()
        viewModel.initialLoadItems(SharedItemType.POLL)
        nextPage.onNext(page(SharedItemType.FILE, 4..6, lastSeenId = 4))
        nextPage.onComplete()

        val state = viewModel.viewState.value as SharedItemsViewModel.LoadedState
        assertEquals(SharedItemType.POLL, state.selectedType)
        assertEquals(listOf("11", "10"), loadedItemIds())
    }

    @Test
    fun `removed item is not shown again after loading the next page`() {
        whenever(repository.media(any(), eq(SharedItemType.FILE), eq(1)))
            .thenReturn(Observable.just(page(SharedItemType.FILE, 4..4, lastSeenId = 4, moreItemsExisting = false)))
        val removed = (viewModel.viewState.value as SharedItemsViewModel.LoadedState).items.items.first()

        viewModel.removeItem(removed)
        viewModel.loadNextItems()

        assertEquals(listOf("2", "1", "4"), loadedItemIds())
        assertFalse((viewModel.viewState.value as SharedItemsViewModel.LoadedState).items.moreItemsExisting)
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
