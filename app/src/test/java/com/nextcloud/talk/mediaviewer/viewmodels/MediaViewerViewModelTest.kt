/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.mediaviewer.viewmodels

import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.mediaviewer.model.MediaViewerItem
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import com.nextcloud.talk.shareditems.repositories.SharedItemsRepository
import com.nextcloud.talk.utils.FileUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerViewModelTest {

    @get:Rule
    val cacheDir = TemporaryFolder()

    private val repository: SharedItemsRepository = mock()
    private val viewModel = MediaViewerViewModel(repository)
    private var previousApplication: NextcloudTalkApplication? = null

    // the setter of the singleton is protected: only the real application sets it
    private val sharedApplicationField = NextcloudTalkApplication::class.java
        .getDeclaredField("sharedApplication")
        .apply { isAccessible = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previousApplication = sharedApplicationField.get(null) as NextcloudTalkApplication?
        val application = mock<NextcloudTalkApplication>()
        whenever(application.cacheDir).thenReturn(cacheDir.root)
        sharedApplicationField.set(null, application)
    }

    @After
    fun tearDown() {
        sharedApplicationField.set(null, previousApplication)
        Dispatchers.resetMain()
    }

    @Test
    fun `a page with only shown items moves on to the next page`() {
        olderPage(cursor = 10, messageIds = listOf(10, 11), lastSeenId = 9, moreItemsExisting = true)
        olderPage(cursor = 9, messageIds = listOf(5, 6), lastSeenId = 5, moreItemsExisting = false)
        viewModel.initialize(USER, ROOM_TOKEN, items(10L..12L), startMessageId = 12)

        viewModel.onPageSettled(12)

        assertEquals(listOf(5L, 6L, 10L, 11L, 12L), shownMessageIds())
        assertEquals(12L, viewModel.uiState.value.currentItem?.messageId)
    }

    @Test
    fun `an item that settled while older items were added stays the current one`() {
        val page = CompletableDeferred<SharedItems>()
        wheneverBlocking { repository.media(any(), eq(SharedItemType.MEDIA), eq(10)) }
            .doSuspendableAnswer { page.await() }
        viewModel.initialize(USER, ROOM_TOKEN, items(10L..12L), startMessageId = 12)
        viewModel.onPageSettled(12)

        page.complete(sharedItems(listOf(5, 6), lastSeenId = 5, moreItemsExisting = false))
        viewModel.onPageSettled(11)

        assertEquals(11L, viewModel.uiState.value.currentItem?.messageId)
    }

    @Test
    fun `older items are loaded on while the shown item stays near the oldest one`() {
        olderPage(cursor = 10, messageIds = listOf(9), lastSeenId = 9, moreItemsExisting = true)
        olderPage(cursor = 9, messageIds = listOf(8), lastSeenId = 8, moreItemsExisting = false)
        viewModel.initialize(USER, ROOM_TOKEN, items(10L..12L), startMessageId = 10)

        viewModel.onPageSettled(10)

        assertEquals(listOf(8L, 9L, 10L, 11L, 12L), shownMessageIds())
        assertEquals(10L, viewModel.uiState.value.currentItem?.messageId)
    }

    @Test
    fun `older items are requested again after a failed request`() {
        wheneverBlocking { repository.media(any(), eq(SharedItemType.MEDIA), eq(10)) }
            .thenThrow(RuntimeException("server not reachable"))
            .thenReturn(sharedItems(listOf(5), lastSeenId = 5, moreItemsExisting = false))
        viewModel.initialize(USER, ROOM_TOKEN, items(10L..12L), startMessageId = 10)

        viewModel.onPageSettled(10)
        viewModel.onPageSettled(10)

        verifyBlocking(repository, times(2)) { media(any(), eq(SharedItemType.MEDIA), eq(10)) }
        assertEquals(listOf(5L, 10L, 11L, 12L), shownMessageIds())
    }

    private fun shownMessageIds() = viewModel.uiState.value.flattenedItems.map { it.messageId }

    private fun olderPage(cursor: Int, messageIds: List<Int>, lastSeenId: Int, moreItemsExisting: Boolean) {
        wheneverBlocking { repository.media(any(), eq(SharedItemType.MEDIA), eq(cursor)) }
            .thenReturn(sharedItems(messageIds, lastSeenId, moreItemsExisting))
    }

    private fun sharedItems(messageIds: List<Int>, lastSeenId: Int, moreItemsExisting: Boolean) =
        SharedItems(
            items = messageIds.map { sharedFileItem(it.toLong()) },
            type = SharedItemType.MEDIA,
            lastSeenId = lastSeenId,
            moreItemsExisting = moreItemsExisting
        )

    private fun sharedFileItem(messageId: Long): SharedFileItem {
        cache(fileId(messageId), fileName(messageId))
        return SharedFileItem(
            id = fileId(messageId),
            name = fileName(messageId),
            actorId = "actor",
            actorName = "Actor",
            dateTime = "",
            fileSize = 1024L,
            path = "/${fileName(messageId)}",
            link = "https://example.com/f/$messageId",
            mimeType = "image/jpeg",
            previewLink = "",
            messageId = messageId,
            referenceId = null,
            timestamp = messageId
        )
    }

    private fun items(messageIds: LongRange) =
        messageIds.map { messageId ->
            cache(fileId(messageId), fileName(messageId))
            MediaViewerItem(
                messageId = messageId,
                referenceId = null,
                fileId = fileId(messageId),
                fileName = fileName(messageId),
                mimeType = "image/jpeg",
                path = "/${fileName(messageId)}",
                link = "https://example.com/f/$messageId",
                fileSize = 1024L,
                previewUrl = null,
                actorDisplayName = "Actor",
                timestamp = messageId
            )
        }

    // A cached file keeps the viewer from downloading the item, which needs WorkManager.
    private fun cache(fileId: String, fileName: String) {
        FileUtils.resolveSharedAttachmentFile(cacheDir.root, fileId, fileName)!!.createNewFile()
    }

    private fun fileId(messageId: Long) = "file-$messageId"

    private fun fileName(messageId: Long) = "image-$messageId.jpg"

    private companion object {
        const val ROOM_TOKEN = "roomToken"
        val USER = User(id = 1, username = "user", token = "token", baseUrl = "https://example.com")
    }
}
