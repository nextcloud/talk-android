package com.nextcloud.talk.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.chat.ChatShareOverall
import com.nextcloud.talk.repositories.SharedItem
import com.nextcloud.talk.repositories.SharedItemsRepository
import com.nextcloud.talk.repositories.SharedMediaItems
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import retrofit2.Response

class SharedItemsViewModel(private val repository: SharedItemsRepository, private val initialType: String) :
    ViewModel() {

    private val _sharedItems: MutableLiveData<SharedMediaItems> by lazy {
        MutableLiveData<SharedMediaItems>().also {
            loadItems(initialType)
        }
    }

    val sharedItems: LiveData<SharedMediaItems>
        get() = _sharedItems

    fun loadNextItems() {
        val currentSharedItems = sharedItems.value!!

        if (currentSharedItems.moreItemsExisting) {
            repository.media(currentSharedItems.type, currentSharedItems.lastSeenId)?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(observer(currentSharedItems.type, false))
        }
    }

    fun loadItems(type: String) {
        repository.media(type)?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(observer(type, true))
    }

    private fun observer(type: String, initModel: Boolean): Observer<Response<ChatShareOverall>> {
        return object : Observer<Response<ChatShareOverall>> {

            var chatLastGiven: Int? = null
            val items = mutableMapOf<String, SharedItem>()

            override fun onSubscribe(d: Disposable) = Unit

            override fun onNext(response: Response<ChatShareOverall>) {

                if (response.headers()["x-chat-last-given"] != null) {
                    chatLastGiven = response.headers()["x-chat-last-given"]!!.toInt()
                }

                val mediaItems = response.body()!!.ocs!!.data
                if (mediaItems != null) {
                    for (it in mediaItems) {
                        if (it.value.messageParameters.containsKey("file")) {
                            val fileParameters = it.value.messageParameters["file"]!!

                            val previewAvailable =
                                "yes".equals(fileParameters["preview-available"]!!, ignoreCase = true)

                            items[it.value.id] = SharedItem(
                                fileParameters["id"]!!,
                                fileParameters["name"]!!,
                                fileParameters["size"]!!.toInt(),
                                fileParameters["path"]!!,
                                fileParameters["link"]!!,
                                fileParameters["mimetype"]!!,
                                previewAvailable,
                                repository.previewLink(fileParameters["id"]),
                                repository.parameters!!.userEntity
                            )
                        } else {
                            Log.w(TAG, "location and deckcard are not yet supported")
                        }
                    }
                }
            }

            override fun onError(e: Throwable) {
                Log.d(TAG, "An error occurred: $e")
            }

            override fun onComplete() {

                val sortedMutableItems = items.toSortedMap().values.toList().reversed().toMutableList()
                val moreItemsExisting = items.count() == 28

                if (initModel) {
                    this@SharedItemsViewModel._sharedItems.value =
                        SharedMediaItems(
                            type,
                            sortedMutableItems,
                            chatLastGiven,
                            moreItemsExisting,
                            repository.authHeader()
                        )
                } else {
                    val oldItems = this@SharedItemsViewModel._sharedItems.value!!.items
                    this@SharedItemsViewModel._sharedItems.value =
                        SharedMediaItems(
                            type,
                            (oldItems.toMutableList() + sortedMutableItems) as MutableList<SharedItem>,
                            chatLastGiven,
                            moreItemsExisting,
                            repository.authHeader()
                        )
                }
            }
        }
    }

    class Factory(val userEntity: UserEntity, val roomToken: String, private val initialType: String) :
        ViewModelProvider
        .Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SharedItemsViewModel::class.java)) {

                val repository = SharedItemsRepository()
                repository.parameters = SharedItemsRepository.Parameters(
                    userEntity.userId,
                    userEntity.token,
                    userEntity.baseUrl,
                    userEntity,
                    roomToken
                )

                return SharedItemsViewModel(repository, initialType) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private val TAG = SharedItemsViewModel::class.simpleName
    }
}
