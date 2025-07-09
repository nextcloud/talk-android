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
            user.userId!!,
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
            _viewState.value = LoadingItemsState(state.types, type)
            repository.media(repositoryParameters, type)?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(SharedMediaItemsObserver())
        }
    }

    fun loadNextItems() {
        when (val currentState = _viewState.value) {
            is LoadedState -> {
                val currentSharedItems = currentState.items
                if (currentSharedItems.moreItemsExisting) {
                    repository.media(repositoryParameters, currentState.selectedType, currentSharedItems.lastSeenId)
                        ?.subscribeOn(Schedulers.io())
                        ?.observeOn(AndroidSchedulers.mainThread())
                        ?.subscribe(SharedMediaItemsObserver())
                }
            }
            else -> return
        }
    }

    inner class SharedMediaItemsObserver : Observer<SharedItems> {

        var newSharedItems: SharedItems? = null

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(response: SharedItems) {
            newSharedItems = response
        }

        override fun onError(e: Throwable) {
            Log.d(TAG, "An error occurred: $e")
        }

        override fun onComplete() {
            val items = newSharedItems!!
            val state = this@SharedItemsViewModel._viewState.value
            if (state is LoadedState) {
                val oldItems = state.items.items
                val newItems =
                    SharedItems(
                        oldItems + newSharedItems!!.items,
                        state.items.type,
                        newSharedItems!!.lastSeenId,
                        newSharedItems!!.moreItemsExisting
                    )
                setCurrentState(newItems)
            } else {
                setCurrentState(items)
            }
        }

        private fun setCurrentState(items: SharedItems) {
            when (val state = this@SharedItemsViewModel._viewState.value) {
                is TypesLoadedState -> {
                    this@SharedItemsViewModel._viewState.value = LoadedState(
                        state.types,
                        state.selectedType,
                        items
                    )
                }
                else -> return
            }
        }
    }

    companion object {
        private val TAG = SharedItemsViewModel::class.simpleName
    }
}
