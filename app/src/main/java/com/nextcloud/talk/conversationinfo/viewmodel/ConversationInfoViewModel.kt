/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo.viewmodel

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.generic.GenericMeta
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.TalkBan
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.repositories.conversations.ConversationsRepositoryImpl.Companion.STATUS_CODE_OK
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConversationInfoViewModel @Inject constructor(
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val conversationsRepository: ConversationsRepository
) : ViewModel() {

    object LifeCycleObserver : DefaultLifecycleObserver {
        enum class LifeCycleFlag {
            PAUSED,
            RESUMED
        }

        lateinit var currentLifeCycleFlag: LifeCycleFlag
        val disposableSet = mutableSetOf<Disposable>()

        override fun onResume(owner: LifecycleOwner) {
            super.onResume(owner)
            currentLifeCycleFlag = LifeCycleFlag.RESUMED
        }

        override fun onPause(owner: LifecycleOwner) {
            super.onPause(owner)
            currentLifeCycleFlag = LifeCycleFlag.PAUSED
            disposableSet.forEach { disposable -> disposable.dispose() }
            disposableSet.clear()
        }
    }

    sealed interface ViewState

    class ListBansSuccessState(val talkBans: List<TalkBan>) : ViewState
    object ListBansErrorState : ViewState

    private val _getTalkBanState: MutableLiveData<ViewState> = MutableLiveData()
    val getTalkBanState: LiveData<ViewState>
        get() = _getTalkBanState

    class BanActorSuccessState(val talkBan: TalkBan) : ViewState
    object BanActorErrorState : ViewState

    private val _getBanActorState: MutableLiveData<ViewState> = MutableLiveData()
    val getBanActorState: LiveData<ViewState>
        get() = _getBanActorState

    object UnBanActorSuccessState : ViewState
    object UnBanActorErrorState : ViewState

    private val _getUnBanActorState: MutableLiveData<ViewState> = MutableLiveData()
    val getUnBanActorState: LiveData<ViewState>
        get() = _getUnBanActorState

    object SetConversationReadOnlySuccessState : ViewState
    object SetConversationReadOnlyErrorState : ViewState

    private val _getConversationReadOnlyState: MutableLiveData<ViewState> = MutableLiveData()
    val getConversationReadOnlyState: LiveData<ViewState>
        get() = _getConversationReadOnlyState

    object GetRoomStartState : ViewState
    object GetRoomErrorState : ViewState
    open class GetRoomSuccessState(val conversationModel: ConversationModel) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomStartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    object GetCapabilitiesStartState : ViewState
    object GetCapabilitiesErrorState : ViewState
    open class GetCapabilitiesSuccessState(val spreedCapabilities: SpreedCapability) : ViewState

    private val _allowGuestsViewState = MutableLiveData<AllowGuestsUIState>(AllowGuestsUIState.None)
    val allowGuestsViewState: LiveData<AllowGuestsUIState>
        get() = _allowGuestsViewState

    private val _getCapabilitiesViewState: MutableLiveData<ViewState> = MutableLiveData(GetCapabilitiesStartState)
    val getCapabilitiesViewState: LiveData<ViewState>
        get() = _getCapabilitiesViewState

    fun getRoom(user: User, token: String) {
        _viewState.value = GetRoomStartState
        chatNetworkDataSource.getRoom(user, token)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetRoomObserver())
    }

    fun getCapabilities(user: User, token: String, conversationModel: ConversationModel) {
        _getCapabilitiesViewState.value = GetCapabilitiesStartState

        if (conversationModel.remoteServer.isNullOrEmpty()) {
            _getCapabilitiesViewState.value = GetCapabilitiesSuccessState(user.capabilities!!.spreedCapability!!)
        } else {
            chatNetworkDataSource.getCapabilities(user, token)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<SpreedCapability> {
                    override fun onSubscribe(d: Disposable) {
                        LifeCycleObserver.disposableSet.add(d)
                    }

                    override fun onNext(spreedCapabilities: SpreedCapability) {
                        _getCapabilitiesViewState.value = GetCapabilitiesSuccessState(spreedCapabilities)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Error when fetching spreed capabilities", e)
                        _getCapabilitiesViewState.value = GetCapabilitiesErrorState
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    fun listBans(user: User, token: String) {
        val url = ApiUtils.getUrlForBans(user.baseUrl!!, token)
        chatNetworkDataSource.listBans(user.getCredentials(), url)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<List<TalkBan>> {
                override fun onSubscribe(p0: Disposable) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    _getTalkBanState.value = ListBansErrorState
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(talkBans: List<TalkBan>) {
                    _getTalkBanState.value = ListBansSuccessState(talkBans)
                }
            })
    }

    fun banActor(user: User, token: String, actorType: String, actorId: String, internalNote: String) {
        val url = ApiUtils.getUrlForBans(user.baseUrl!!, token)
        chatNetworkDataSource.banActor(user.getCredentials(), url, actorType, actorId, internalNote)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<TalkBan> {
                override fun onSubscribe(p0: Disposable) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    _getBanActorState.value = BanActorErrorState
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(talkBan: TalkBan) {
                    _getBanActorState.value = BanActorSuccessState(talkBan)
                }
            })
    }

    fun setConversationReadOnly(user: User, token: String, state: Int) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForConversationReadOnly(apiVersion, user.baseUrl!!, token)
        conversationsRepository.setConversationReadOnly(user.getCredentials(), url, state)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(p0: Disposable) {
                }

                override fun onError(error: Throwable) {
                    _getConversationReadOnlyState.value = SetConversationReadOnlyErrorState
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(p0: GenericOverall) {
                    _getConversationReadOnlyState.value = SetConversationReadOnlySuccessState
                }
            })
    }

    fun unbanActor(user: User, token: String, banId: Int) {
        val url = ApiUtils.getUrlForUnban(user.baseUrl!!, token, banId)
        chatNetworkDataSource.unbanActor(user.getCredentials(), url)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(p0: Disposable) {
                    // unused atm
                }

                override fun onError(p0: Throwable) {
                    _getUnBanActorState.value = UnBanActorErrorState
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(p0: GenericOverall) {
                    _getUnBanActorState.value = UnBanActorSuccessState
                }
            })
    }


    fun allowGuests(token:String,allow:Boolean){
        viewModelScope.launch{
           try{
               val allowGuestsResult = conversationsRepository.allowGuests(token,allow)
               val statusCode: GenericMeta? = allowGuestsResult.ocs?.meta
               val result = (statusCode?.statusCode == STATUS_CODE_OK)
               if (result) {
                   _allowGuestsViewState.value = AllowGuestsUIState.Success(result)
               }
           }catch(exception:Exception){
               _allowGuestsViewState.value = AllowGuestsUIState.Error(exception.message?: "")

           }
        }
    }

    suspend fun archiveConversation(user: User, token: String) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForArchive(apiVersion, user.baseUrl, token)
        conversationsRepository.archiveConversation(user.getCredentials(), url)
    }

    suspend fun unarchiveConversation(user: User, token: String) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForArchive(apiVersion, user.baseUrl, token)
        conversationsRepository.unarchiveConversation(user.getCredentials(), url)
    }

    inner class GetRoomObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(conversationModel: ConversationModel) {
            _viewState.value = GetRoomSuccessState(conversationModel)
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when fetching room")
            _viewState.value = GetRoomErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = ConversationInfoViewModel::class.simpleName
    }

    sealed class AllowGuestsUIState {
        data object None : AllowGuestsUIState()
        data class Success(val result: Boolean) : AllowGuestsUIState()
        data class Error(val message: String) : AllowGuestsUIState()
    }
}
