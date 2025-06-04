/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo.viewmodel

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationinfo.CreateRoomRequest
import com.nextcloud.talk.conversationinfo.Participants
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.ActorType.CIRCLES
import com.nextcloud.talk.models.json.participants.Participant.ActorType.EMAILS
import com.nextcloud.talk.models.json.participants.Participant.ActorType.FEDERATED
import com.nextcloud.talk.models.json.participants.Participant.ActorType.GROUPS
import com.nextcloud.talk.models.json.participants.TalkBan
import com.nextcloud.talk.models.json.profile.Profile
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ApiUtils.getUrlForRooms
import com.nextcloud.talk.utils.DisplayUtils
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

    private val _passwordViewState = MutableLiveData<PasswordUiState>(PasswordUiState.None)
    val passwordViewState: LiveData<PasswordUiState>
        get() = _passwordViewState

    private val _getCapabilitiesViewState: MutableLiveData<ViewState> = MutableLiveData(GetCapabilitiesStartState)
    val getCapabilitiesViewState: LiveData<ViewState>
        get() = _getCapabilitiesViewState

    private val _clearChatHistoryViewState: MutableLiveData<ClearChatHistoryViewState> =
        MutableLiveData(ClearChatHistoryViewState.None)
    val clearChatHistoryViewState: LiveData<ClearChatHistoryViewState>
        get() = _clearChatHistoryViewState

    private val _getConversationReadOnlyState: MutableLiveData<SetConversationReadOnlyViewState> =
        MutableLiveData(SetConversationReadOnlyViewState.None)
    val getConversationReadOnlyState: LiveData<SetConversationReadOnlyViewState>
        get() = _getConversationReadOnlyState

    @Suppress("PropertyName")
    private val _markConversationAsImportantResult =
        MutableLiveData<MarkConversationAsImportantViewState>(MarkConversationAsImportantViewState.None)
    val markAsImportantResult: LiveData<MarkConversationAsImportantViewState>
        get() = _markConversationAsImportantResult

    @Suppress("PropertyName")
    private val _markConversationAsUnimportantResult =
        MutableLiveData<MarkConversationAsUnimportantViewState>(MarkConversationAsUnimportantViewState.None)
    val markAsUnimportantResult: LiveData<MarkConversationAsUnimportantViewState>
        get() = _markConversationAsUnimportantResult

    private val _createRoomViewState = MutableLiveData<CreateRoomUIState>(CreateRoomUIState.None)
    val createRoomViewState: LiveData<CreateRoomUIState>
        get() = _createRoomViewState

    object GetProfileErrorState : ViewState
    class GetProfileSuccessState(val profile: Profile) : ViewState
    private val _getProfileViewState = MutableLiveData<ViewState>()
    val getProfileViewState: LiveData<ViewState>
        get() = _getProfileViewState

    @Suppress("PropertyName")
    private val _markConversationAsSensitiveResult =
        MutableLiveData<MarkConversationAsSensitiveViewState>(MarkConversationAsSensitiveViewState.None)
    val markAsSensitiveResult: LiveData<MarkConversationAsSensitiveViewState>
        get() = _markConversationAsSensitiveResult

    @Suppress("PropertyName")
    private val _markConversationAsInsensitiveResult =
        MutableLiveData<MarkConversationAsInsensitiveViewState>(MarkConversationAsInsensitiveViewState.None)
    val markAsInsensitiveResult: LiveData<MarkConversationAsInsensitiveViewState>
        get() = _markConversationAsInsensitiveResult

    fun getRoom(user: User, token: String) {
        _viewState.value = GetRoomStartState
        chatNetworkDataSource.getRoom(user, token)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetRoomObserver())
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun createRoomFromOneToOne(
        user: User,
        userItems: List<Participant>,
        autocompleteUsers: List<AutocompleteUser>,
        roomToken: String
    ) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
        val url = getUrlForRooms(apiVersion, user.baseUrl!!)
        val credentials = ApiUtils.getCredentials(user.username, user.token)!!

        val participantsBody = convertAutocompleteUserToParticipant(autocompleteUsers)

        val body = CreateRoomRequest(
            roomName = createConversationNameByParticipants(
                userItems.map { it.displayName },
                autocompleteUsers.map { it.label }
            ),
            roomType = GROUP_CONVERSATION_TYPE,
            readOnly = 0,
            listable = 1,
            lobbyTimer = 0,
            sipEnabled = 0,
            permissions = 0,
            recordingConsent = 0,
            mentionPermissions = 0,
            participants = participantsBody,
            objectType = EXTENDED_CONVERSATION,
            objectId = roomToken
        )

        viewModelScope.launch {
            try {
                val roomOverall = conversationsRepository.createRoom(
                    credentials,
                    url,
                    body
                )
                _createRoomViewState.value = CreateRoomUIState.Success(roomOverall)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create room", e)
                _createRoomViewState.value = CreateRoomUIState.Error(e)
            }
        }
    }

    private fun convertAutocompleteUserToParticipant(autocompleteUsers: List<AutocompleteUser>): Participants {
        val participants = Participants()

        autocompleteUsers.forEach { autocompleteUser ->
            when (autocompleteUser.source) {
                GROUPS.name.lowercase() -> participants.groups.add(autocompleteUser.id!!)
                EMAILS.name.lowercase() -> participants.emails.add(autocompleteUser.id!!)
                CIRCLES.name.lowercase() -> participants.teams.add(autocompleteUser.id!!)
                FEDERATED.name.lowercase() -> participants.federatedUsers.add(autocompleteUser.id!!)
                "phones".lowercase() -> participants.phones.add(autocompleteUser.id!!)
                else -> participants.users.add(autocompleteUser.id!!)
            }
        }

        return participants
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

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun listBans(user: User, token: String) {
        val url = ApiUtils.getUrlForBans(user.baseUrl!!, token)
        viewModelScope.launch {
            try {
                val listBans = conversationsRepository.listBans(user.getCredentials(), url)
                _getTalkBanState.value = ListBansSuccessState(listBans)
            } catch (exception: Exception) {
                _getTalkBanState.value = ListBansErrorState
                Log.e(TAG, "Error while getting list of banned participants", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun banActor(user: User, token: String, actorType: String, actorId: String, internalNote: String) {
        val url = ApiUtils.getUrlForBans(user.baseUrl!!, token)
        viewModelScope.launch {
            try {
                val talkBan = conversationsRepository.banActor(
                    user.getCredentials(),
                    url,
                    actorType,
                    actorId,
                    internalNote
                )
                _getBanActorState.value = BanActorSuccessState(talkBan)
            } catch (exception: Exception) {
                _getBanActorState.value = BanActorErrorState
                Log.e(TAG, "Error banning a participant", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun setConversationReadOnly(roomToken: String, state: Int) {
        viewModelScope.launch {
            try {
                conversationsRepository.setConversationReadOnly(roomToken, state)
                _getConversationReadOnlyState.value = SetConversationReadOnlyViewState.Success
            } catch (exception: Exception) {
                _getConversationReadOnlyState.value = SetConversationReadOnlyViewState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun unbanActor(user: User, token: String, banId: Int) {
        val url = ApiUtils.getUrlForUnban(user.baseUrl!!, token, banId)
        viewModelScope.launch {
            try {
                conversationsRepository.unbanActor(user.getCredentials(), url)
                _getUnBanActorState.value = UnBanActorSuccessState
            } catch (exception: Exception) {
                _getUnBanActorState.value = UnBanActorErrorState
                Log.e(TAG, "Error while unbanning a participant", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun getProfileData(user: User, userId: String) {
        val url = ApiUtils.getUrlForProfile(user.baseUrl!!, userId)
        viewModelScope.launch {
            try {
                val profile = conversationsRepository.getProfile(user.getCredentials(), url)
                if (profile != null) {
                    _getProfileViewState.value = GetProfileSuccessState(profile)
                } else {
                    _getProfileViewState.value = GetProfileErrorState
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get profile data (if not supported there wil be http405)", e)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun allowGuests(token: String, allow: Boolean) {
        viewModelScope.launch {
            try {
                conversationsRepository.allowGuests(token, allow)
                _allowGuestsViewState.value = AllowGuestsUIState.Success(allow)
            } catch (exception: Exception) {
                _allowGuestsViewState.value = AllowGuestsUIState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    @SuppressLint("SuspiciousIndentation")
    fun setPassword(password: String, token: String) {
        viewModelScope.launch {
            try {
                conversationsRepository.setPassword(password, token)
                _passwordViewState.value = PasswordUiState.Success
            } catch (exception: Exception) {
                _passwordViewState.value = PasswordUiState.Error(exception)
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

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun markConversationAsImportant(credentials: String, baseUrl: String, roomToken: String) {
        viewModelScope.launch {
            try {
                val response = conversationsRepository.markConversationAsImportant(credentials, baseUrl, roomToken)
                _markConversationAsImportantResult.value =
                    MarkConversationAsImportantViewState.Success(response.ocs?.meta?.statusCode!!)
            } catch (exception: Exception) {
                _markConversationAsImportantResult.value =
                    MarkConversationAsImportantViewState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun markConversationAsUnimportant(credentials: String, baseUrl: String, roomToken: String) {
        viewModelScope.launch {
            try {
                val response = conversationsRepository.markConversationAsUnImportant(credentials, baseUrl, roomToken)
                _markConversationAsUnimportantResult.value =
                    MarkConversationAsUnimportantViewState.Success(response.ocs?.meta?.statusCode!!)
            } catch (exception: Exception) {
                _markConversationAsUnimportantResult.value =
                    MarkConversationAsUnimportantViewState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun clearChatHistory(apiVersion: Int, roomToken: String) {
        viewModelScope.launch {
            try {
                conversationsRepository.clearChatHistory(apiVersion, roomToken)
                _clearChatHistoryViewState.value = ClearChatHistoryViewState.Success
            } catch (exception: Exception) {
                _clearChatHistoryViewState.value = ClearChatHistoryViewState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun markConversationAsSensitive(credentials: String, baseUrl: String, roomToken: String) {
        viewModelScope.launch {
            try {
                val response = conversationsRepository.markConversationAsSensitive(credentials, baseUrl, roomToken)
                _markConversationAsSensitiveResult.value =
                    MarkConversationAsSensitiveViewState.Success(response.ocs?.meta?.statusCode!!)
            } catch (exception: Exception) {
                _markConversationAsSensitiveResult.value =
                    MarkConversationAsSensitiveViewState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun markConversationAsInsensitive(credentials: String, baseUrl: String, roomToken: String) {
        viewModelScope.launch {
            try {
                val response = conversationsRepository.markConversationAsInsensitive(credentials, baseUrl, roomToken)
                _markConversationAsInsensitiveResult.value =
                    MarkConversationAsInsensitiveViewState.Success(response.ocs?.meta?.statusCode!!)
            } catch (exception: Exception) {
                _markConversationAsInsensitiveResult.value =
                    MarkConversationAsInsensitiveViewState.Error(exception)
            }
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
        private const val NEW_CONVERSATION_PARTICIPANTS_SEPARATOR = ", "
        private const val EXTENDED_CONVERSATION = "extended_conversation"
        private const val GROUP_CONVERSATION_TYPE = "2"
        private const val MAX_ROOM_NAME_LENGTH = 255

        fun createConversationNameByParticipants(
            originalParticipants: List<String?>,
            allParticipants: List<String?>
        ): String {
            fun List<String?>.sortedJoined() =
                sortedBy { it?.lowercase() }
                    .joinToString(NEW_CONVERSATION_PARTICIPANTS_SEPARATOR)

            val addedParticipants = allParticipants - originalParticipants.toSet()
            val conversationName = originalParticipants.mapNotNull { it }.sortedJoined() +
                NEW_CONVERSATION_PARTICIPANTS_SEPARATOR +
                addedParticipants.mapNotNull { it }.sortedJoined()

            return DisplayUtils.ellipsize(conversationName, MAX_ROOM_NAME_LENGTH)
        }
    }

    sealed class ClearChatHistoryViewState {
        data object None : ClearChatHistoryViewState()
        data object Success : ClearChatHistoryViewState()
        data class Error(val exception: Exception) : ClearChatHistoryViewState()
    }

    sealed class MarkConversationAsSensitiveViewState {
        data object None : MarkConversationAsSensitiveViewState()
        data class Success(val statusCode: Int) : MarkConversationAsSensitiveViewState()
        data class Error(val exception: Exception) : MarkConversationAsSensitiveViewState()
    }

    sealed class MarkConversationAsInsensitiveViewState {
        data object None : MarkConversationAsInsensitiveViewState()
        data class Success(val statusCode: Int) : MarkConversationAsInsensitiveViewState()
        data class Error(val exception: Exception) : MarkConversationAsInsensitiveViewState()
    }

    sealed class SetConversationReadOnlyViewState {
        data object None : SetConversationReadOnlyViewState()
        data object Success : SetConversationReadOnlyViewState()
        data class Error(val exception: Exception) : SetConversationReadOnlyViewState()
    }

    sealed class AllowGuestsUIState {
        data object None : AllowGuestsUIState()
        data class Success(val allow: Boolean) : AllowGuestsUIState()
        data class Error(val exception: Exception) : AllowGuestsUIState()
    }

    sealed class CreateRoomUIState {
        data object None : CreateRoomUIState()
        data class Success(val room: RoomOverall) : CreateRoomUIState()
        data class Error(val exception: Exception) : CreateRoomUIState()
    }

    sealed class PasswordUiState {
        data object None : PasswordUiState()
        data object Success : PasswordUiState()
        data class Error(val exception: Exception) : PasswordUiState()
    }

    sealed class MarkConversationAsImportantViewState {
        data object None : MarkConversationAsImportantViewState()
        data class Success(val statusCode: Int) : MarkConversationAsImportantViewState()
        data class Error(val exception: Exception) : MarkConversationAsImportantViewState()
    }

    sealed class MarkConversationAsUnimportantViewState {
        data object None : MarkConversationAsUnimportantViewState()
        data class Success(val statusCode: Int) : MarkConversationAsUnimportantViewState()
        data class Error(val exception: Exception) : MarkConversationAsUnimportantViewState()
    }
}
