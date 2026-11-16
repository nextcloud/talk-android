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
    object NoSharedItemsState : ViewState
    open class TypesLoadedState(val types: Set<SharedItemType>, val selectedType: SharedItemType) : ViewState
    class LoadingItemsState(types: Set<SharedItemType>, selectedType: SharedItemType) :
        TypesLoadedState(types, selectedType)

    /**
     * [isLoadingMore] is true while the page following [items] is being loaded.
     */
    class LoadedState(
        types: Set<SharedItemType>,
        selectedType: SharedItemType,
        val items: SharedItems,
        val isLoadingMore: Boolean = false
    ) : TypesLoadedState(types, selectedType)

    private val _viewState = MutableStateFlow<ViewState>(InitialState)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    /**
     * Starts loading the shared items, or resumes a load that failed. Requests that are running or have succeeded
     * are kept, so a recreated activity keeps the loaded pages and the selected type.
     */
    fun initialize(user: User, roomToken: String) {
        repositoryParameters = SharedItemsRepository.Parameters(
            // The login name, as it authenticates the requests. The user id can differ from it.
            user.username!!,
            user.token!!,
            user.baseUrl!!,
            roomToken
        )
        when (val state = _viewState.value) {
            InitialState -> if (typesJob?.isActive != true) loadAvailableTypes()
            is LoadingItemsState -> if (itemsJob?.isActive != true) initialLoadItems(state.selectedType)
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
                loadItems(type, null)?.let { firstPage -> onFirstPageLoaded(type, firstPage) }
            }
        }
    }

    /**
     * Requests the page following the currently loaded items. Calls while a request is already running, or when
     * the server reported no further items, are ignored.
     */
    fun loadNextItems() {
        val state = _viewState.value as? LoadedState ?: return
        val cursor = state.items.lastSeenId
        if (!state.items.moreItemsExisting || cursor == null || itemsJob?.isActive == true) {
            return
        }
        setLoadingMore(true)
        itemsJob = viewModelScope.launch {
            loadItems(state.selectedType, cursor)?.let { nextPage ->
                onNextPageLoaded(state.selectedType, cursor, nextPage)
            }
            setLoadingMore(false)
        }
    }

    private fun setLoadingMore(isLoadingMore: Boolean) {
        val state = _viewState.value as? LoadedState ?: return
        if (state.isLoadingMore != isLoadingMore) {
            _viewState.value = LoadedState(state.types, state.selectedType, state.items, isLoadingMore)
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
            state.isLoadingMore
        )
    }

    private fun onFirstPageLoaded(type: SharedItemType, firstPage: SharedItems) {
        val state = _viewState.value
        if (state is LoadingItemsState && state.selectedType == type) {
            _viewState.value = LoadedState(state.types, type, firstPage)
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
