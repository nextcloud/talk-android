/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.viewmodels

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
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker
import com.nextcloud.talk.mediaviewer.model.MediaViewerGroup
import com.nextcloud.talk.mediaviewer.model.MediaViewerItem
import com.nextcloud.talk.mediaviewer.model.isImageOrVideo
import com.nextcloud.talk.mediaviewer.model.toMediaViewerGroups
import com.nextcloud.talk.mediaviewer.model.toMediaViewerItem
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepository
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the swipeable media viewer: a flattened, chronologically ordered (oldest first) sequence
 * of [MediaViewerGroup]s. Seeded synchronously from whatever the chat screen already had loaded
 * (see ChatViewModel.mediaViewerSeed()) - which alone covers all "newer" navigation, since that
 * direction has no network fallback. "Older" navigation pages further back through conversation
 * history via [SharedItemsRepository] before the locally seeded items run out.
 */
class MediaViewerViewModel @Inject constructor(private val sharedItemsRepository: SharedItemsRepository) :
    ViewModel() {

    data class UiState(
        val groups: List<MediaViewerGroup> = emptyList(),
        val currentGlobalIndex: Int = 0,
        val loadingOlder: Boolean = false,
        val canLoadOlder: Boolean = true,
        val cachedFilePaths: Map<Long, String> = emptyMap(),
        val downloadingMessageIds: Set<Long> = emptySet()
    ) {
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

    private lateinit var user: User
    private lateinit var repositoryParameters: SharedItemsRepository.Parameters
    private var olderPagesCursor: Long? = null

    /** Seeds the viewer with [seedItems] showing [startMessageId]; once seeded, later calls keep the current state. */
    fun initialize(user: User, roomToken: String, seedItems: List<MediaViewerItem>, startMessageId: Long) {
        if (this::user.isInitialized) return
        this.user = user
        repositoryParameters = SharedItemsRepository.Parameters(
            // The login name, as it authenticates the requests. The user id can differ from it.
            user.username!!,
            user.token!!,
            user.baseUrl!!,
            roomToken
        )
        val items = seedItems.distinctBy { it.messageId }
        val groups = items.toMediaViewerGroups()
        val startIndex = items.indexOfFirst { it.messageId == startMessageId }.coerceAtLeast(0)
        olderPagesCursor = items.minOfOrNull { it.messageId }

        _uiState.value = UiState(groups = groups, currentGlobalIndex = startIndex)
        ensureCachedAround(startIndex)
    }

    /**
     * Makes the item of [messageId] the current one. Identifies the item by its message id, as the index of the
     * page the pager settled on can be outdated by older items that were added in the meantime.
     */
    fun onPageSettled(messageId: Long) {
        val globalIndex = _uiState.value.flattenedItems.indexOfFirst { it.messageId == messageId }
        if (globalIndex >= 0) {
            show(globalIndex)
        }
    }

    fun jumpTo(globalIndex: Int) {
        show(globalIndex)
    }

    private fun show(globalIndex: Int) {
        _uiState.update { it.copy(currentGlobalIndex = globalIndex) }
        ensureCachedAround(globalIndex)
        loadOlderGroupsIfNearOldest()
    }

    private fun loadOlderGroupsIfNearOldest() {
        if (_uiState.value.currentGlobalIndex < EDGE_LOAD_THRESHOLD) {
            loadOlderGroups()
        }
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

    private fun loadOlderGroups() {
        val state = _uiState.value
        if (state.loadingOlder || !state.canLoadOlder) return
        val cursor = olderPagesCursor ?: run {
            _uiState.update { it.copy(canLoadOlder = false) }
            return
        }

        _uiState.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val olderPage = loadOlderPage(cursor) ?: run {
                _uiState.update { it.copy(loadingOlder = false) }
                return@launch
            }
            val knownMessageIds = _uiState.value.flattenedItems.mapTo(HashSet()) { it.messageId }
            val olderItems = olderPage.items
                .distinctBy { it.messageId }
                .filterNot { it.messageId in knownMessageIds }

            // A page whose items are all shown already still moves the cursor on; only the server ends the paging.
            olderPagesCursor = olderPage.cursor
            _uiState.update { current ->
                val groups = if (olderItems.isEmpty()) {
                    current.groups
                } else {
                    (olderItems + current.flattenedItems).toMediaViewerGroups()
                }
                current.copy(
                    groups = groups,
                    currentGlobalIndex = current.currentGlobalIndex + olderItems.size,
                    loadingOlder = false,
                    canLoadOlder = olderPage.items.isNotEmpty() &&
                        olderPage.moreItemsExisting &&
                        olderPage.cursor != null &&
                        olderPage.cursor != cursor
                )
            }
            // Items shown while this page loaded did not start a request, and the page may not have added enough.
            loadOlderGroupsIfNearOldest()
        }
    }

    private class OlderPage(val items: List<MediaViewerItem>, val cursor: Long?, val moreItemsExisting: Boolean)

    /**
     * Loads the images and videos shared before [startCursor], oldest first. Pages without any are skipped while
     * the server reports more. Returns null if a request fails.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadOlderPage(startCursor: Long): OlderPage? {
        var cursor = startCursor
        while (true) {
            val sharedItems = try {
                sharedItemsRepository.media(repositoryParameters, SharedItemType.MEDIA, cursor.toInt())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load older shared items", e)
                return null
            }
            val items = sharedItems.items
                .filterIsInstance<SharedFileItem>()
                .filter { it.isImageOrVideo() }
                .map { it.toMediaViewerItem() }
                .sortedBy { it.messageId }
            val nextCursor = sharedItems.lastSeenId?.toLong()
            if (items.isNotEmpty() || !sharedItems.moreItemsExisting || nextCursor == null || nextCursor == cursor) {
                return OlderPage(items, nextCursor, sharedItems.moreItemsExisting)
            }
            cursor = nextCursor
        }
    }

    companion object {
        private val TAG = MediaViewerViewModel::class.simpleName
        private const val PREFETCH_RADIUS = 1
        private const val EDGE_LOAD_THRESHOLD = 10
    }
}
