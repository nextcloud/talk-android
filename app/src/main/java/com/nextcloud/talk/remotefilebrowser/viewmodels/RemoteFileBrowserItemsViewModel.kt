/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 202 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.remotefilebrowser.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.remotefilebrowser.repositories.RemoteFileBrowserItemsRepository
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class RemoteFileBrowserItemsViewModel @Inject constructor(
    private val repository: RemoteFileBrowserItemsRepository
) :
    ViewModel() {

    sealed interface ViewState
    object InitialState : ViewState
    object NoRemoteFileItemsState : ViewState
    object LoadingItemsState : ViewState
    class LoadedState(val items: List<RemoteFileBrowserItem>) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)

    val viewState: LiveData<ViewState>
        get() = _viewState

    fun loadItems(path: String) {
        _viewState.value = LoadingItemsState
        repository.listFolder(path).subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(RemoteFileBrowserItemsObserver())
    }

    inner class RemoteFileBrowserItemsObserver : Observer<List<RemoteFileBrowserItem>> {

        var newRemoteFileBrowserItems: List<RemoteFileBrowserItem>? = null

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(response: List<RemoteFileBrowserItem>) {
            newRemoteFileBrowserItems = response
        }

        override fun onError(e: Throwable) {
            Log.d(TAG, "An error occurred: $e")
        }

        override fun onComplete() {
            if (newRemoteFileBrowserItems.isNullOrEmpty()) {
                this@RemoteFileBrowserItemsViewModel._viewState.value = NoRemoteFileItemsState
            } else {
                setCurrentState(newRemoteFileBrowserItems!!)
            }
        }

        private fun setCurrentState(items: List<RemoteFileBrowserItem>) {
            when (this@RemoteFileBrowserItemsViewModel._viewState.value) {
                is LoadedState, LoadingItemsState -> {
                    this@RemoteFileBrowserItemsViewModel._viewState.value = LoadedState(items)
                }
                else -> return
            }
        }
    }

    companion object {
        private val TAG = RemoteFileBrowserItemsViewModel::class.simpleName
    }
}
