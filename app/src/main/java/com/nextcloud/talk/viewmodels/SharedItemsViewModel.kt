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

class SharedItemsViewModel(private val repository: SharedItemsRepository) : ViewModel() {

    private val _media: MutableLiveData<SharedMediaItems> by lazy {
        MutableLiveData<SharedMediaItems>().also {
            loadMediaItems()
        }
    }

    val media: LiveData<SharedMediaItems>
        get() = _media

    private fun loadMediaItems() {

        repository.media()?.subscribeOn(Schedulers.io())
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
                        val fileParameters = it.value.messageParameters["file"]!!

                        val previewAvailable = "yes".equals(fileParameters["preview-available"]!!, ignoreCase = true)

                        items[it.value.id] = SharedItem(
                            fileParameters["id"]!!, fileParameters["name"]!!,
                            fileParameters["mimetype"]!!, fileParameters["link"]!!,
                            previewAvailable,
                            repository.previewLink(fileParameters["id"])
                        )
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

    class Factory(val userEntity: UserEntity, val roomToken: String) : ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SharedItemsViewModel::class.java)) {

                val repository = SharedItemsRepository()
                repository.parameters = SharedItemsRepository.Parameters(
                    userEntity.userId,
                    userEntity.token,
                    userEntity.baseUrl,
                    roomToken
                )

                return SharedItemsViewModel(repository) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private val TAG = SharedItemsViewModel::class.simpleName
    }
}
