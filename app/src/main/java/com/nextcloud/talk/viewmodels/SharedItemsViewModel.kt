package com.nextcloud.talk.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.repositories.SharedItemType
import com.nextcloud.talk.repositories.SharedItemsRepository
import com.nextcloud.talk.repositories.SharedMediaItems
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class SharedItemsViewModel(
    private val repository: SharedItemsRepository,
    private val initialType: SharedItemType,
    private val repositoryParameters: SharedItemsRepository.Parameters
) :
    ViewModel() {

    private val _sharedItemType: MutableLiveData<Set<SharedItemType>> by lazy {
        MutableLiveData<Set<SharedItemType>>().also {
            availableTypes()
        }
    }

    private val _sharedItems: MutableLiveData<SharedMediaItems> by lazy {
        MutableLiveData<SharedMediaItems>().also {
            loadItems(initialType)
        }
    }

    val sharedItemType: LiveData<Set<SharedItemType>>
        get() = _sharedItemType

    val sharedItems: LiveData<SharedMediaItems>
        get() = _sharedItems

    fun loadNextItems() {
        val currentSharedItems = sharedItems.value!!

        if (currentSharedItems.moreItemsExisting) {
            repository.media(repositoryParameters, currentSharedItems.type, currentSharedItems.lastSeenId)
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(observer(currentSharedItems.type, false))
        }
    }

    fun loadItems(type: SharedItemType) {
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
                            type,
                            oldItems + newSharedItems!!.items,
                            newSharedItems!!.lastSeenId,
                            newSharedItems!!.moreItemsExisting,
                            newSharedItems!!.authHeader
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
                    this@SharedItemsViewModel._sharedItemType.value = this.types
                }
            })
    }

    class Factory(val userEntity: UserEntity, val roomToken: String, private val initialType: SharedItemType) :
        ViewModelProvider
        .Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SharedItemsViewModel::class.java)) {

                val repository = SharedItemsRepository()
                val repositoryParameters = SharedItemsRepository.Parameters(
                    userEntity.userId,
                    userEntity.token,
                    userEntity.baseUrl,
                    userEntity,
                    roomToken
                )

                return SharedItemsViewModel(repository, initialType, repositoryParameters) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private val TAG = SharedItemsViewModel::class.simpleName
        const val BATCH_SIZE: Int = 28
    }
}
