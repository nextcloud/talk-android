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

    private val _sharedItemTypes: MutableLiveData<Set<SharedItemType>> by lazy {
        MutableLiveData<Set<SharedItemType>>().also {
            availableTypes()
        }
    }

    private val _sharedItems: MutableLiveData<SharedMediaItems> by lazy {
        MutableLiveData<SharedMediaItems>().also {
            loadItems(_currentItemType)
        }
    }

    private lateinit var repositoryParameters: SharedItemsRepository.Parameters
    private lateinit var _currentItemType: SharedItemType

    val sharedItemTypes: LiveData<Set<SharedItemType>>
        get() = _sharedItemTypes

    val sharedItems: LiveData<SharedMediaItems>
        get() = _sharedItems

    val currentItemType: SharedItemType
        get() = _currentItemType

    fun loadNextItems() {
        val currentSharedItems = sharedItems.value!!

        if (currentSharedItems.moreItemsExisting) {
            repository.media(repositoryParameters, _currentItemType, currentSharedItems.lastSeenId)
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(observer(_currentItemType, false))
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
                if (initModel) {
                    this@SharedItemsViewModel._sharedItems.value =
                        newSharedItems
                } else {
                    val oldItems = this@SharedItemsViewModel._sharedItems.value!!.items
                    this@SharedItemsViewModel._sharedItems.value =
                        SharedMediaItems(
                            oldItems + newSharedItems!!.items,
                            newSharedItems!!.lastSeenId,
                            newSharedItems!!.moreItemsExisting
                        )
                }
            }
        }
    }

    private fun availableTypes() {
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
                    this@SharedItemsViewModel._sharedItemTypes.value = this.types
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
    }

    companion object {
        private val TAG = SharedItemsViewModel::class.simpleName
    }
}
