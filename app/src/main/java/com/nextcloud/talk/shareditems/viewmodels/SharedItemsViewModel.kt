/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.shareditems.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedMediaItems
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepository
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class SharedItemsViewModel @Inject constructor(
    private val repository: SharedItemsRepository
) :
    ViewModel() {

    private lateinit var repositoryParameters: SharedItemsRepository.Parameters
    private lateinit var _currentItemType: SharedItemType
    val currentItemType: SharedItemType
        get() = _currentItemType

    // items
    sealed interface ViewState
    object InitialState : ViewState
    object NoSharedItemsState : ViewState
    open class TabsLoadedState(val types: Set<SharedItemType>) : ViewState
    class LoadedState(types: Set<SharedItemType>, val items: SharedMediaItems) : TabsLoadedState(types)

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun loadNextItems() {
        when (val currentState = _viewState.value) {
            is LoadedState -> {
                val currentSharedItems = currentState.items
                if (currentSharedItems.moreItemsExisting) {
                    repository.media(repositoryParameters, _currentItemType, currentSharedItems.lastSeenId)
                        ?.subscribeOn(Schedulers.io())
                        ?.observeOn(AndroidSchedulers.mainThread())
                        ?.subscribe(observer(_currentItemType, false))
                }
            }
            else -> return
        }
    }

    fun loadItems(type: SharedItemType) {

        _currentItemType = type

        repository.media(repositoryParameters, type)?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(observer(type, true))
    }

    private fun observer(type: SharedItemType, initModel: Boolean): Observer<SharedMediaItems> {
        return object : Observer<SharedMediaItems> {

            var newSharedItems: SharedMediaItems? = null

            override fun onSubscribe(d: Disposable) = Unit

            override fun onNext(response: SharedMediaItems) {
                newSharedItems = response
            }

            override fun onError(e: Throwable) {
                Log.d(TAG, "An error occurred: $e")
            }

            override fun onComplete() {
                val items = newSharedItems!!
                // todo replace initmodel with tabsloadedstate
                if (initModel) {
                    setCurrentState(items)
                } else {
                    val state = this@SharedItemsViewModel._viewState.value as LoadedState
                    val oldItems = state.items.items
                    val newItems =
                        SharedMediaItems(
                            oldItems + newSharedItems!!.items,
                            newSharedItems!!.lastSeenId,
                            newSharedItems!!.moreItemsExisting
                        )
                    setCurrentState(newItems)
                }
            }

            private fun setCurrentState(items: SharedMediaItems) {
                when (val state = this@SharedItemsViewModel._viewState.value) {
                    is TabsLoadedState -> {
                        this@SharedItemsViewModel._viewState.value = LoadedState(
                            state.types,
                            items
                        )
                    }
                    else -> return
                }
            }
        }
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
                    Log.d(TAG, "An error occurred: $e")
                }

                override fun onComplete() {
                    val newTypes = this.types
                    if (newTypes.isNullOrEmpty()) {
                        this@SharedItemsViewModel._viewState.value = NoSharedItemsState
                    } else {
                        this@SharedItemsViewModel._viewState.value = TabsLoadedState(newTypes)
                    }
                }
            })
    }

    // TODO cleanup
    fun initialize(userEntity: UserEntity, roomToken: String, initialType: SharedItemType) {
        repositoryParameters = SharedItemsRepository.Parameters(
            userEntity.userId,
            userEntity.token,
            userEntity.baseUrl,
            roomToken
        )
        _currentItemType = initialType
        loadAvailableTypes()
    }

    companion object {
        private val TAG = SharedItemsViewModel::class.simpleName
    }
}
