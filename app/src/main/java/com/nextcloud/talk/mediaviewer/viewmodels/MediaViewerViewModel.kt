/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.setExpeditedIfSupported
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ui.MessageActionsState
import com.nextcloud.talk.chat.ui.buildMessageActionsState
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.mappers.toDomainModel
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.mediaviewer.model.MediaViewerCounter
import com.nextcloud.talk.mediaviewer.model.mediaViewerCounter
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker
import com.nextcloud.talk.mediaviewer.model.MediaViewerGroup
import com.nextcloud.talk.mediaviewer.model.MediaViewerItem
import com.nextcloud.talk.mediaviewer.model.isImageOrVideo
import com.nextcloud.talk.mediaviewer.model.toMediaViewerGroups
import com.nextcloud.talk.mediaviewer.model.toMediaViewerItem
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepository
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.FileUtils
import io.reactivex.Observable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Backs the swipeable media viewer: a flattened, chronologically ordered (oldest first) sequence
 * of [MediaViewerGroup]s. Seeded synchronously from whatever the chat screen already had loaded
 * (see ChatViewModel.mediaViewerSeed()) - which alone covers all "newer" navigation, since that
 * direction has no network fallback (see class doc on the pending-index-shift mechanism below for
 * why "older" does). "Older" navigation pages further back through conversation history via
 * [SharedItemsRepository] once the locally seeded items run out.
 */
@Suppress("LongParameterList")
class MediaViewerViewModel @Inject constructor(
    private val sharedItemsRepository: SharedItemsRepository,
    private val chatMessagesDao: ChatMessagesDao,
    private val conversationsRepository: OfflineConversationsRepository,
    private val networkMonitor: NetworkMonitor,
    private val context: Context
) : ViewModel() {

    data class PendingShift(val id: Long, val amount: Int)

    data class UiState(
        val groups: List<MediaViewerGroup> = emptyList(),
        val currentGlobalIndex: Int = 0,
        val loadingOlder: Boolean = false,
        val canLoadOlder: Boolean = true,
        val cachedFilePaths: Map<Long, String> = emptyMap(),
        val downloadingMessageIds: Set<Long> = emptySet(),
        val pendingShift: PendingShift? = null,
        /** Per message id: what the chat allows for it. A message missing here is not in the local database. */
        val actionStates: Map<Long, MessageActionsState> = emptyMap(),
        val conversation: ConversationModel? = null
    ) {
        val counter: MediaViewerCounter?
            get() = mediaViewerCounter(currentGlobalIndex, flattenedItems.size, canLoadOlder)

        val flattenedItems: List<MediaViewerItem> get() = groups.flatMap { it.items }
        val currentItem: MediaViewerItem? get() = flattenedItems.getOrNull(currentGlobalIndex)
        val currentGroup: MediaViewerGroup?
            get() {
                val item = currentItem ?: return null
                return groups.firstOrNull { group -> group.items.any { it.messageId == item.messageId } }
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private var nextShiftId = 0L

    private lateinit var user: User
    private lateinit var repositoryParameters: SharedItemsRepository.Parameters
    private var oldestKnownMessageId: Long? = null

    fun initialize(user: User, roomToken: String, seedItems: List<MediaViewerItem>, startMessageId: Long) {
        this.user = user
        repositoryParameters = SharedItemsRepository.Parameters(
            // The login name, as it authenticates the requests. The user id can differ from it.
            user.username!!,
            user.token!!,
            user.baseUrl!!,
            roomToken
        )
        val groups = seedItems.toMediaViewerGroups()
        val startIndex = seedItems.indexOfFirst { it.messageId == startMessageId }.coerceAtLeast(0)
        oldestKnownMessageId = seedItems.minOfOrNull { it.messageId }

        _uiState.value = UiState(groups = groups, currentGlobalIndex = startIndex)
        ensureCachedAround(startIndex)
        loadConversation(roomToken)
        observeOnlineState()
    }

    /** What the chat offers depends on being online (forwarding, deleting), so the states are worked out again. */
    private fun observeOnlineState() {
        viewModelScope.launch {
            networkMonitor.isOnline.drop(1).collect {
                _uiState.update { state -> state.copy(actionStates = emptyMap()) }
                _uiState.value.currentItem?.let(::loadActionState)
            }
        }
    }

    private fun loadConversation(roomToken: String) {
        viewModelScope.launch {
            @Suppress("DEPRECATION")
            val conversation = conversationsRepository.getLocallyStoredConversation(user, roomToken)
            _uiState.update { it.copy(conversation = conversation) }
            // Items may have been settled before the conversation was known.
            _uiState.value.currentItem?.let(::loadActionState)
        }
    }

    /**
     * Works out what the chat allows for [item] with the chat's own rules (`buildMessageActionsState`), from the
     * message and the conversation in the local database. Nothing is stored when the message is not there (an item
     * paged in from the network), so the actions that need permissions stay hidden.
     */
    private fun loadActionState(item: MediaViewerItem) {
        val state = _uiState.value
        val conversation = state.conversation
        if (conversation == null || state.actionStates.containsKey(item.messageId)) return
        viewModelScope.launch {
            val message = chatMessagesDao
                .getChatMessageEntity("${user.id}@${conversation.token}", item.messageId)
                ?.toDomainModel()
                ?: return@launch
            val capabilities = user.capabilities?.spreedCapability ?: return@launch
            val permissions = ParticipantPermissions(capabilities, conversation)
            val actions = buildMessageActionsState(
                message = message,
                user = user,
                conversation = conversation,
                hasChatPermission = permissions.hasChatPermission(),
                hasReactPermission = permissions.hasReactPermission(),
                spreedCapabilities = capabilities,
                isOnline = networkMonitor.isOnline.value,
                dateUtils = DateUtils(context),
                conversationThreadId = null
            )
            _uiState.update { it.copy(actionStates = it.actionStates + (item.messageId to actions)) }
        }
    }

    fun onPageSettled(globalIndex: Int) {
        _uiState.update { it.copy(currentGlobalIndex = globalIndex) }
        ensureCachedAround(globalIndex)
        _uiState.value.currentItem?.let(::loadActionState)
        if (globalIndex <= EDGE_LOAD_THRESHOLD) {
            loadOlderGroups()
        }
    }

    fun jumpTo(globalIndex: Int) {
        onPageSettled(globalIndex)
    }

    fun consumePendingShift(id: Long) {
        _uiState.update { if (it.pendingShift?.id == id) it.copy(pendingShift = null) else it }
    }

    private fun ensureCachedAround(globalIndex: Int) {
        val items = _uiState.value.flattenedItems
        for (index in (globalIndex - PREFETCH_RADIUS)..(globalIndex + PREFETCH_RADIUS)) {
            items.getOrNull(index)?.let(::ensureCached)
        }
    }

    private fun ensureCached(item: MediaViewerItem) {
        if (_uiState.value.cachedFilePaths.containsKey(item.messageId) ||
            _uiState.value.downloadingMessageIds.contains(item.messageId)
        ) {
            return
        }

        val context = NextcloudTalkApplication.sharedApplication!!
        val existing = FileUtils.resolveSharedAttachmentFile(context.cacheDir, item.fileId, item.fileName)
        if (existing != null && existing.exists()) {
            _uiState.update {
                it.copy(cachedFilePaths = it.cachedFilePaths + (item.messageId to existing.absolutePath))
            }
            return
        }

        _uiState.update { it.copy(downloadingMessageIds = it.downloadingMessageIds + item.messageId) }

        val data = Data.Builder()
            .putLong(BundleKeys.KEY_INTERNAL_USER_ID, user.id!!)
            .putString(
                DownloadFileToCacheWorker.KEY_ATTACHMENT_FOLDER,
                CapabilitiesUtil.getAttachmentFolder(user.capabilities!!.spreedCapability!!)
            )
            .putString(DownloadFileToCacheWorker.KEY_FILE_ID, item.fileId)
            .putString(DownloadFileToCacheWorker.KEY_FILE_NAME, item.fileName)
            .putString(DownloadFileToCacheWorker.KEY_FILE_PATH, item.path)
            .putLong(DownloadFileToCacheWorker.KEY_FILE_SIZE, item.fileSize)
            .build()

        val request = OneTimeWorkRequest.Builder(DownloadFileToCacheWorker::class.java)
            .setInputData(data)
            .addTag(item.fileId)
            .setExpeditedIfSupported()
            .build()
        WorkManager.getInstance(context).enqueue(request)

        viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfoByIdLiveData(request.id).asFlow().collect { workInfo ->
                if (workInfo == null) return@collect
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val downloaded = FileUtils.resolveSharedAttachmentFile(
                            context.cacheDir,
                            item.fileId,
                            item.fileName
                        )
                        _uiState.update {
                            it.copy(
                                downloadingMessageIds = it.downloadingMessageIds - item.messageId,
                                cachedFilePaths = downloaded?.let { file ->
                                    it.cachedFilePaths + (item.messageId to file.absolutePath)
                                } ?: it.cachedFilePaths
                            )
                        }
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _uiState.update { it.copy(downloadingMessageIds = it.downloadingMessageIds - item.messageId) }
                    }
                    else -> Unit
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadOlderGroups() {
        val state = _uiState.value
        if (state.loadingOlder || !state.canLoadOlder) return
        val cursor = oldestKnownMessageId ?: run {
            _uiState.update { it.copy(canLoadOlder = false) }
            return
        }

        _uiState.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val sharedItems = try {
                sharedItemsRepository.media(repositoryParameters, SharedItemType.MEDIA, cursor.toInt())?.awaitFirst()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load older shared items", e)
                null
            }

            val olderItems = sharedItems?.items.orEmpty()
                .filterIsInstance<SharedFileItem>()
                .filter { it.isImageOrVideo() }
                .map { it.toMediaViewerItem() }
                .sortedBy { it.messageId }

            if (olderItems.isEmpty()) {
                _uiState.update { it.copy(loadingOlder = false, canLoadOlder = false) }
                return@launch
            }

            oldestKnownMessageId = olderItems.first().messageId
            _uiState.update { current ->
                val combined = (olderItems + current.flattenedItems).toMediaViewerGroups()
                current.copy(
                    groups = combined,
                    currentGlobalIndex = current.currentGlobalIndex + olderItems.size,
                    loadingOlder = false,
                    canLoadOlder = sharedItems?.moreItemsExisting == true,
                    pendingShift = PendingShift(id = nextShiftId++, amount = olderItems.size)
                )
            }
        }
    }

    private suspend fun Observable<SharedItems>.awaitFirst(): SharedItems =
        suspendCancellableCoroutine { continuation ->
            val disposable = subscribe(
                { continuation.resume(it) },
                { continuation.resumeWithException(it) }
            )
            continuation.invokeOnCancellation { disposable.dispose() }
        }

    companion object {
        private val TAG = MediaViewerViewModel::class.simpleName
        private const val PREFETCH_RADIUS = 1
        private const val EDGE_LOAD_THRESHOLD = 1
    }
}
