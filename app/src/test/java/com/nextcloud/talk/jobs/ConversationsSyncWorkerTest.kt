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
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [ConversationsSyncWorker]: which accounts a run syncs, when a run stands down without
 * syncing, and how a failed run reports itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ConversationsSyncWorkerTest {

    private val userManager: UserManager = mock()
    private val repository: OfflineConversationsRepository = mock()

    @Test
    fun `every account is synced, not just the current one`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2), user(3)))
        wheneverBlocking { repository.syncRooms(any(), any()) }.thenReturn(true)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(repository) { syncRooms(user(1), false) }
        verifyBlocking(repository) { syncRooms(user(2), false) }
        verifyBlocking(repository) { syncRooms(user(3), false) }
    }

    @Test
    fun `battery saver stands the sync down entirely`() {
        val worker = worker()
        shadowOf(applicationContext().getSystemService(Context.POWER_SERVICE) as PowerManager)
            .setIsPowerSaveMode(true)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(userManager, never()) { getUsers() }
        verifyBlocking(repository, never()) { syncRooms(any(), any()) }
    }

    @Test
    fun `an account with nothing to sync is not an error`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(emptyList())

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(repository, never()) { syncRooms(any(), any()) }
    }

    @Test
    fun `a failed account asks for another attempt`() {
        val worker = worker(runAttempt = 0)
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2)))
        wheneverBlocking { repository.syncRooms(user(1), false) }.thenReturn(true)
        wheneverBlocking { repository.syncRooms(user(2), false) }.thenReturn(false)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.retry(), result)
        verifyBlocking(repository) { syncRooms(user(1), false) }
    }

    @Test
    fun `a sync that keeps failing gives up instead of retrying for ever`() {
        val worker = worker(runAttempt = 2)
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1)))
        wheneverBlocking { repository.syncRooms(any(), any()) }.thenReturn(false)

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `an account whose lookup throws does not take the other accounts down with it`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenThrow(IllegalStateException("database is gone"))

        val result = runBlocking { worker.sync() }

        assertEquals(ListenableWorker.Result.success(), result)
        verifyBlocking(repository, never()) { syncRooms(any(), any()) }
    }

    @Test
    fun `a cancelled run stops instead of syncing the remaining accounts`() {
        val worker = worker()
        wheneverBlocking { userManager.getUsers() }.thenReturn(listOf(user(1), user(2), user(3)))
        wheneverBlocking { repository.syncRooms(any(), any()) }.thenThrow(CancellationException("run stopped"))

        assertThrows(CancellationException::class.java) { runBlocking { worker.sync() } }

        verifyBlocking(repository, times(1)) { syncRooms(any(), any()) }
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
    }
}
