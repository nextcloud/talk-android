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

package com.nextcloud.talk.chat.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.reminder.Reminder
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ChatViewModel @Inject constructor(private val repository: ChatRepository) :
    ViewModel() {

    sealed interface ViewState

    object GetRoomStartState : ViewState
    object GetRoomErrorState : ViewState
    object GetReminderStartState : ViewState
    open class GetReminderExistState(val reminder: Reminder) : ViewState

    private val _getReminderExistState: MutableLiveData<ViewState> = MutableLiveData(GetReminderStartState)
    val getReminderExistState: LiveData<ViewState>
        get() = _getReminderExistState

    open class GetRoomSuccessState(val conversationModel: ConversationModel) : ViewState

    private val _getRoomViewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomStartState)
    val getRoomViewState: LiveData<ViewState>
        get() = _getRoomViewState

    object JoinRoomStartState : ViewState
    object JoinRoomErrorState : ViewState
    open class JoinRoomSuccessState(val conversationModel: ConversationModel) : ViewState

    private val _joinRoomViewState: MutableLiveData<ViewState> = MutableLiveData(JoinRoomStartState)
    val joinRoomViewState: LiveData<ViewState>
        get() = _joinRoomViewState

    fun getRoom(user: User, token: String) {
        _getRoomViewState.value = GetRoomStartState
        repository.getRoom(user, token)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetRoomObserver())
    }

    fun joinRoom(user: User, token: String, roomPassword: String) {
        _getRoomViewState.value = JoinRoomStartState
        repository.joinRoom(user, token, roomPassword)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.retry(JOIN_ROOM_RETRY_COUNT)
            ?.subscribe(JoinRoomObserver())
    }

    fun setReminder(user: User, roomToken: String, messageId: String, timestamp: Int) {
        repository.setReminder(user, roomToken, messageId, timestamp)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(SetReminderObserver())
    }

    fun getReminder(user: User, roomToken: String, messageId: String) {
        repository.getReminder(user, roomToken, messageId)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetReminderObserver())
    }

    fun deleteReminder(user: User, roomToken: String, messageId: String) {
        repository.deleteReminder(user, roomToken, messageId)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(genericOverall: GenericOverall) {
                    _getReminderExistState.value = GetReminderStartState
                }

                override fun onError(e: Throwable) {
                    Log.d(TAG, "Error when deleting reminder $e")
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    inner class GetRoomObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(conversationModel: ConversationModel) {
            _getRoomViewState.value = GetRoomSuccessState(conversationModel)
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when fetching room")
            _getRoomViewState.value = GetRoomErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    inner class JoinRoomObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(conversationModel: ConversationModel) {
            _joinRoomViewState.value = JoinRoomSuccessState(conversationModel)
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when joining room")
            _joinRoomViewState.value = JoinRoomErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    inner class SetReminderObserver : Observer<Reminder> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(reminder: Reminder) {
            Log.d(TAG, "reminder set successfully")
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when sending reminder, $e")
        }

        override fun onComplete() {
            // unused atm
        }
    }

    inner class GetReminderObserver : Observer<Reminder> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(reminder: Reminder) {
            _getReminderExistState.value = GetReminderExistState(reminder)
        }

        override fun onError(e: Throwable) {
            Log.d(TAG, "Error when getting reminder $e")
            _getReminderExistState.value = GetReminderStartState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = ChatViewModel::class.simpleName
        const val JOIN_ROOM_RETRY_COUNT: Long = 3
    }
}
