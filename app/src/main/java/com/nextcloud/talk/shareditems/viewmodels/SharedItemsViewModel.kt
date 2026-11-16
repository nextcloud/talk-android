/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class SharedItemsViewModel @Inject constructor(private val repository: SharedItemsRepository) : ViewModel() {

    private lateinit var repositoryParameters: SharedItemsRepository.Parameters

    private var typesJob: Job? = null
    private var itemsJob: Job? = null

    sealed interface ViewState
    object InitialState : ViewState
    object TypesErrorState : ViewState
    object NoSharedItemsState : ViewState
    open class TypesLoadedState(val types: Set<SharedItemType>, val selectedType: SharedItemType) : ViewState
    class LoadingItemsState(types: Set<SharedItemType>, selectedType: SharedItemType) :
        TypesLoadedState(types, selectedType)

    class ItemsErrorState(types: Set<SharedItemType>, selectedType: SharedItemType) :
        TypesLoadedState(types, selectedType)

    enum class NextPageState { IDLE, LOADING, FAILED }

    /**
     * [nextPageState] tells whether the page following [items] is being loaded or failed to load.
     */
    class LoadedState(
        types: Set<SharedItemType>,
        selectedType: SharedItemType,
        val items: SharedItems,
        val nextPageState: NextPageState = NextPageState.IDLE
    ) : TypesLoadedState(types, selectedType)

    private val _viewState = MutableStateFlow<ViewState>(InitialState)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    /**
     * Starts loading the shared items. Requests that are running or have finished are kept, so a recreated activity
     * keeps the loaded pages and the selected type. Failed requests are repeated by [retry].
     */
    fun initialize(user: User, roomToken: String) {
        repositoryParameters = SharedItemsRepository.Parameters(
            // The login name, as it authenticates the requests. The user id can differ from it.
            user.username!!,
            user.token!!,
            user.baseUrl!!,
            roomToken
        )
        if (_viewState.value == InitialState && typesJob?.isActive != true) {
            loadAvailableTypes()
        }
    }

    /**
     * Repeats the request that failed: the available types, the first page of the selected type or the next page.
     */
    fun retry() {
        when (val state = _viewState.value) {
            TypesErrorState -> {
                _viewState.value = InitialState
                loadAvailableTypes()
            }
            is ItemsErrorState -> initialLoadItems(state.selectedType)
            is LoadedState -> if (state.nextPageState == NextPageState.FAILED) requestNextPage(state)
            else -> Unit
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadAvailableTypes() {
        typesJob = viewModelScope.launch {
            val types = try {
                repository.availableTypes(repositoryParameters)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "An error occurred when loading available types", e)
                _viewState.value = TypesErrorState
                return@launch
            }

            if (types.isEmpty()) {
                _viewState.value = NoSharedItemsState
            } else {
                val selectedType = chooseInitialType(types)
                _viewState.value = TypesLoadedState(types, selectedType)
                initialLoadItems(selectedType)
            }
        }
    }

    private fun chooseInitialType(newTypes: Set<SharedItemType>): SharedItemType =
        when {
            newTypes.contains(SharedItemType.MEDIA) -> SharedItemType.MEDIA
            else -> newTypes.toList().first()
        }

    fun initialLoadItems(type: SharedItemType) {
        val state = _viewState.value
        if (state is TypesLoadedState) {
            itemsJob?.cancel()
            _viewState.value = LoadingItemsState(state.types, type)
            itemsJob = viewModelScope.launch {
                val firstPage = loadItems(type, null)
                if (firstPage != null) {
                    onFirstPageLoaded(type, firstPage)
                } else {
                    onFirstPageFailed(type)
                }
            }
        }
    }

    /**
     * Whether [loadNextItems] would request a page: more items exist, and no request is running or has failed.
     */
    fun canLoadNextItems(): Boolean {
        val state = _viewState.value as? LoadedState ?: return false
        return state.items.moreItemsExisting &&
            state.items.lastSeenId != null &&
            state.nextPageState == NextPageState.IDLE
    }

    /**
     * Requests the page following the currently loaded items, if [canLoadNextItems]. After a failure, the next
     * page is only requested again by [retry].
     */
    fun loadNextItems() {
        val state = _viewState.value as? LoadedState ?: return
        if (canLoadNextItems()) {
            requestNextPage(state)
        }
    }

    private fun requestNextPage(state: LoadedState) {
        val cursor = state.items.lastSeenId ?: return
        setNextPageState(NextPageState.LOADING)
        itemsJob = viewModelScope.launch {
            val nextPage = loadItems(state.selectedType, cursor)
            if (nextPage != null) {
                onNextPageLoaded(state.selectedType, cursor, nextPage)
            } else {
                setNextPageState(NextPageState.FAILED)
            }
        }
    }

    private fun setNextPageState(nextPageState: NextPageState) {
        val state = _viewState.value as? LoadedState ?: return
        if (state.nextPageState != nextPageState) {
            _viewState.value = LoadedState(state.types, state.selectedType, state.items, nextPageState)
        }
    }

    /**
     * Loads the page after [cursor]. Pages without any displayable item are skipped while the server reports more,
     * as an unchanged list would not trigger another load from the UI.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadItems(type: SharedItemType, cursor: Int?): SharedItems? {
        var pageCursor = cursor
        while (true) {
            val page = try {
                repository.media(repositoryParameters, type, pageCursor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "An error occurred when loading shared items", e)
                return null
            }
            val nextCursor = page.lastSeenId
            if (page.items.isNotEmpty() || !page.moreItemsExisting || nextCursor == null || nextCursor == pageCursor) {
                return page
            }
            pageCursor = nextCursor
        }
    }

    fun removeItem(item: SharedItem) {
        val state = _viewState.value as? LoadedState ?: return
        val items = state.items
        _viewState.value = LoadedState(
            state.types,
            state.selectedType,
            SharedItems(items.items - item, items.type, items.lastSeenId, items.moreItemsExisting),
            state.nextPageState
        )
    }

    private fun onFirstPageLoaded(type: SharedItemType, firstPage: SharedItems) {
        val state = _viewState.value
        if (state is LoadingItemsState && state.selectedType == type) {
            _viewState.value = LoadedState(state.types, type, firstPage)
        }
    }

    private fun onFirstPageFailed(type: SharedItemType) {
        val state = _viewState.value
        if (state is LoadingItemsState && state.selectedType == type) {
            _viewState.value = ItemsErrorState(state.types, type)
        }
    }

    private fun onNextPageLoaded(type: SharedItemType, cursor: Int, nextPage: SharedItems) {
        val state = _viewState.value
        // Only append to the list the page was requested for, so a page can never be added twice
        // or end up in the list of another type.
        if (state is LoadedState && state.selectedType == type && state.items.lastSeenId == cursor) {
            _viewState.value = LoadedState(
                state.types,
                type,
                SharedItems(
                    state.items.items + nextPage.items,
                    state.items.type,
                    nextPage.lastSeenId,
                    nextPage.moreItemsExisting
                )
            )
        }
    }

    companion object {
        private val TAG = SharedItemsViewModel::class.simpleName
    }
}
