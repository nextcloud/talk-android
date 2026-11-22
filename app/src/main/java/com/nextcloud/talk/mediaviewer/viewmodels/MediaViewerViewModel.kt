/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.viewmodels

import android.util.Log
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.await
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
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.mediaviewer.stream.VideoDownloadRegistry
import com.nextcloud.talk.mediaviewer.stream.VideoStreamSource
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
class MediaViewerViewModel @Inject constructor(
    private val sharedItemsRepository: SharedItemsRepository,
    private val ncApi: NcApi
) : ViewModel() {

    data class PendingShift(val id: Long, val amount: Int)

    data class UiState(
        val groups: List<MediaViewerGroup> = emptyList(),
        val currentGlobalIndex: Int = 0,
        val loadingOlder: Boolean = false,
        val canLoadOlder: Boolean = true,
        val cachedFilePaths: Map<Long, String> = emptyMap(),
        val downloadingMessageIds: Set<Long> = emptySet(),
        val failedMessageIds: Set<Long> = emptySet(),
        val downloadProgress: Map<Long, Int> = emptyMap(),
        val videoStreams: Map<Long, VideoStreamSource> = emptyMap(),
        val pendingShift: PendingShift? = null
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
    private var nextShiftId = 0L

    private lateinit var user: User
    private lateinit var repositoryParameters: SharedItemsRepository.Parameters
    private var oldestKnownMessageId: Long? = null

    fun initialize(user: User, roomToken: String, seedItems: List<MediaViewerItem>, startMessageId: Long) {
        viewModelScope.coroutineContext.cancelChildren()
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
        ensureCurrentCached(startIndex)
    }

    fun onPageSettled(globalIndex: Int) {
        _uiState.update { it.copy(currentGlobalIndex = globalIndex) }
        ensureCurrentCached(globalIndex)
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

    private fun ensureCurrentCached(globalIndex: Int) {
        _uiState.value.flattenedItems.getOrNull(globalIndex)?.let(::ensureCached)
    }

    fun retryDownload(messageId: Long) {
        _uiState.value.flattenedItems.firstOrNull { it.messageId == messageId }?.let(::ensureCached)
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

        _uiState.update {
            it.copy(
                downloadingMessageIds = it.downloadingMessageIds + item.messageId,
                failedMessageIds = it.failedMessageIds - item.messageId,
                downloadProgress = it.downloadProgress - item.messageId
            )
        }

        val workName = "media-download-${user.id}-${item.fileId}-${item.fileName}"
        val data = Data.Builder()
            .putString(DownloadFileToCacheWorker.KEY_STREAM_KEY, workName.takeIf { item.mimeType.startsWith("video/") })
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
        observeDownload(context, workName, request, item)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun observeDownload(
        context: Context,
        workName: String,
        request: OneTimeWorkRequest,
        item: MediaViewerItem
    ) {
        viewModelScope.launch {
            try {
                val manager = WorkManager.getInstance(context)
                manager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request).await()
                manager.getWorkInfosForUniqueWorkLiveData(workName).asFlow().first { workInfos ->
                    val workInfo = workInfos.firstOrNull { it.id == request.id } ?: workInfos.singleOrNull()
                    if (workInfo == null) return@first false
                    updateDownload(context, item, workInfo, workName)
                    workInfo.state.isFinished
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to observe media download", exception)
                markDownloadFailed(item.messageId)
            }
        }
    }

    private fun updateDownload(context: Context, item: MediaViewerItem, workInfo: WorkInfo, workName: String) {
        when (workInfo.state) {
            WorkInfo.State.SUCCEEDED -> {
                val downloaded = FileUtils.resolveSharedAttachmentFile(context.cacheDir, item.fileId, item.fileName)
                    ?.takeIf { it.isFile }
                _uiState.update {
                    it.copy(
                        downloadingMessageIds = it.downloadingMessageIds - item.messageId,
                        downloadProgress = it.downloadProgress + (item.messageId to 100),
                        failedMessageIds = if (downloaded != null) {
                            it.failedMessageIds - item.messageId
                        } else {
                            it.failedMessageIds + item.messageId
                        },
                        cachedFilePaths = downloaded?.let { file ->
                            it.cachedFilePaths + (item.messageId to file.absolutePath)
                        } ?: it.cachedFilePaths
                    )
                }
            }
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> markDownloadFailed(item.messageId)
            WorkInfo.State.RUNNING -> updateRunningDownload(item, workInfo, workName)
            else -> Unit
        }
    }

    private fun updateRunningDownload(item: MediaViewerItem, workInfo: WorkInfo, workName: String) {
        val source = if (item.messageId !in _uiState.value.videoStreams) createVideoStream(item, workName) else null
        _uiState.update {
            it.copy(
                downloadProgress = it.downloadProgress +
                    (item.messageId to workInfo.progress.getInt(DownloadFileToCacheWorker.PROGRESS, -1)),
                videoStreams = source?.let { stream -> it.videoStreams + (item.messageId to stream) } ?: it.videoStreams
            )
        }
    }

    private fun createVideoStream(item: MediaViewerItem, workName: String): VideoStreamSource? {
        val download = VideoDownloadRegistry.find(workName) ?: return null
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        val url = ApiUtils.getUrlForFileDownload(user.baseUrl!!, user.userId!!, item.path)
        return VideoStreamSource(download) { position, validator ->
            ncApi.downloadVideoRange(credentials, url, "bytes=$position-", validator)
        }
    }

    private fun markDownloadFailed(messageId: Long) {
        _uiState.update {
            it.copy(
                downloadingMessageIds = it.downloadingMessageIds - messageId,
                failedMessageIds = it.failedMessageIds + messageId,
                videoStreams = it.videoStreams - messageId
            )
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
            } catch (e: CancellationException) {
                throw e
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
        private const val EDGE_LOAD_THRESHOLD = 1
    }
}
