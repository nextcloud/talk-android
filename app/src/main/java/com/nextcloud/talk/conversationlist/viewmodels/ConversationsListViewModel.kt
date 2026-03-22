/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.R
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.contacts.ContactsRepository
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.ui.ConversationListEntry
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.invitation.data.InvitationsModel
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.messagesearch.MessageSearchHelper
import com.nextcloud.talk.messagesearch.MessageSearchHelper.MessageSearchResults
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.converters.EnumActorTypeConverter
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.ARCHIVE
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.DEFAULT
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.MENTION
import com.nextcloud.talk.ui.dialog.FilterConversationFragment.Companion.UNREAD
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Suppress("LongParameterList")
class ConversationsListViewModel @Inject constructor(
    private val repository: OfflineConversationsRepository,
    private val threadsRepository: ThreadsRepository,
    private val currentUserProvider: CurrentUserProviderOld,
    private val openConversationsRepository: OpenConversationsRepository,
    private val contactsRepository: ContactsRepository,
    private val unifiedSearchRepository: UnifiedSearchRepository,
    private val invitationsRepository: InvitationsRepository,
    private val arbitraryStorageManager: ArbitraryStorageManager,
    var userManager: UserManager
) : ViewModel() {

    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser
    val credentials = ApiUtils.getCredentials(_currentUser.username, _currentUser.token) ?: ""

    private val searchHelper = MessageSearchHelper(unifiedSearchRepository, currentUser)

    sealed interface ViewState

    sealed class ThreadsExistUiState {
        data object None : ThreadsExistUiState()
        data class Success(val threadsExistence: Boolean?) : ThreadsExistUiState()
        data class Error(val exception: Exception) : ThreadsExistUiState()
    }

    private val _threadsExistState = MutableStateFlow<ThreadsExistUiState>(ThreadsExistUiState.None)
    val threadsExistState: StateFlow<ThreadsExistUiState> = _threadsExistState

    sealed class OpenConversationsUiState {
        data object None : OpenConversationsUiState()
        data class Success(val conversations: List<Conversation>) : OpenConversationsUiState()
        data class Error(val exception: Throwable) : OpenConversationsUiState()
    }

    private val _openConversationsState = MutableStateFlow<OpenConversationsUiState>(OpenConversationsUiState.None)
    val openConversationsState: StateFlow<OpenConversationsUiState> = _openConversationsState

    object GetRoomsStartState : ViewState
    object GetRoomsErrorState : ViewState
    open class GetRoomsSuccessState(val listIsNotEmpty: Boolean) : ViewState

    private val _getRoomsViewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomsStartState)
    val getRoomsViewState: LiveData<ViewState>
        get() = _getRoomsViewState

    val getRoomsFlow = repository.roomListFlow
        .onEach { list ->
            _getRoomsViewState.value = GetRoomsSuccessState(list.isNotEmpty())
        }.catch {
            _getRoomsViewState.value = GetRoomsErrorState
        }

    private val _isShimmerVisible = MutableStateFlow(true)

    /**
     * Drives the shimmer skeleton visibility. Set to false as soon as the first room-list
     * emission arrives (same subscription as [getRoomsStateFlow] so it hides in the same
     * coroutine step that populates [conversationListEntriesFlow].
     */
    val isShimmerVisible: StateFlow<Boolean> = _isShimmerVisible.asStateFlow()

    val getRoomsStateFlow = repository
        .roomListFlow
        .onEach { _isShimmerVisible.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf())

    private val _federationInvitationHintVisible = MutableStateFlow(false)
    val federationInvitationHintVisible: StateFlow<Boolean> = _federationInvitationHintVisible.asStateFlow()

    object ShowBadgeStartState : ViewState
    object ShowBadgeErrorState : ViewState
    open class ShowBadgeSuccessState(val showBadge: Boolean) : ViewState

    private val _showBadgeViewState: MutableLiveData<ViewState> = MutableLiveData(ShowBadgeStartState)
    val showBadgeViewState: LiveData<ViewState>
        get() = _showBadgeViewState

    private val searchResultEntries: MutableStateFlow<List<ConversationListEntry>> =
        MutableStateFlow(emptyList())

    private val filterStateFlow = MutableStateFlow<Map<String, Boolean>>(
        mapOf(MENTION to false, UNREAD to false, ARCHIVE to false, DEFAULT to true)
    )

    private val _isSearchActiveFlow = MutableStateFlow(false)
    val isSearchActiveFlow: StateFlow<Boolean> = _isSearchActiveFlow.asStateFlow()

    private val _currentSearchQueryFlow = MutableStateFlow("")
    val currentSearchQueryFlow: StateFlow<String> = _currentSearchQueryFlow.asStateFlow()

    private val hideRoomToken = MutableStateFlow<String?>(null)

    /**
     * Single source of truth for the [ConversationList] LazyColumn.
     * Auto-reacts to rooms, filter, search-active and search-result changes.
     */
    val conversationListEntriesFlow: StateFlow<List<ConversationListEntry>> = combine(
        getRoomsStateFlow,
        filterStateFlow,
        _isSearchActiveFlow,
        searchResultEntries,
        hideRoomToken
    ) { rooms, filterState, isSearchActive, searchResults, hideToken ->
        buildConversationListEntries(rooms, filterState, isSearchActive, searchResults, hideToken)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS), emptyList())

    /** Update filter state; triggers [conversationListEntriesFlow] re-emit. */
    fun applyFilter(newFilterState: Map<String, Boolean>) {
        filterStateFlow.value = newFilterState
    }

    /** Mark the SearchView as expanded (true) or collapsed (false). */
    fun setIsSearchActive(active: Boolean) {
        _isSearchActiveFlow.value = active
        if (!active) {
            searchResultEntries.value = emptyList()
            _currentSearchQueryFlow.value = ""
        }
    }

    /** Exclude the forward-source room token from the list. */
    fun setHideRoomToken(token: String?) {
        hideRoomToken.value = token
    }

    fun getFederationInvitations() {
        _federationInvitationHintVisible.value = false
        _showBadgeViewState.value = ShowBadgeStartState

        userManager.users.blockingGet()?.forEach {
            invitationsRepository.fetchInvitations(it)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(FederatedInvitationsObserver())
        }
    }

    @Suppress("LongMethod")
    fun getSearchQuery(context: Context, filter: String) {
        _currentSearchQueryFlow.value = filter
        val conversationsTitle = context.resources.getString(R.string.conversations)
        val openConversationsTitle = context.resources.getString(R.string.openConversations)
        val usersTitle = context.resources.getString(R.string.nc_user)
        val messagesTitle = context.resources.getString(R.string.messages)
        val actorTypeConverter = EnumActorTypeConverter()

        viewModelScope.launch {
            combine(
                getRoomsStateFlow.map { list ->
                    list.filter { it.displayName?.contains(filter, ignoreCase = true) == true }
                },
                openConversationsRepository.fetchOpenConversationsFlow(currentUser, filter),
                contactsRepository.getContactsFlow(currentUser, filter),
                getMessagesFlow(filter)
            ) { localConvs, openConvs, contacts, (messages, hasMore) ->
                val entries = mutableListOf<ConversationListEntry>()

                if (localConvs.isNotEmpty()) {
                    entries.add(ConversationListEntry.Header(conversationsTitle))
                    localConvs.forEach { entries.add(ConversationListEntry.ConversationEntry(it)) }
                }
                if (openConvs.isNotEmpty()) {
                    entries.add(ConversationListEntry.Header(openConversationsTitle))
                    openConvs.forEach { conv ->
                        entries.add(
                            ConversationListEntry.ConversationEntry(
                                ConversationModel.mapToConversationModel(conv, currentUser)
                            )
                        )
                    }
                }
                if (contacts.isNotEmpty()) {
                    entries.add(ConversationListEntry.Header(usersTitle))
                    contacts.forEach { autocompleteUser ->
                        val participant = Participant()
                        participant.actorId = autocompleteUser.id
                        participant.actorType = actorTypeConverter.getFromString(autocompleteUser.source)
                        participant.displayName = autocompleteUser.label
                        entries.add(ConversationListEntry.ContactEntry(participant))
                    }
                }
                if (messages.isNotEmpty()) {
                    entries.add(ConversationListEntry.Header(messagesTitle))
                    messages.forEach { msg -> entries.add(ConversationListEntry.MessageResultEntry(msg)) }
                }
                if (hasMore) entries.add(ConversationListEntry.LoadMore)

                entries.toList()
            }.collect { results ->
                searchResultEntries.emit(results)
            }
        }
    }

    private fun getMessagesFlow(search: String): Flow<MessageSearchResults> =
        searchHelper.startMessageSearch(search).subscribeOn(Schedulers.io()).asFlow()

    fun loadMoreMessages(context: Context) {
        viewModelScope.launch {
            searchHelper.loadMore()
                ?.asFlow()
                ?.map { (messages, hasMore) ->
                    val newEntries: List<ConversationListEntry> =
                        messages.map { ConversationListEntry.MessageResultEntry(it) }
                    if (hasMore) newEntries + ConversationListEntry.LoadMore else newEntries
                }?.collect { newEntries ->
                    searchResultEntries.update { current ->
                        val withoutOld = current.filter { entry ->
                            entry !is ConversationListEntry.MessageResultEntry &&
                                entry !is ConversationListEntry.LoadMore
                        }
                        withoutOld + newEntries
                    }
                }
        }
    }

    fun getRooms(user: User) {
        val startNanoTime = System.nanoTime()
        Log.d(TAG, "fetchData - getRooms - calling: $startNanoTime")
        repository.getRooms(user)
    }

    fun checkIfThreadsExist() {
        val limitForFollowedThreadsExistenceCheck = 1
        val accountId = UserIdUtils.getIdForUser(currentUserProvider.currentUser.blockingGet())

        fun isLastCheckTooOld(lastCheckDate: Long): Boolean {
            val currentTimeMillis = System.currentTimeMillis()
            val differenceMillis = currentTimeMillis - lastCheckDate
            val checkIntervalInMillis = TimeUnit.HOURS.toMillis(2)
            return differenceMillis > checkIntervalInMillis
        }

        @Suppress("Detekt.TooGenericExceptionCaught")
        fun checkIfFollowedThreadsExist() {
            val threadsUrl = ApiUtils.getUrlForSubscribedThreads(
                version = 1,
                baseUrl = currentUser.baseUrl
            )

            viewModelScope.launch {
                try {
                    val threads =
                        threadsRepository.getThreads(credentials, threadsUrl, limitForFollowedThreadsExistenceCheck)
                    val followedThreadsExistNew = threads.ocs?.data?.isNotEmpty()
                    _threadsExistState.value = ThreadsExistUiState.Success(followedThreadsExistNew)
                    val followedThreadsExistLastCheckNew = System.currentTimeMillis()
                    arbitraryStorageManager.storeStorageSetting(
                        accountId,
                        FOLLOWED_THREADS_EXIST_LAST_CHECK,
                        followedThreadsExistLastCheckNew.toString(),
                        ""
                    )
                    arbitraryStorageManager.storeStorageSetting(
                        accountId,
                        FOLLOWED_THREADS_EXIST,
                        followedThreadsExistNew.toString(),
                        ""
                    )
                } catch (exception: Exception) {
                    _threadsExistState.value = ThreadsExistUiState.Error(exception)
                }
            }
        }

        if (!hasSpreedFeatureCapability(currentUser.capabilities?.spreedCapability, SpreedFeatures.THREADS)) {
            _threadsExistState.value = ThreadsExistUiState.Success(false)
            return
        }

        val followedThreadsExistOld = arbitraryStorageManager.getStorageSetting(
            accountId,
            FOLLOWED_THREADS_EXIST,
            ""
        ).blockingGet()?.value?.toBoolean() ?: false

        val followedThreadsExistLastCheckOld = arbitraryStorageManager.getStorageSetting(
            accountId,
            FOLLOWED_THREADS_EXIST_LAST_CHECK,
            ""
        ).blockingGet()?.value?.toLong()

        if (followedThreadsExistOld) {
            Log.d(TAG, "followed threads exist for this user. No need to check again.")
            _threadsExistState.value = ThreadsExistUiState.Success(true)
        } else {
            if (followedThreadsExistLastCheckOld == null || isLastCheckTooOld(followedThreadsExistLastCheckOld)) {
                Log.d(TAG, "check if followed threads exist never happened or is too old. Checking now...")
                checkIfFollowedThreadsExist()
            } else {
                _threadsExistState.value = ThreadsExistUiState.Success(false)
                Log.d(TAG, "already checked in the last 2 hours if followed threads exist. Skip check.")
            }
        }
    }

    suspend fun fetchOpenConversations(searchTerm: String) =
        withContext(Dispatchers.IO) {
            _openConversationsState.value = OpenConversationsUiState.None

            if (!hasSpreedFeatureCapability(
                    currentUser.capabilities?.spreedCapability,
                    SpreedFeatures.LISTABLE_ROOMS
                )
            ) {
                return@withContext
            }

            val apiVersion = ApiUtils.getConversationApiVersion(
                currentUser,
                intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1)
            )
            val url = ApiUtils.getUrlForOpenConversations(apiVersion, currentUser.baseUrl!!)

            openConversationsRepository.fetchConversations(currentUser, url, searchTerm)
                .onSuccess { conversations ->
                    if (conversations.isEmpty()) {
                        _openConversationsState.value = OpenConversationsUiState.None
                    } else {
                        _openConversationsState.value = OpenConversationsUiState.Success(conversations)
                    }
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to fetch conversations", exception)
                    _openConversationsState.value = OpenConversationsUiState.Error(exception)
                }
        }

    private fun buildConversationListEntries(
        rooms: List<ConversationModel>,
        filterState: Map<String, Boolean>,
        isSearchActive: Boolean,
        searchResults: List<ConversationListEntry>,
        hideToken: String?
    ): List<ConversationListEntry> {
        if (isSearchActive && searchResults.isNotEmpty()) return searchResults

        val hasFilterEnabled = filterState[MENTION] == true ||
            filterState[UNREAD] == true ||
            filterState[ARCHIVE] == true

        var filtered = rooms
            .filter { it.token != hideToken }
            .filter { conversation ->
                !(
                    conversation.objectType == ConversationEnums.ObjectType.ROOM &&
                        conversation.lobbyState == ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY
                    )
            }

        filtered = if (hasFilterEnabled) {
            filtered.filter { filterConversationModel(it, filterState) }
        } else {
            filtered.filter { !isFutureEvent(it) && !it.hasArchived }
        }

        val sorted = filtered.sortedWith(
            compareByDescending<ConversationModel> { it.favorite }
                .thenByDescending { it.lastActivity }
        )
        return sorted.map { ConversationListEntry.ConversationEntry(it) }
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun filterConversationModel(conversation: ConversationModel, filterState: Map<String, Boolean>): Boolean {
        var result = true
        for ((k, v) in filterState) {
            if (v) {
                when (k) {
                    MENTION -> result = (result && conversation.unreadMention) ||
                        (
                            result &&
                                (
                                    conversation.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
                                        conversation.type == ConversationEnums.ConversationType.FORMER_ONE_TO_ONE
                                    ) &&
                                (conversation.unreadMessages > 0)
                            )
                    UNREAD -> result = result && (conversation.unreadMessages > 0)
                    DEFAULT -> result = if (filterState[ARCHIVE] == true) {
                        result && conversation.hasArchived
                    } else {
                        result && !conversation.hasArchived
                    }
                }
            }
        }
        return result
    }

    private fun isFutureEvent(conversation: ConversationModel): Boolean {
        val eventTimeStart = conversation.objectId.substringBefore("#").toLongOrNull() ?: return false
        val currentTimeStampInSeconds = System.currentTimeMillis() / LONG_1000
        return conversation.objectType == ConversationEnums.ObjectType.EVENT &&
            (eventTimeStart - currentTimeStampInSeconds) > SIXTEEN_HOURS_IN_SECONDS
    }

    inner class FederatedInvitationsObserver : Observer<InvitationsModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(invitationsModel: InvitationsModel) {
            val currentUser = currentUserProvider.currentUser.blockingGet()

            if (invitationsModel.user.userId?.equals(currentUser.userId) == true &&
                invitationsModel.user.baseUrl?.equals(currentUser.baseUrl) == true
            ) {
                _federationInvitationHintVisible.value = invitationsModel.invitations.isNotEmpty()
            } else {
                if (invitationsModel.invitations.isNotEmpty()) {
                    _showBadgeViewState.value = ShowBadgeSuccessState(true)
                }
            }
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Failed to fetch pending invitations", e)
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = ConversationsListViewModel::class.simpleName
        const val FOLLOWED_THREADS_EXIST_LAST_CHECK = "FOLLOWED_THREADS_EXIST_LAST_CHECK"
        const val FOLLOWED_THREADS_EXIST = "FOLLOWED_THREADS_EXIST"
        private const val SIXTEEN_HOURS_IN_SECONDS: Long = 57600
        private const val LONG_1000: Long = 1000
        private const val WHILE_SUBSCRIBED_TIMEOUT_MS: Long = 5_000
    }
}
