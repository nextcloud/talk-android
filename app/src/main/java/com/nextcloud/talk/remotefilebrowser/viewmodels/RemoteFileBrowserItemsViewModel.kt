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
import com.nextcloud.talk.utils.FileSortOrderNew
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener
import java.io.File
import javax.inject.Inject

class RemoteFileBrowserItemsViewModel @Inject constructor(
    private val repository: RemoteFileBrowserItemsRepository,
    private val appPreferences: AppPreferences
) :
    ViewModel() {

    sealed interface ViewState
    object InitialState : ViewState
    object NoRemoteFileItemsState : ViewState
    object LoadingItemsState : ViewState
    class LoadedState(val items: List<RemoteFileBrowserItem>) : ViewState

    private val initialSortOrder = FileSortOrderNew.getFileSortOrder(appPreferences.sorting)
    private val sortingPrefListener: SortChangeListener = SortChangeListener()

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    // TODO incorporate into view state object?
    private val _fileSortOrder: MutableLiveData<FileSortOrderNew> = MutableLiveData(initialSortOrder)
    val fileSortOrder: LiveData<FileSortOrderNew>
        get() = _fileSortOrder

    private val _currentPath: MutableLiveData<String> = MutableLiveData(ROOT_PATH)
    val currentPath: LiveData<String>
        get() = _currentPath

    init {
        appPreferences.registerSortingChangeListener(sortingPrefListener)
    }

    inner class SortChangeListener : OnPreferenceValueChangedListener<String> {
        override fun onChanged(newValue: String) {
            onSelectSortOrder(newValue)
        }
    }

    override fun onCleared() {
        super.onCleared()
        appPreferences.unregisterSortingChangeListener(sortingPrefListener)
    }

    fun loadItems() {
        _viewState.value = LoadingItemsState
        repository.listFolder(currentPath.value!!).subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(RemoteFileBrowserItemsObserver())
    }

    inner class RemoteFileBrowserItemsObserver : Observer<List<RemoteFileBrowserItem>> {

        var newRemoteFileBrowserItems: List<RemoteFileBrowserItem>? = null

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(response: List<RemoteFileBrowserItem>) {
            val itemsWithoutRoot = response.filterNot { it.mimeType == MIME_DIRECTORY && it.path == ROOT_PATH }
            newRemoteFileBrowserItems = fileSortOrder.value!!.sortCloudFiles(itemsWithoutRoot)
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

    private fun onSelectSortOrder(newSortOrderString: String) {
        val newSortOrder = FileSortOrderNew.getFileSortOrder(newSortOrderString)
        if (newSortOrder.name != fileSortOrder.value?.name) {
            _fileSortOrder.value = newSortOrder
            val currentState = viewState.value
            if (currentState is LoadedState) {
                val sortedItems = newSortOrder.sortCloudFiles(currentState.items)
                _viewState.value = LoadedState(sortedItems)
            }
        }
    }

    fun changePath(path: String) {
        _currentPath.value = path
        loadItems()
    }

    fun navigateUp() {
        val path = _currentPath.value
        if (path!! != ROOT_PATH) {
            _currentPath.value = File(path).parent!!
            loadItems()
        }
    }

    companion object {
        private val TAG = RemoteFileBrowserItemsViewModel::class.simpleName
        private const val ROOT_PATH = "/"
        private const val MIME_DIRECTORY = "inode/directory"
    }
}
