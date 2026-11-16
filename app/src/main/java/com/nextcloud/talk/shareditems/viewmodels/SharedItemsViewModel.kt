/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepository
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class SharedItemsViewModel @Inject constructor(private val repository: SharedItemsRepository) : ViewModel() {

    private lateinit var repositoryParameters: SharedItemsRepository.Parameters

    private var itemsDisposable: Disposable? = null

    sealed interface ViewState
    object InitialState : ViewState
    object NoSharedItemsState : ViewState
    open class TypesLoadedState(val types: Set<SharedItemType>, val selectedType: SharedItemType) : ViewState
    class LoadingItemsState(types: Set<SharedItemType>, selectedType: SharedItemType) :
        TypesLoadedState(types, selectedType)

    class LoadedState(types: Set<SharedItemType>, selectedType: SharedItemType, val items: SharedItems) :
        TypesLoadedState(types, selectedType)

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun initialize(user: User, roomToken: String) {
        repositoryParameters = SharedItemsRepository.Parameters(
            // The login name, as it authenticates the requests. The user id can differ from it.
            user.username!!,
            user.token!!,
            user.baseUrl!!,
            roomToken
        )
        loadAvailableTypes()
    }

    private fun loadAvailableTypes() {
        repository.availableTypes(repositoryParameters).subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<Set<SharedItemType>> {

                var types: Set<SharedItemType>? = null

                override fun onSubscribe(d: Disposable) = Unit

                override fun onNext(types: Set<SharedItemType>) {
                    this.types = types
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "An error occurred when loading available types", e)
                }

                override fun onComplete() {
                    val newTypes = this.types
                    if (newTypes.isNullOrEmpty()) {
                        this@SharedItemsViewModel._viewState.value = NoSharedItemsState
                    } else {
                        val selectedType = chooseInitialType(newTypes)
                        this@SharedItemsViewModel._viewState.value =
                            TypesLoadedState(newTypes, selectedType)
                        initialLoadItems(selectedType)
                    }
                }
            })
    }

    private fun chooseInitialType(newTypes: Set<SharedItemType>): SharedItemType =
        when {
            newTypes.contains(SharedItemType.MEDIA) -> SharedItemType.MEDIA
            else -> newTypes.toList().first()
        }

    fun initialLoadItems(type: SharedItemType) {
        val state = _viewState.value
        if (state is TypesLoadedState) {
            itemsDisposable?.dispose()
            _viewState.value = LoadingItemsState(state.types, type)
            itemsDisposable = repository.media(repositoryParameters, type)
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(
                    { firstPage -> onFirstPageLoaded(type, firstPage) },
                    { e -> onItemsLoadingFailed(e) }
                )
        }
    }

    /**
     * Requests the page following the currently loaded items. Calls while a request is already running, or when
     * the server reported no further items, are ignored.
     */
    fun loadNextItems() {
        val state = _viewState.value as? LoadedState ?: return
        val cursor = state.items.lastSeenId
        if (!state.items.moreItemsExisting || cursor == null || itemsDisposable?.isDisposed == false) {
            return
        }
        itemsDisposable = repository.media(repositoryParameters, state.selectedType, cursor)
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(
                { nextPage -> onNextPageLoaded(state.selectedType, cursor, nextPage) },
                { e -> onItemsLoadingFailed(e) }
            )
    }

    fun removeItem(item: SharedItem) {
        val state = _viewState.value as? LoadedState ?: return
        val items = state.items
        _viewState.value = LoadedState(
            state.types,
            state.selectedType,
            SharedItems(items.items - item, items.type, items.lastSeenId, items.moreItemsExisting)
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

    private fun onItemsLoadingFailed(e: Throwable) {
        Log.e(TAG, "An error occurred when loading shared items", e)
    }

    override fun onCleared() {
        itemsDisposable?.dispose()
        super.onCleared()
    }

    companion object {
        private val TAG = SharedItemsViewModel::class.simpleName
    }
}
