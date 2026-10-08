/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.jobs

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Tests for [ConversationsSyncWorker]: which accounts a run syncs, when a run stands down without
 * syncing, and how a failed run reports itself.
 */
@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsSyncWorkerTest {

    private val userManager: UserManager = mock()
    private val repository: OfflineConversationsRepository = mock()

    @Test
    fun `every account is synced, not just the current one`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2), user(3)))
        wheneverBlocking { repository.syncRooms(any(), any(), anyOrNull()) }.thenReturn(NO_ROOMS)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(repository) { syncRooms(user(1), false, ROOM_LIST_TIMEOUT_MILLIS) }
        verifyBlocking(repository) { syncRooms(user(2), false, ROOM_LIST_TIMEOUT_MILLIS) }
        verifyBlocking(repository) { syncRooms(user(3), false, ROOM_LIST_TIMEOUT_MILLIS) }
    }

    @Test
    fun `battery saver stands the sync down entirely`() {
        val worker = worker()
        shadowOf(applicationContext().getSystemService(Context.POWER_SERVICE) as PowerManager)
            .setIsPowerSaveMode(true)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(userManager, never()) { getUsers() }
        verifyBlocking(repository, never()) { syncRooms(any(), any(), anyOrNull()) }
    }

    @Test
    fun `an app in the foreground stands the sync down entirely`() {
        val worker = worker()
        moveAppTo(Lifecycle.State.STARTED)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(userManager, never()) { getUsers() }
        verifyBlocking(repository, never()) { syncRooms(any(), any(), anyOrNull()) }
    }

    @Test
    fun `an account with nothing to sync is not an error`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(emptyList())

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(repository, never()) { syncRooms(any(), any(), anyOrNull()) }
    }

    @Test
    fun `a failed account asks for another attempt`() {
        val worker = worker(runAttempt = 0)
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2)))
        wheneverBlocking { repository.syncRooms(user(1), false, ROOM_LIST_TIMEOUT_MILLIS) }.thenReturn(NO_ROOMS)
        wheneverBlocking { repository.syncRooms(user(2), false, ROOM_LIST_TIMEOUT_MILLIS) }.thenReturn(null)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.retry(), result)
        verifyBlocking(repository) { syncRooms(user(1), false, ROOM_LIST_TIMEOUT_MILLIS) }
        verifyBlocking(repository) { catchUpRooms(user(1), NO_ROOMS) }
        verifyBlocking(repository, never()) { catchUpRooms(eq(user(2)), any()) }
    }

    @Test
    fun `every room list is synced before any messages are caught up`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2)))
        wheneverBlocking { repository.syncRooms(any(), any(), anyOrNull()) }.thenReturn(NO_ROOMS)

        runBlocking { worker.sync() }

        val order = inOrder(repository)
        runBlocking {
            order.verify(repository).syncRooms(eq(user(1)), any(), anyOrNull())
            order.verify(repository).syncRooms(eq(user(2)), any(), anyOrNull())
            order.verify(repository).catchUpRooms(user(1), NO_ROOMS)
            order.verify(repository).catchUpRooms(user(2), NO_ROOMS)
        }
    }

    @Test
    fun `a failed message catch-up does not fail the run`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2)))
        wheneverBlocking { repository.syncRooms(any(), any(), anyOrNull()) }.thenReturn(NO_ROOMS)
        wheneverBlocking { repository.catchUpRooms(eq(user(1)), any()) }
            .thenThrow(IllegalStateException("database is gone"))

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(repository) { catchUpRooms(user(2), NO_ROOMS) }
    }

    @Test
    fun `a sync that keeps failing gives up instead of retrying for ever`() {
        val worker = worker(runAttempt = 2)
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1)))
        wheneverBlocking { repository.syncRooms(any(), any(), anyOrNull()) }.thenReturn(null)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `the account that waited longest is synced first`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2), user(3)))
        wheneverBlocking { repository.lastFullSyncAt(1) }.thenReturn(300)
        wheneverBlocking { repository.lastFullSyncAt(2) }.thenReturn(null)
        wheneverBlocking { repository.lastFullSyncAt(3) }.thenReturn(100)
        wheneverBlocking { repository.syncRooms(any(), any(), anyOrNull()) }.thenReturn(NO_ROOMS)

        runBlocking { worker.sync() }

        val synced = argumentCaptor<User>()
        verifyBlocking(repository, times(3)) { syncRooms(synced.capture(), any(), anyOrNull()) }
        assertEquals(listOf(2L, 3L, 1L), synced.allValues.map { it.id })
        verifyBlocking(repository, times(1)) { lastFullSyncAt(1) }
        verifyBlocking(repository, times(1)) { lastFullSyncAt(2) }
        verifyBlocking(repository, times(1)) { lastFullSyncAt(3) }
    }

    @Test
    fun `a failed read of the sync times keeps the account order and still syncs every account`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2), user(3)))
        wheneverBlocking { repository.lastFullSyncAt(any()) }.thenThrow(IllegalStateException("database is gone"))
        wheneverBlocking { repository.syncRooms(any(), any(), anyOrNull()) }.thenReturn(NO_ROOMS)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        val synced = argumentCaptor<User>()
        verifyBlocking(repository, times(3)) { syncRooms(synced.capture(), any(), anyOrNull()) }
        assertEquals(listOf(1L, 2L, 3L), synced.allValues.map { it.id })
    }

    @Test
    fun `the app coming to the foreground stops the room lists but not the messages already due`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2)))
        wheneverBlocking { repository.syncRooms(eq(user(1)), any(), anyOrNull()) }.thenAnswer {
            moveAppTo(Lifecycle.State.STARTED)
            NO_ROOMS
        }

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(repository, never()) { syncRooms(eq(user(2)), any(), anyOrNull()) }
        verifyBlocking(repository) { catchUpRooms(user(1), NO_ROOMS) }
    }

    @Test
    fun `a failed account lookup is retried rather than reported as a sync`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenThrow(IllegalStateException("database is gone"))

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.retry(), result)
        verifyBlocking(repository, never()) { syncRooms(any(), any(), anyOrNull()) }
    }

    @Test
    fun `a last attempt whose account lookup fails reports failure`() {
        val worker = worker(runAttempt = 2)
        wheneverBlocking { userManager.getUsers() }.thenThrow(IllegalStateException("database is gone"))

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `a cancelled run stops instead of syncing the remaining accounts`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2), user(3)))
        wheneverBlocking { repository.syncRooms(any(), any(), anyOrNull()) }
            .thenThrow(CancellationException("run stopped"))

        assertThrows(CancellationException::class.java) { runBlocking { worker.sync() } }

        verifyBlocking(repository, times(1)) { syncRooms(any(), any(), anyOrNull()) }
    }

    @Test
    fun `scheduling again updates the scheduled run instead of keeping or replacing it`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(applicationContext())
        val workManager = WorkManager.getInstance(applicationContext())

        ConversationsSyncWorker.schedule(applicationContext())
        val first = workManager.getWorkInfosForUniqueWork(ConversationsSyncWorker.UNIQUE_WORK_NAME).get().single()
        ConversationsSyncWorker.schedule(applicationContext())
        val second = workManager.getWorkInfosForUniqueWork(ConversationsSyncWorker.UNIQUE_WORK_NAME).get().single()

        assertEquals(first.id, second.id)
        assertEquals(first.generation + 1, second.generation)
        assertEquals(WorkInfo.State.ENQUEUED, second.state)
    }

    @After
    fun tearDown() {
        moveAppTo(Lifecycle.State.CREATED)
    }

    /** Moves the process lifecycle, which outlives a single test, to [state]. */
    private fun moveAppTo(state: Lifecycle.State) {
        (ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).currentState = state
    }

    private fun worker(runAttempt: Int = 0): ConversationsSyncWorker =
        TestListenableWorkerBuilder<ConversationsSyncWorker>(applicationContext())
            .setRunAttemptCount(runAttempt)
            .build()
            .also {
                it.userManager = userManager
                it.conversationsRepository = repository
            }

    private fun applicationContext(): Context = ApplicationProvider.getApplicationContext()

    private fun user(id: Long): User = User(id = id, userId = "user$id", username = "user$id", baseUrl = BASE_URL)

    companion object {
        private const val BASE_URL = "https://server.example.com"
        private val ROOM_LIST_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2)
        private val NO_ROOMS = emptyList<ConversationEntity>()
    }
}
