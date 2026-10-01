/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UsersDaoRepairTest {

    private lateinit var db: TalkDatabase
    private lateinit var dao: UsersDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TalkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.usersDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `several active users are reduced to the one with the highest id`() =
        runBlocking {
            saveUsers(activeIds = setOf(1L, 3L))

            val updated = dao.repairMultipleActiveUsers()

            assertEquals(USER_IDS.size, updated)
            assertEquals(listOf(3L), activeIds())
        }

    @Test
    fun `a single active user is left unchanged`() =
        runBlocking {
            saveUsers(activeIds = setOf(1L))

            val updated = dao.repairMultipleActiveUsers()

            assertEquals(0, updated)
            assertEquals(listOf(1L), activeIds())
        }

    private suspend fun saveUsers(activeIds: Set<Long>) {
        USER_IDS.forEach { id ->
            dao.saveUser(UserEntity(id = id, username = "user$id", baseUrl = BASE_URL, current = id in activeIds))
        }
    }

    private suspend fun activeIds(): List<Long> = dao.getUsers().filter { it.current }.map { it.id }

    companion object {
        private val USER_IDS = listOf(1L, 2L, 3L)
        private const val BASE_URL = "https://example.com"
    }
}
