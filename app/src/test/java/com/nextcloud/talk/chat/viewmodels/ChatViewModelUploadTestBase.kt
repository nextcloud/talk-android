/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.viewmodels

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.message.SendMessageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal const val AUTHORITY = "com.nextcloud.client.documentsfileprovider"
internal const val PROVIDER_DELAY_MS = 300L
internal const val SLOW_DELAY_MS = 1500L
internal const val FILE_BYTES = 2048L
internal const val TIMEOUT_MS = 20_000L
internal const val POLL_MS = 50L
internal const val MARK_WAIT_MS = 3_000L
internal const val UPLOAD_ID = "upload"

class SlowProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String {
        count(uri)
        require(uri.lastPathSegment != "rejected.pdf") { "Unknown URL " + uri }
        return "application/pdf"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        count(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any?>(uri.lastPathSegment, FILE_BYTES))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun count(uri: Uri) {
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        (if (onMain) mainThreadCalls else otherCalls).incrementAndGet()
        Thread.sleep(if (uri.lastPathSegment.orEmpty().startsWith("slow")) SLOW_DELAY_MS else PROVIDER_DELAY_MS)
    }

    companion object {
        val mainThreadCalls = AtomicInteger()
        val otherCalls = AtomicInteger()

        fun reset() {
            mainThreadCalls.set(0)
            otherCalls.set(0)
        }
    }
}

/**
 * What the upload tests of the view model share: a work manager, a provider that answers slowly, a view model
 * and the wait for its placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
abstract class ChatViewModelUploadTestBase {

    protected val chatRepository: ChatMessageRepository = mock()
    protected val placeholderAdded = CountDownLatch(1)
    protected val placeholderMarkedFailed = CountDownLatch(1)
    private var previousApplication: NextcloudTalkApplication? = null

    // the setter of the singleton is protected: only the real application sets it
    private val sharedApplicationField = NextcloudTalkApplication::class.java
        .getDeclaredField("sharedApplication")
        .apply { isAccessible = true }

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        previousApplication = sharedApplicationField.get(null) as NextcloudTalkApplication?
        val application = mock<NextcloudTalkApplication>()
        whenever(application.contentResolver).thenReturn(context.contentResolver)
        whenever(application.applicationContext).thenReturn(context)
        sharedApplicationField.set(null, application)

        SlowProvider.reset()
        Robolectric.buildContentProvider(SlowProvider::class.java).create(AUTHORITY)
        runBlocking {
            whenever(chatRepository.addUploadPlaceholderMessage(any(), any(), any(), anyOrNull(), any(), any()))
                .thenAnswer {
                    placeholderAdded.countDown()
                    flowOf<Result<ChatMessage?>>()
                }
            whenever(chatRepository.markUploadPlaceholderFailed(any())).thenAnswer {
                placeholderMarkedFailed.countDown()
            }
        }
    }

    @After
    fun tearDown() {
        sharedApplicationField.set(null, previousApplication)
    }

    protected fun queuedUploads(): Int =
        WorkManager.getInstance(RuntimeEnvironment.getApplication())
            .getWorkInfosForUniqueWork("upload_queue_1@room")
            .get()
            .size

    protected fun referenceId(order: Int) = SendMessageUtils().generateGroupedReferenceId(UPLOAD_ID, order)

    @Suppress("UNCHECKED_CAST")
    protected fun ChatViewModel.uploadWorkIds(): Map<String, UUID> =
        ChatViewModel::class.java.getDeclaredField("uploadReferenceToWorkId")
            .apply { isAccessible = true }
            .get(this) as Map<String, UUID>

    protected fun ChatViewModel.send(name: String, order: Int) {
        uploadFile(
            fileUri = "content://" + AUTHORITY + "/" + name,
            isVoiceMessage = false,
            displayName = "Team",
            uploadId = UPLOAD_ID,
            order = order
        )
    }

    protected fun newViewModel(): ChatViewModel {
        val user = User().apply { id = 1L }
        val viewModel = ChatViewModel(
            logger = mock(),
            appPreferences = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            chatNetworkDataSource = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            chatRepository = chatRepository,
            threadsRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            conversationRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            reactionsRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            unifiedSearchRepository = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            mediaRecorderManager = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            audioFocusRequestManager = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            userManager = mock(defaultAnswer = RETURNS_DEEP_STUBS),
            appScope = CoroutineScope(Dispatchers.Unconfined),
            initialUser = user,
            chatRoomToken = "room",
            conversationThreadId = null
        )
        viewModel.participantPermissions = Mockito.mock(ParticipantPermissions::class.java).also {
            whenever(it.hasChatPermission()).thenReturn(true)
        }
        return viewModel
    }

    protected fun sendAndAwaitPlaceholder(name: String) {
        newViewModel().send(name, order = 1)

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (placeholderAdded.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            placeholderAdded.await(POLL_MS, TimeUnit.MILLISECONDS)
        }
    }
}
