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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.reminder.Reminder
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.utils.ConversationUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import retrofit2.Response
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongParameterList")
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val reactionsRepository: ReactionsRepository
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

    private val _getFieldMapForChat: MutableLiveData<HashMap<String, Int>> = MutableLiveData()
    val getFieldMapForChat: LiveData<HashMap<String, Int>>
        get() = _getFieldMapForChat
    sealed interface ViewState

    object GetReminderStartState : ViewState
    open class GetReminderExistState(val reminder: Reminder) : ViewState

    private val _getReminderExistState: MutableLiveData<ViewState> = MutableLiveData(GetReminderStartState)

    var isPausedDueToBecomingNoisy = false
    var receiverRegistered = false
    var receiverUnregistered = false

    val getReminderExistState: LiveData<ViewState>
        get() = _getReminderExistState

    object NoteToSelfNotAvaliableState : ViewState
    open class NoteToSelfAvaliableState(val roomToken: String) : ViewState

    private val _getNoteToSelfAvaliability: MutableLiveData<ViewState> = MutableLiveData(NoteToSelfNotAvaliableState)
    val getNoteToSelfAvaliability: LiveData<ViewState>
        get() = _getNoteToSelfAvaliability

    object GetRoomStartState : ViewState
    object GetRoomErrorState : ViewState
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

    object LeaveRoomStartState : ViewState
    class LeaveRoomSuccessState(val funToCallWhenLeaveSuccessful: (() -> Unit)?) : ViewState
    private val _leaveRoomViewState: MutableLiveData<ViewState> = MutableLiveData(LeaveRoomStartState)
    val leaveRoomViewState: LiveData<ViewState>
        get() = _leaveRoomViewState

    object SendChatMessageStartState : ViewState
    class SendChatMessageSuccessState(val message: CharSequence) : ViewState
    class SendChatMessageErrorState(val e: Throwable, val message: CharSequence) : ViewState
    private val _sendChatMessageViewState: MutableLiveData<ViewState> = MutableLiveData(SendChatMessageStartState)
    val sendChatMessageViewState: LiveData<ViewState>
        get() = _sendChatMessageViewState

    object PullChatMessageStartState : ViewState
    class PullChatMessageSuccessState(val response: Response<*>, val lookIntoFuture: Boolean) : ViewState
    object PullChatMessageErrorState : ViewState
    object PullChatMessageCompleteState : ViewState
    private val _pullChatMessageViewState: MutableLiveData<ViewState> = MutableLiveData(PullChatMessageStartState)
    val pullChatMessageViewState: LiveData<ViewState>
        get() = _pullChatMessageViewState

    object DeleteChatMessageStartState : ViewState
    class DeleteChatMessageSuccessState(val msg: ChatOverallSingleMessage) : ViewState
    object DeleteChatMessageErrorState : ViewState
    private val _deleteChatMessageViewState: MutableLiveData<ViewState> = MutableLiveData(DeleteChatMessageStartState)
    val deleteChatMessageViewState: LiveData<ViewState>
        get() = _deleteChatMessageViewState

    object CreateRoomStartState : ViewState
    object CreateRoomErrorState : ViewState
    class CreateRoomSuccessState(val roomOverall: RoomOverall) : ViewState

    private val _createRoomViewState: MutableLiveData<ViewState> = MutableLiveData(CreateRoomStartState)
    val createRoomViewState: LiveData<ViewState>
        get() = _createRoomViewState

    object ReactionAddedStartState : ViewState
    class ReactionAddedSuccessState(val reactionAddedModel: ReactionAddedModel) : ViewState
    private val _reactionAddedViewState: MutableLiveData<ViewState> = MutableLiveData(ReactionAddedStartState)
    val reactionAddedViewState: LiveData<ViewState>
        get() = _reactionAddedViewState

    object ReactionDeletedStartState : ViewState
    class ReactionDeletedSuccessState(val reactionDeletedModel: ReactionDeletedModel) : ViewState
    private val _reactionDeletedViewState: MutableLiveData<ViewState> = MutableLiveData(ReactionDeletedStartState)
    val reactionDeletedViewState: LiveData<ViewState>
        get() = _reactionDeletedViewState

    object EditMessageStartState : ViewState
    object EditMessageErrorState : ViewState
    class EditMessageSuccessState(val messageEdited: ChatOverallSingleMessage) : ViewState

    private val _editMessageViewState: MutableLiveData<ViewState> = MutableLiveData(EditMessageStartState)
    val editMessageViewState: LiveData<ViewState>
        get() = _editMessageViewState

    fun refreshChatParams(pullChatMessagesFieldMap: HashMap<String, Int>) {
        if (pullChatMessagesFieldMap != _getFieldMapForChat.value) {
            _getFieldMapForChat.postValue(pullChatMessagesFieldMap)
            Log.d(TAG, "FieldMap Refreshed with $pullChatMessagesFieldMap vs ${_getFieldMapForChat.value}")
        }
    }

    fun getRoom(user: User, token: String) {
        _getRoomViewState.value = GetRoomStartState
        chatRepository.getRoom(user, token)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetRoomObserver())
    }

    fun joinRoom(user: User, token: String, roomPassword: String) {
        _joinRoomViewState.value = JoinRoomStartState
        chatRepository.joinRoom(user, token, roomPassword)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.retry(JOIN_ROOM_RETRY_COUNT)
            ?.subscribe(JoinRoomObserver())
    }

    fun leaveRoom(credentials: String, url: String, funToCallWhenLeaveSuccessful: (() -> Unit)?) {
        val startNanoTime = System.nanoTime()
        chatRepository.leaveRoom(credentials, url)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "leaveRoom - leaveRoom - ERROR", e)
                }

                override fun onComplete() {
                    Log.d(TAG, "leaveRoom - leaveRoom - completed: $startNanoTime")
                }

                override fun onNext(t: GenericOverall) {
                    _leaveRoomViewState.value = LeaveRoomSuccessState(funToCallWhenLeaveSuccessful)
                }
            })
    }

    fun createRoom(credentials: String, url: String, queryMap: Map<String, String>) {
        chatRepository.createRoom(credentials, url, queryMap)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    _createRoomViewState.value = CreateRoomErrorState
                    Log.e(TAG, e.message, e)
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(t: RoomOverall) {
                    _createRoomViewState.value = CreateRoomSuccessState(t)
                }
            })
    }

    fun sendChatMessage(
        credentials: String,
        url: String,
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean
    ) {
        chatRepository.sendChatMessage(
            credentials,
            url,
            message,
            displayName,
            replyTo,
            sendWithoutNotification
        ).subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    _sendChatMessageViewState.value = SendChatMessageErrorState(e, message)
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(t: GenericOverall) {
                    _sendChatMessageViewState.value = SendChatMessageSuccessState(message)
                }
            })
    }

    fun pullChatMessages(credentials: String, url: String) {
        chatRepository.pullChatMessages(credentials, url, _getFieldMapForChat.value!!)
            .subscribeOn(Schedulers.io())
            .takeUntil { (LifeCycleObserver.currentLifeCycleFlag == LifeCycleObserver.LifeCycleFlag.PAUSED) }
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<Response<*>> {
                override fun onSubscribe(d: Disposable) {
                    Log.d(TAG, "pullChatMessages - pullChatMessages SUBSCRIBE")
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "pullChatMessages - pullChatMessages ERROR", e)
                    _pullChatMessageViewState.value = PullChatMessageErrorState
                }

                override fun onComplete() {
                    Log.d(TAG, "pullChatMessages - pullChatMessages COMPLETE")
                    _pullChatMessageViewState.value = PullChatMessageCompleteState
                }

                override fun onNext(response: Response<*>) {
                    val lookIntoFuture = getFieldMapForChat.value?.get("lookIntoFuture") == 1
                    _pullChatMessageViewState.value = PullChatMessageSuccessState(response, lookIntoFuture)
                }
            })
    }

    fun deleteChatMessages(credentials: String, url: String, messageId: String) {
        chatRepository.deleteChatMessage(credentials, url)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ChatOverallSingleMessage> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(
                        TAG,
                        "Something went wrong when trying to delete message with id " +
                            messageId,
                        e
                    )
                    _deleteChatMessageViewState.value = DeleteChatMessageErrorState
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(t: ChatOverallSingleMessage) {
                    _deleteChatMessageViewState.value = DeleteChatMessageSuccessState(t)
                }
            })
    }

    fun setChatReadMarker(credentials: String, url: String, previousMessageId: Int) {
        chatRepository.setChatReadMarker(credentials, url, previousMessageId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, e.message, e)
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(t: GenericOverall) {
                    // unused atm
                }
            })
    }

    fun setReminder(user: User, roomToken: String, messageId: String, timestamp: Int) {
        chatRepository.setReminder(user, roomToken, messageId, timestamp)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(SetReminderObserver())
    }

    fun getReminder(user: User, roomToken: String, messageId: String) {
        chatRepository.getReminder(user, roomToken, messageId)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetReminderObserver())
    }

    fun deleteReminder(user: User, roomToken: String, messageId: String) {
        chatRepository.deleteReminder(user, roomToken, messageId)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
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

    fun shareToNotes(credentials: String, url: String, message: String, displayName: String) {
        chatRepository.shareToNotes(credentials, url, message, displayName)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onNext(genericOverall: GenericOverall) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    Log.d(TAG, "Error when sharing to notes $e")
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    fun checkForNoteToSelf(credentials: String, baseUrl: String, includeStatus: Boolean) {
        chatRepository.checkForNoteToSelf(credentials, baseUrl, includeStatus).subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(CheckForNoteToSelfObserver())
    }

    fun shareLocationToNotes(credentials: String, url: String, objectType: String, objectId: String, metadata: String) {
        chatRepository.shareLocationToNotes(credentials, url, objectType, objectId, metadata)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onNext(genericOverall: GenericOverall) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error when sharing location to notes $e")
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    fun deleteReaction(roomToken: String, chatMessage: ChatMessage, emoji: String) {
        reactionsRepository.deleteReaction(roomToken, chatMessage, emoji)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ReactionDeletedModel> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.d(TAG, "$e")
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(reactionDeletedModel: ReactionDeletedModel) {
                    if (reactionDeletedModel.success) {
                        _reactionDeletedViewState.value = ReactionDeletedSuccessState(reactionDeletedModel)
                    }
                }
            })
    }

    fun addReaction(roomToken: String, chatMessage: ChatMessage, emoji: String) {
        reactionsRepository.addReaction(roomToken, chatMessage, emoji)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ReactionAddedModel> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.d(TAG, "$e")
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(reactionAddedModel: ReactionAddedModel) {
                    if (reactionAddedModel.success) {
                        _reactionAddedViewState.value = ReactionAddedSuccessState(reactionAddedModel)
                    }
                }
            })
    }

    fun editChatMessage(credentials: String, url: String, text: String) {
        chatRepository.editChatMessage(credentials, url, text)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ChatOverallSingleMessage> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to edit message", e)
                    _editMessageViewState.value = EditMessageErrorState
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(messageEdited: ChatOverallSingleMessage) {
                    _editMessageViewState.value = EditMessageSuccessState(messageEdited)
                }
            })
    }

    inner class GetRoomObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            LifeCycleObserver.disposableSet.add(d)
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
            LifeCycleObserver.disposableSet.add(d)
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
            LifeCycleObserver.disposableSet.add(d)
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
            LifeCycleObserver.disposableSet.add(d)
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

    inner class CheckForNoteToSelfObserver : Observer<RoomsOverall> {
        override fun onSubscribe(d: Disposable) {
            LifeCycleObserver.disposableSet.add(d)
        }

        override fun onNext(roomsOverall: RoomsOverall) {
            val rooms = roomsOverall.ocs?.data
            rooms?.let {
                try {
                    val noteToSelf = rooms.first {
                        val model = ConversationModel.mapToConversationModel(it)
                        ConversationUtils.isNoteToSelfConversation(model)
                    }
                    _getNoteToSelfAvaliability.value = NoteToSelfAvaliableState(noteToSelf.token!!)
                } catch (e: NoSuchElementException) {
                    _getNoteToSelfAvaliability.value = NoteToSelfNotAvaliableState
                    Log.e(TAG, "Note to self not found $e")
                }
            }
        }

        override fun onError(e: Throwable) {
            Log.d(TAG, "Error when getting rooms for Note to Self Observer $e")
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
