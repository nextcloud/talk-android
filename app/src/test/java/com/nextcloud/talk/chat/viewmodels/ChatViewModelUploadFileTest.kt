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
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.message.SendMessageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

/**
 * The name, type and size of a file to send come from the provider of the app that owns it (Nextcloud Files):
 * a blocking binder call into another process. Sending a file must not make it on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatViewModelUploadFileTest {

    private val chatRepository: ChatMessageRepository = mock()
    private val placeholderAdded = CountDownLatch(1)
    private val placeholderMarkedFailed = CountDownLatch(1)
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

    @Test
    fun `sending a file does not ask its provider on the main thread`() {
        sendAndAwaitPlaceholder("document.pdf")

        assertTrue("placeholder message is added", placeholderAdded.count == 0L)
        assertEquals("provider calls on the main thread", 0, SlowProvider.mainThreadCalls.get())
        assertTrue("provider is asked for the type and the name", SlowProvider.otherCalls.get() >= 2)
    }

    @Test
    fun `a provider that rejects the uri still leaves a placeholder and does not crash`() {
        sendAndAwaitPlaceholder("rejected.pdf")

        assertTrue("placeholder message is added", placeholderAdded.count == 0L)
        assertEquals("provider calls on the main thread", 0, SlowProvider.mainThreadCalls.get())
    }

    @Test
    fun `the upload worker is enqueued at once, before the provider has answered`() {
        val viewModel = newViewModel()

        viewModel.send("slow-a.pdf", order = 1)

        assertEquals("uploads queued while the provider is still asked", 1, queuedUploads())
        assertEquals("placeholder is not added yet", 1L, placeholderAdded.count)
    }

    @Test
    fun `a view model cleared while the provider answers still has its upload enqueued`() {
        val viewModel = newViewModel()

        viewModel.send("slow-a.pdf", order = 1)
        viewModel.viewModelScope.cancel()

        assertEquals("uploads queued", 1, queuedUploads())
    }

    @Test
    fun `files are enqueued in the order they were sent whatever speed their providers answer with`() {
        val viewModel = newViewModel()

        viewModel.send("slow-a.pdf", order = 1)
        viewModel.send("b.pdf", order = 2)

        val workIds = viewModel.uploadWorkIds()
        val first = workIds.getValue(referenceId(1))
        val second = workIds.getValue(referenceId(2))
        val dependents = (WorkManager.getInstance(RuntimeEnvironment.getApplication()) as WorkManagerImpl)
            .workDatabase
            .dependencyDao()
            .getDependentWorkIds(first.toString())
        assertEquals("the second file waits for the first one", listOf(second.toString()), dependents)
    }

    @Test
    fun `a worker that fails before the placeholder exists does not leave the placeholder pending`() {
        val viewModel = newViewModel()

        viewModel.send("slow-a.pdf", order = 1)
        val workId = viewModel.uploadWorkIds().getValue(referenceId(1))
        WorkManagerTestInitHelper.getTestDriver(RuntimeEnvironment.getApplication())!!.setAllConstraintsMet(workId)

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (placeholderAdded.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            placeholderAdded.await(POLL_MS, TimeUnit.MILLISECONDS)
        }
        while (placeholderMarkedFailed.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            placeholderMarkedFailed.await(POLL_MS, TimeUnit.MILLISECONDS)
        }

        assertEquals("the placeholder is marked as failed", 0L, placeholderMarkedFailed.count)
    }

    private fun queuedUploads(): Int =
        WorkManager.getInstance(RuntimeEnvironment.getApplication())
            .getWorkInfosForUniqueWork("upload_queue_1@room")
            .get()
            .size

    private fun referenceId(order: Int) = SendMessageUtils().generateGroupedReferenceId(UPLOAD_ID, order)

    @Suppress("UNCHECKED_CAST")
    private fun ChatViewModel.uploadWorkIds(): Map<String, UUID> =
        ChatViewModel::class.java.getDeclaredField("uploadReferenceToWorkId")
            .apply { isAccessible = true }
            .get(this) as Map<String, UUID>

    private fun ChatViewModel.send(name: String, order: Int) {
        uploadFile(
            fileUri = "content://" + AUTHORITY + "/" + name,
            isVoiceMessage = false,
            displayName = "Team",
            uploadId = UPLOAD_ID,
            order = order
        )
    }

    private fun newViewModel(): ChatViewModel {
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

    private fun sendAndAwaitPlaceholder(name: String) {
        newViewModel().send(name, order = 1)

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (placeholderAdded.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            placeholderAdded.await(POLL_MS, TimeUnit.MILLISECONDS)
        }
    }

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

    companion object {
        private const val AUTHORITY = "com.nextcloud.client.documentsfileprovider"
        private const val PROVIDER_DELAY_MS = 300L
        private const val SLOW_DELAY_MS = 1500L
        private const val FILE_BYTES = 2048L
        private const val TIMEOUT_MS = 20_000L
        private const val POLL_MS = 50L
        private const val UPLOAD_ID = "upload"
    }
}
