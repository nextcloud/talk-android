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

class SharedItemsViewModel(private val repository: SharedItemsRepository, val initialType: String) : ViewModel() {

    private val _media: MutableLiveData<SharedMediaItems> by lazy {
        MutableLiveData<SharedMediaItems>().also {
            loadMediaItems(initialType)
        }
    }

    val media: LiveData<SharedMediaItems>
        get() = _media

    fun loadMediaItems(type: String) {

        repository.media(type)?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<Response<ChatShareOverall>> {

                var chatLastGiven: String = ""
                val items = mutableMapOf<String, SharedItem>()

                override fun onSubscribe(d: Disposable) = Unit

                override fun onNext(response: Response<ChatShareOverall>) {

                    if (response.headers()["x-chat-last-given"] != null) {
                        chatLastGiven = response.headers()["x-chat-last-given"]!!
                    }

                    val mediaItems = response.body()!!.ocs!!.data
                    mediaItems?.forEach {
                        if (it.value.messageParameters.containsKey("file")) {
                            val fileParameters = it.value.messageParameters["file"]!!

                            val previewAvailable = "yes".equals(fileParameters["preview-available"]!!, ignoreCase = true)

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

                override fun onError(e: Throwable) {
                    Log.d(TAG, "An error occurred: $e")
                }

                override fun onComplete() {
                    this@SharedItemsViewModel._media.value =
                        SharedMediaItems(
                            items.toSortedMap().values.toList().reversed(),
                            chatLastGiven,
                            repository.authHeader()
                        )
                }
            })
    }

    class Factory(val userEntity: UserEntity, val roomToken: String, private val initialType: String) : ViewModelProvider
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
