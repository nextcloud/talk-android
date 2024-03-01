/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.conversationinfo.viewmodel

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ConversationInfoViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    object LifeCycleObserver : DefaultLifecycleObserver {
        enum class LifeCycleFlag {
            PAUSED,
            RESUMED
        }
        lateinit var currentLifeCycleFlag: LifeCycleFlag
        public val disposableSet = mutableSetOf<Disposable>()

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

    object GetRoomStartState : ViewState
    object GetRoomErrorState : ViewState
    open class GetRoomSuccessState(val conversationModel: ConversationModel) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomStartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    object GetCapabilitiesStartState : ViewState
    object GetCapabilitiesErrorState : ViewState
    open class GetCapabilitiesSuccessState(val spreedCapabilities: SpreedCapability) : ViewState

    private val _getCapabilitiesViewState: MutableLiveData<ViewState> = MutableLiveData(GetCapabilitiesStartState)
    val getCapabilitiesViewState: LiveData<ViewState>
        get() = _getCapabilitiesViewState

    fun getRoom(user: User, token: String) {
        _viewState.value = GetRoomStartState
        chatRepository.getRoom(user, token)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetRoomObserver())
    }

    fun getCapabilities(user: User, token: String, conversationModel: ConversationModel) {
        _getCapabilitiesViewState.value = GetCapabilitiesStartState

        if (conversationModel.remoteServer.isNullOrEmpty()) {
            _getCapabilitiesViewState.value = GetCapabilitiesSuccessState(user.capabilities!!.spreedCapability!!)
        } else {
            chatRepository.getCapabilities(user, token)
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
}
