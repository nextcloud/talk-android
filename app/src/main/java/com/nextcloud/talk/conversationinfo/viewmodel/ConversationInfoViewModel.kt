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
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationinfo.ConversationInfoUiEvent
import com.nextcloud.talk.conversationinfo.ConversationInfoUiState
import com.nextcloud.talk.conversationinfo.CreateRoomRequest
import com.nextcloud.talk.conversationinfo.Participants
import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.converters.DomainEnumNotificationLevelConverter
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.Participant.ActorType.CIRCLES
import com.nextcloud.talk.models.json.participants.Participant.ActorType.EMAILS
import com.nextcloud.talk.models.json.participants.Participant.ActorType.FEDERATED
import com.nextcloud.talk.models.json.participants.Participant.ActorType.GROUPS
import com.nextcloud.talk.models.json.participants.Participant.ActorType.USERS
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.models.json.participants.TalkBan
import com.nextcloud.talk.models.json.profile.Profile
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.repositories.conversations.ConversationsRepository.ResendInvitationsResult
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ApiUtils.getUrlForRooms
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.preferences.preferencestorage.DatabaseStorageModule
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import javax.inject.Inject
@Suppress("TooManyFunctions", "LargeClass")
class ConversationInfoViewModel @Inject constructor(
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val conversationsRepository: ConversationsRepository,
    private val ncApi: NcApi
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
    object UnBanActorSuccessState : ViewState
    object UnBanActorErrorState : ViewState
    private val _getUnBanActorState: MutableLiveData<ViewState> = MutableLiveData()
    val getUnBanActorState: LiveData<ViewState>
        get() = _getUnBanActorState
    private var currentUser: User? = null
    private var currentToken: String = ""
    private var databaseStorageModule: DatabaseStorageModule? = null
    private val _uiState = MutableStateFlow(ConversationInfoUiState())
    val uiState: StateFlow<ConversationInfoUiState> = _uiState.asStateFlow()
    private val _uiEvent = MutableSharedFlow<ConversationInfoUiEvent>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<ConversationInfoUiEvent> = _uiEvent.asSharedFlow()
    fun loadParticipants(user: User, token: String) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
        val credentials = ApiUtils.getCredentials(user.username, user.token)!!
        val fieldMap = HashMap<String, Boolean>()
        fieldMap["includeStatus"] = true
        ncApi.getPeersForCall(
            credentials,
            ApiUtils.getUrlForParticipants(apiVersion, user.baseUrl!!, token),
            fieldMap
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ParticipantsOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                @Suppress("Detekt.TooGenericExceptionCaught")
                override fun onNext(participantsOverall: ParticipantsOverall) {
                    val participants = processParticipants(participantsOverall.ocs!!.data!!, user.userId)
                    _uiState.update { it.copy(participants = participants, showParticipants = true) }
                }
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error loading participants", e)
                }
                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @Suppress("DEPRECATION")
    private fun processParticipants(participants: List<Participant>, userId: String?): List<ParticipantModel> {
        val uiItems: MutableList<ParticipantModel> = ArrayList()
        var ownUiItem: ParticipantModel? = null
        for (participant in participants) {
            val isOnline = if (participant.sessionId != null) {
                !participant.sessionId.equals("0")
            } else {
                participant.sessionIds.isNotEmpty()
            }
            if (participant.calculatedActorType == USERS && participant.calculatedActorId == userId) {
                participant.sessionId = "-1"
                ownUiItem = ParticipantModel(participant, true)
            } else {
                uiItems.add(ParticipantModel(participant, isOnline))
            }
        }
        uiItems.sortWith(
            compareBy(
                { it.participant.actorType == GROUPS || it.participant.actorType == CIRCLES },
                { !it.isOnline },
                {
                    it.participant.type !in listOf(
                        Participant.ParticipantType.MODERATOR,
                        Participant.ParticipantType.OWNER,
                        Participant.ParticipantType.GUEST_MODERATOR
                    )
                },
                { it.participant.displayName!!.lowercase(Locale.ROOT) }
            )
        )
        if (ownUiItem != null) {
            uiItems.add(0, ownUiItem)
        }
        return uiItems
    }
    fun getRoom(user: User, token: String) {
        currentUser = user
        currentToken = token
        if (databaseStorageModule == null) {
            databaseStorageModule = DatabaseStorageModule(user, token)
        }
        _uiState.update { it.copy(isLoading = true) }
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
                val roomOverall = conversationsRepository.createRoom(credentials, url, body)
                val token = roomOverall.ocs?.data?.token
                if (token != null) {
                    _uiEvent.emit(ConversationInfoUiEvent.NavigateToChat(token))
                } else {
                    _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create room", e)
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
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
    private fun getCapabilities(user: User, token: String, conversationModel: ConversationModel) {
        if (conversationModel.remoteServer.isNullOrEmpty()) {
            handleCapabilitiesSuccess(user.capabilities!!.spreedCapability!!, conversationModel)
        } else {
            chatNetworkDataSource.getCapabilities(user, token)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<SpreedCapability> {
                    override fun onSubscribe(d: Disposable) {
                        LifeCycleObserver.disposableSet.add(d)
                    }
                    override fun onNext(spreedCapabilities: SpreedCapability) {
                        handleCapabilitiesSuccess(spreedCapabilities, conversationModel)
                    }
                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Error when fetching spreed capabilities", e)
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun handleCapabilitiesSuccess(spreedCapabilities: SpreedCapability, conversationModel: ConversationModel) {
        val res = NextcloudTalkApplication.sharedApplication!!.resources
        val user = currentUser ?: return
        val token = currentToken
        val dbModule = databaseStorageModule ?: DatabaseStorageModule(user, token).also { databaseStorageModule = it }

        val isOne2One = conversationModel.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        val isNoteToSelf = ConversationUtils.isNoteToSelfConversation(conversationModel)
        val isPublic = conversationModel.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
        val isGroup = conversationModel.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL
        val isSystem = conversationModel.type == ConversationEnums.ConversationType.ROOM_SYSTEM
        val canModerate = ConversationUtils.canModerate(conversationModel, spreedCapabilities)
        val isModerator = ConversationUtils.isParticipantOwnerOrModerator(conversationModel)

        val avatarUrl: String? = when (conversationModel.type) {
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ->
                if (conversationModel.name.isNotEmpty()) {
                    ApiUtils.getUrlForAvatar(user.baseUrl, conversationModel.name, true)
                } else {
                    null
                }
            ConversationEnums.ConversationType.ROOM_GROUP_CALL,
            ConversationEnums.ConversationType.ROOM_PUBLIC_CALL ->
                ApiUtils.getUrlForConversationAvatar(1, user.baseUrl, token)
            else -> null
        }

        val notifLevel = conversationModel.notificationLevel
        val notificationLevelStr = when {
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.NOTIFICATION_LEVELS) &&
                notifLevel != ConversationEnums.NotificationLevel.DEFAULT -> {
                when (DomainEnumNotificationLevelConverter().convertToInt(notifLevel)) {
                    NOTIFICATION_LEVEL_ALWAYS -> res.getString(R.string.nc_notify_me_always)
                    NOTIFICATION_LEVEL_MENTION -> res.getString(R.string.nc_notify_me_mention)
                    NOTIFICATION_LEVEL_NEVER -> res.getString(R.string.nc_notify_me_never)
                    else -> res.getString(R.string.nc_notify_me_mention)
                }
            }
            else -> {
                if (isOne2One && hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MENTION_FLAG)) {
                    res.getString(R.string.nc_notify_me_always)
                } else {
                    res.getString(R.string.nc_notify_me_mention)
                }
            }
        }

        dbModule.setMessageExpiration(conversationModel.messageExpiration)
        val expirationValue = dbModule.getString("conversation_settings_dropdown", "") ?: ""
        val expirationValues = res.getStringArray(R.array.message_expiring_values)
        val expirationDescriptions = res.getStringArray(R.array.message_expiring_descriptions)
        val expirationPos = expirationValues.indexOf(expirationValue).coerceAtLeast(0)
        val messageExpirationLabel =
            if (expirationPos < expirationDescriptions.size) expirationDescriptions[expirationPos] else ""

        val isLobbyEnabled =
            conversationModel.lobbyState == ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY
        val lobbyTimerMs = (conversationModel.lobbyTimer ?: 0L) * DateConstants.SECOND_DIVIDER
        val lobbyTimerLabel = if (conversationModel.lobbyTimer != null &&
            conversationModel.lobbyTimer != 0L &&
            conversationModel.lobbyTimer != Long.MIN_VALUE
        ) {
            DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT).format(Date(lobbyTimerMs))
        } else {
            ""
        }

        val recordingConsentType = CapabilitiesUtil.getRecordingConsentType(spreedCapabilities)
        val showRecordingConsent = isModerator &&
            !isNoteToSelf &&
            recordingConsentType != CapabilitiesUtil.RECORDING_CONSENT_NOT_REQUIRED
        val showRecordingConsentSwitch =
            showRecordingConsent && recordingConsentType == CapabilitiesUtil.RECORDING_CONSENT_DEPEND_ON_CONVERSATION
        val showRecordingConsentAll =
            showRecordingConsent && recordingConsentType == CapabilitiesUtil.RECORDING_CONSENT_REQUIRED
        val recordingConsentForConversation =
            conversationModel.recordingConsentRequired == RECORDING_CONSENT_REQUIRED_FOR_CONVERSATION
        val recordingConsentEnabled = !conversationModel.hasCall

        val showCallNotifications = !isSystem &&
            conversationModel.notificationCalls != null &&
            conversationModel.remoteServer.isNullOrEmpty()
        val callNotificationsEnabled = dbModule.getBoolean("call_notifications_switch", true)

        val showLockConversation = ConversationUtils.isConversationReadOnlyAvailable(
            conversationModel,
            spreedCapabilities
        )
        val isConversationLocked = dbModule.getBoolean("lock_switch", false)

        val canLeave = conversationModel.canLeaveConversation
        val canDelete = conversationModel.canDeleteConversation

        val showGuestAccess = canModerate
        val guestsAllowed = isPublic
        val hasPassword = guestsAllowed && conversationModel.hasPassword
        val showPasswordProtection = guestsAllowed
        val showResendInvitations = guestsAllowed &&
            user.capabilities?.spreedCapability?.features?.contains("sip-support") == true

        val showSharedItems = hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.RICH_OBJECT_LIST_MEDIA) &&
            conversationModel.remoteServer.isNullOrEmpty()
        val showThreadsButton = hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.THREADS)

        val isWebinarRoom = isGroup || isPublic
        val showWebinarSettings = hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.WEBINARY_LOBBY) &&
            isWebinarRoom &&
            canModerate

        val showImportantConversation =
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.IMPORTANT_CONVERSATIONS)
        val importantConversation = conversationModel.hasImportant
        val showSensitiveConversation =
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.SENSITIVE_CONVERSATIONS)
        val sensitiveConversation = conversationModel.hasSensitive

        val showArchiveConversation =
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.ARCHIVE_CONVERSATIONS)
        val isArchived = conversationModel.hasArchived

        val showAddParticipants: Boolean
        val showStartGroupChat: Boolean
        val showClearHistory: Boolean
        if (isOne2One && hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.CONVERSATION_CREATION_ALL)) {
            showStartGroupChat = true
            showAddParticipants = false
            showClearHistory = canDelete && hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.CLEAR_HISTORY)
        } else if (canModerate) {
            showAddParticipants = true
            showStartGroupChat = false
            showClearHistory = canDelete && hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.CLEAR_HISTORY)
        } else {
            showAddParticipants = false
            showStartGroupChat = false
            showClearHistory = false
        }

        val showEditButton = canModerate && hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.AVATAR)
        val showShareConversationButton = !isNoteToSelf
        val showListBans = canModerate && !isOne2One
        val showMessageExpiration = isModerator &&
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MESSAGE_EXPIRATION)

        val credentials = ApiUtils.getCredentials(user.username, user.token) ?: ""

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                spreedCapabilities = spreedCapabilities,
                capabilitiesVersion = state.capabilitiesVersion + 1,
                displayName = conversationModel.displayName,
                description = conversationModel.description,
                avatarUrl = avatarUrl,
                conversationType = conversationModel.type,
                serverBaseUrl = user.baseUrl ?: "",
                credentials = credentials,
                conversationToken = token,
                notificationLevel = notificationLevelStr,
                callNotificationsEnabled = callNotificationsEnabled,
                showCallNotifications = showCallNotifications,
                importantConversation = importantConversation,
                showImportantConversation = showImportantConversation,
                sensitiveConversation = sensitiveConversation,
                showSensitiveConversation = showSensitiveConversation,
                lobbyEnabled = isLobbyEnabled,
                showWebinarSettings = showWebinarSettings,
                lobbyTimerLabel = lobbyTimerLabel,
                showLobbyTimer = isLobbyEnabled,
                guestsAllowed = guestsAllowed,
                showGuestAccess = showGuestAccess,
                hasPassword = hasPassword,
                showPasswordProtection = showPasswordProtection,
                showResendInvitations = showResendInvitations,
                showSharedItems = showSharedItems,
                showThreadsButton = showThreadsButton,
                showRecordingConsent = showRecordingConsent,
                recordingConsentForConversation = recordingConsentForConversation,
                showRecordingConsentSwitch = showRecordingConsentSwitch,
                showRecordingConsentAll = showRecordingConsentAll,
                recordingConsentEnabled = recordingConsentEnabled,
                messageExpirationLabel = messageExpirationLabel,
                showMessageExpiration = showMessageExpiration,
                showShareConversationButton = showShareConversationButton,
                isConversationLocked = isConversationLocked,
                showLockConversation = showLockConversation,
                showAddParticipants = showAddParticipants,
                showStartGroupChat = showStartGroupChat,
                showListBans = showListBans,
                showArchiveConversation = showArchiveConversation,
                isArchived = isArchived,
                canLeave = canLeave,
                canDelete = canDelete,
                showClearHistory = showClearHistory,
                showEditButton = showEditButton
            )
        }

        loadParticipants(user, token)
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
                conversationsRepository.banActor(user.getCredentials(), url, actorType, actorId, internalNote)
                _uiEvent.emit(ConversationInfoUiEvent.RefreshParticipants)
            } catch (exception: Exception) {
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbarText("Error banning actor"))
                Log.e(TAG, "Error banning a participant", exception)
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
                    processProfileData(profile)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get profile data (if not supported there wil be http405)", e)
            }
        }
    }
    private fun processProfileData(profile: Profile) {
        val pronouns = profile.pronouns ?: ""
        val concat1 = if (profile.role != null && profile.company != null) " @ " else ""
        val professionCompany = "${profile.role ?: ""}$concat1${profile.company ?: ""}"
        val secondsToAdd = profile.timezoneOffset?.toLong() ?: 0
        val localTime = ZonedDateTime.ofInstant(Instant.now().plusSeconds(secondsToAdd), ZoneOffset.ofTotalSeconds(0))
        val localTimeString =
            localTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
        val concat2 = if (profile.address != null) " : " else ""
        val localTimeLocation = "$localTimeString$concat2${profile.address ?: ""}"
        _uiState.update {
            it.copy(
                pronouns = pronouns,
                professionCompany = professionCompany,
                localTimeLocation = localTimeLocation,
                profileDataAvailable = true
            )
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun allowGuests(user: User, token: String, allow: Boolean) {
        val previous = _uiState.value.guestsAllowed
        _uiState.update { it.copy(guestsAllowed = allow) }
        viewModelScope.launch {
            try {
                val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
                val url = ApiUtils.getUrlForRoomPublic(apiVersion, user.baseUrl!!, token)
                conversationsRepository.allowGuests(user = user, url = url, token = token, allow = allow)
            } catch (exception: Exception) {
                _uiState.update { it.copy(guestsAllowed = previous) }
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_guest_access_allow_failed))
                Log.e(TAG, "Error allowing guests", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun setPassword(user: User, url: String, password: String) {
        val previousHasPassword = _uiState.value.hasPassword
        val newHasPassword = password.isNotEmpty()
        _uiState.update { it.copy(hasPassword = newHasPassword) }
        viewModelScope.launch {
            try {
                conversationsRepository.setPassword(user, url, password)
            } catch (exception: Exception) {
                _uiState.update { it.copy(hasPassword = previousHasPassword) }
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_guest_access_password_failed))
                Log.e(TAG, "Error setting password", exception)
            }
        }
    }

    fun resendInvitations(user: User, token: String) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4))
        val url = ApiUtils.getUrlForParticipantsResendInvitations(apiVersion, user.baseUrl!!, token)
        conversationsRepository.resendInvitations(user = user, url = url)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<ResendInvitationsResult> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }

                override fun onNext(result: ResendInvitationsResult) {
                    if (result.successful) {
                        viewModelScope.launch {
                            _uiEvent.emit(
                                ConversationInfoUiEvent.ShowSnackbar(
                                    R.string.nc_guest_access_resend_invitations_successful
                                )
                            )
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error resending invitations", e)
                    viewModelScope.launch {
                        _uiEvent.emit(
                            ConversationInfoUiEvent.ShowSnackbar(
                                R.string.nc_guest_access_resend_invitations_failed
                            )
                        )
                    }
                }

                override fun onComplete() {
                    // unused atm
                }
            })
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
    fun toggleArchive(user: User, token: String) {
        viewModelScope.launch {
            try {
                val isArchived = _uiState.value.isArchived
                if (isArchived) {
                    unarchiveConversation(user, token)
                } else {
                    archiveConversation(user, token)
                }
                getRoom(user, token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle archive state", e)
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
            }
        }
    }

    fun toggleLobby(user: User, token: String) {
        val previousLobbyEnabled = _uiState.value.lobbyEnabled
        val previousShowLobbyTimer = _uiState.value.showLobbyTimer
        val previousLobbyTimerLabel = _uiState.value.lobbyTimerLabel
        val newLobbyEnabled = !previousLobbyEnabled
        val newLobbyState = if (newLobbyEnabled) 1 else 0
        val lobbyTimer = if (newLobbyEnabled) (_uiState.value.conversation?.lobbyTimer ?: 0L) else 0L
        _uiState.update {
            it.copy(
                lobbyEnabled = newLobbyEnabled,
                showLobbyTimer = newLobbyEnabled,
                lobbyTimerLabel = if (!newLobbyEnabled) "" else it.lobbyTimerLabel
            )
        }
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
        ncApi.setLobbyForConversation(
            ApiUtils.getCredentials(user.username, user.token),
            ApiUtils.getUrlForRoomWebinaryLobby(apiVersion, user.baseUrl!!, token),
            newLobbyState,
            lobbyTimer
        )?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }
                override fun onNext(t: GenericOverall) { /* unused */ }
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to set lobby state", e)
                    _uiState.update {
                        it.copy(
                            lobbyEnabled = previousLobbyEnabled,
                            showLobbyTimer = previousShowLobbyTimer,
                            lobbyTimerLabel = previousLobbyTimerLabel
                        )
                    }
                    viewModelScope.launch {
                        _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
                    }
                }
                override fun onComplete() { /* unused */ }
            })
    }

    fun setLobbyTimerAndSubmit(user: User, token: String, timestampSeconds: Long) {
        val label = if (timestampSeconds != 0L) {
            DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT)
                .format(Date(timestampSeconds * DateConstants.SECOND_DIVIDER))
        } else {
            ""
        }
        _uiState.update { it.copy(lobbyTimerLabel = label) }
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
        ncApi.setLobbyForConversation(
            ApiUtils.getCredentials(user.username, user.token),
            ApiUtils.getUrlForRoomWebinaryLobby(apiVersion, user.baseUrl!!, token),
            1,
            timestampSeconds
        )?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }
                override fun onNext(t: GenericOverall) { /* unused */ }
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to set lobby timer", e)
                    viewModelScope.launch {
                        _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
                    }
                }
                override fun onComplete() { /* unused */ }
            })
    }

    fun toggleRecordingConsent(user: User, token: String) {
        val previousConsent = _uiState.value.recordingConsentForConversation
        val newConsent = !previousConsent
        _uiState.update { it.copy(recordingConsentForConversation = newConsent) }
        val state = if (newConsent) RECORDING_CONSENT_REQUIRED_FOR_CONVERSATION else 0
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
        ncApi.setRecordingConsent(
            ApiUtils.getCredentials(user.username, user.token),
            ApiUtils.getUrlForRecordingConsent(apiVersion, user.baseUrl!!, token),
            state
        )?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    LifeCycleObserver.disposableSet.add(d)
                }
                override fun onNext(t: GenericOverall) { /* unused */ }
                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error setting recording consent", e)
                    _uiState.update { it.copy(recordingConsentForConversation = previousConsent) }
                    viewModelScope.launch {
                        _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
                    }
                }
                override fun onComplete() { /* unused */ }
            })
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun toggleLock(user: User, token: String) {
        val previousLocked = _uiState.value.isConversationLocked
        val newLocked = !previousLocked
        _uiState.update { it.copy(isConversationLocked = newLocked) }
        viewModelScope.launch {
            databaseStorageModule?.saveBoolean("lock_switch", newLocked)
            try {
                val apiVersion = ApiUtils.getConversationApiVersion(
                    user,
                    intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1)
                )
                val url = ApiUtils.getUrlForConversationReadOnly(apiVersion, user.baseUrl!!, token)
                conversationsRepository.setConversationReadOnly(user = user, url = url, state = if (newLocked) 1 else 0)
            } catch (exception: Exception) {
                _uiState.update { it.copy(isConversationLocked = previousLocked) }
                databaseStorageModule?.saveBoolean("lock_switch", previousLocked)
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.conversation_read_only_failed))
            }
        }
    }

    fun toggleCallNotifications() {
        val newEnabled = !_uiState.value.callNotificationsEnabled
        _uiState.update { it.copy(callNotificationsEnabled = newEnabled) }
        viewModelScope.launch {
            databaseStorageModule?.saveBoolean("call_notifications_switch", newEnabled)
        }
    }

    fun saveNotificationLevel(position: Int) {
        val res = NextcloudTalkApplication.sharedApplication!!.resources
        val values = res.getStringArray(R.array.message_notification_levels_entry_values)
        val descriptions = res.getStringArray(R.array.message_notification_levels)
        if (position in values.indices && position in descriptions.indices) {
            _uiState.update { it.copy(notificationLevel = descriptions[position]) }
            viewModelScope.launch {
                databaseStorageModule?.saveString("conversation_info_message_notifications_dropdown", values[position])
            }
        }
    }

    fun saveMessageExpiration(position: Int) {
        val res = NextcloudTalkApplication.sharedApplication!!.resources
        val values = res.getStringArray(R.array.message_expiring_values)
        val descriptions = res.getStringArray(R.array.message_expiring_descriptions)
        if (position in values.indices && position in descriptions.indices) {
            _uiState.update { it.copy(messageExpirationLabel = descriptions[position]) }
            viewModelScope.launch {
                databaseStorageModule?.saveString("conversation_settings_dropdown", values[position])
            }
        }
    }

    fun setUpcomingEvent(summary: String?, time: String?) {
        _uiState.update { it.copy(upcomingEventSummary = summary, upcomingEventTime = time) }
    }

    suspend fun emitSnackbar(@androidx.annotation.StringRes resId: Int) {
        _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(resId))
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun toggleImportantConversation(credentials: String, baseUrl: String, roomToken: String) {
        val previousValue = _uiState.value.importantConversation
        val newValue = !previousValue
        _uiState.update { it.copy(importantConversation = newValue) }
        viewModelScope.launch {
            try {
                if (newValue) {
                    conversationsRepository.markConversationAsImportant(credentials, baseUrl, roomToken)
                } else {
                    conversationsRepository.markConversationAsUnImportant(credentials, baseUrl, roomToken)
                }
            } catch (exception: Exception) {
                _uiState.update { it.copy(importantConversation = previousValue) }
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
                Log.e(TAG, "failed to toggle important conversation state", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun toggleSensitiveConversation(credentials: String, baseUrl: String, roomToken: String) {
        val previousValue = _uiState.value.sensitiveConversation
        val newValue = !previousValue
        _uiState.update { it.copy(sensitiveConversation = newValue) }
        viewModelScope.launch {
            try {
                if (newValue) {
                    conversationsRepository.markConversationAsSensitive(credentials, baseUrl, roomToken)
                } else {
                    conversationsRepository.markConversationAsInsensitive(credentials, baseUrl, roomToken)
                }
            } catch (exception: Exception) {
                _uiState.update { it.copy(sensitiveConversation = previousValue) }
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
                Log.e(TAG, "failed to toggle sensitive conversation state", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun clearChatHistory(user: User, url: String) {
        viewModelScope.launch {
            try {
                conversationsRepository.clearChatHistory(user, url)
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_clear_history_success))
            } catch (exception: Exception) {
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
                Log.e(TAG, "failed to clear chat history", exception)
            }
        }
    }

    inner class GetRoomObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }
        override fun onNext(conversationModel: ConversationModel) {
            _uiState.update { it.copy(conversation = conversationModel) }
            currentUser?.let { getCapabilities(it, currentToken, conversationModel) }
        }
        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when fetching room")
            _uiState.update { it.copy(isLoading = false) }
            viewModelScope.launch {
                _uiEvent.emit(ConversationInfoUiEvent.ShowSnackbar(R.string.nc_common_error_sorry))
            }
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
        private const val NOTIFICATION_LEVEL_ALWAYS: Int = 1
        private const val NOTIFICATION_LEVEL_MENTION: Int = 2
        private const val NOTIFICATION_LEVEL_NEVER: Int = 3
        private const val RECORDING_CONSENT_REQUIRED_FOR_CONVERSATION: Int = 1
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
}
