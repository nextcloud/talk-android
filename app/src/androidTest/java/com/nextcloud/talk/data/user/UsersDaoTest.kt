/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.json.push.PushConfigurationState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsersDaoTest {
    private lateinit var usersDao: UsersDao
    private lateinit var db: TalkDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            TalkDatabase::class.java
        ).allowMainThreadQueries().build()
        usersDao = db.usersDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun saveAndGetUser() {
        val user = createUserEntity("user1", "Account 1", "https://server1.com")
        val id = usersDao.saveUser(user)

        val retrieved = usersDao.getUserWithId(id).blockingGet()
        assertNotNull(retrieved)
        assertEquals("user1", retrieved.userId)
    }

    @Test
    fun saveUsersAndGetAll() {
        val user1 = createUserEntity("user1", "Account 1", "https://server1.com")
        val user2 = createUserEntity("user2", "Account 2", "https://server1.com")
        usersDao.saveUsers(user1, user2)

        val users = usersDao.getUsers().blockingGet()
        assertEquals(2, users.size)
    }

    @Test
    fun getActiveUser() {
        val user1 = createUserEntity("user1", "Account 1", "https://server1.com").apply { current = true }
        val user2 = createUserEntity("user2", "Account 2", "https://server1.com").apply { current = false }
        usersDao.saveUsers(user1, user2)

        val active = usersDao.getActiveUser().blockingGet()
        assertNotNull(active)
        assertEquals("user1", active.userId)

        assertEquals("user1", usersDao.getActiveUserSynchronously()?.userId)

        val activeObs = usersDao.getActiveUserObservable().blockingFirst()
        assertEquals("user1", activeObs.userId)
    }

    @Test
    fun setUserAsActiveWithId() {
        val user1 = createUserEntity("user1", "Account 1", "https://server1.com").apply { current = true }
        val user2 = createUserEntity("user2", "Account 2", "https://server1.com").apply { current = false }
        val id1 = usersDao.saveUser(user1)
        val id2 = usersDao.saveUser(user2)

        usersDao.setUserAsActiveWithId(id2)

        val retrieved1 = usersDao.getUserWithId(id1).blockingGet()
        val retrieved2 = usersDao.getUserWithId(id2).blockingGet()
        assertEquals(false, retrieved1.current)
        assertEquals(true, retrieved2.current)
    }

    @Test
    fun deleteUser() {
        val user = createUserEntity("user1", "Account 1", "https://server1.com")
        val id = usersDao.saveUser(user)
        val savedUser = usersDao.getUserWithId(id).blockingGet()

        usersDao.deleteUser(savedUser)

        val users = usersDao.getUsers().blockingGet()
        assertTrue(users.isEmpty())
    }

    @Test
    fun updateUser() {
        val user = createUserEntity("user1", "Account 1", "https://server1.com")
        val id = usersDao.saveUser(user)
        val savedUser = usersDao.getUserWithId(id).blockingGet()

        savedUser.displayName = "New Display Name"
        usersDao.updateUser(savedUser)

        val retrieved = usersDao.getUserWithId(id).blockingGet()
        assertEquals("New Display Name", retrieved.displayName)
    }

    @Test
    fun getScheduledForDeletion() {
        val user1 = createUserEntity("user1", "Account 1", "https://server1.com").apply { scheduledForDeletion = true }
        val user2 = createUserEntity("user2", "Account 2", "https://server1.com").apply { scheduledForDeletion = false }
        usersDao.saveUsers(user1, user2)

        val scheduled = usersDao.getUsersScheduledForDeletion().blockingGet()
        assertEquals(1, scheduled.size)
        assertEquals("user1", scheduled[0].userId)

        val notScheduled = usersDao.getUsersNotScheduledForDeletion().blockingGet()
        assertEquals(1, notScheduled.size)
        assertEquals("user2", notScheduled[0].userId)

        val all = usersDao.getUsers().blockingGet()
        assertEquals(1, all.size)
        assertEquals("user2", all[0].userId)
    }

    @Test
    fun getUserWithUserId() {
        val user = createUserEntity("user1", "Account 1", "https://server1.com")
        usersDao.saveUser(user)

        val retrieved = usersDao.getUserWithUserId("user1").blockingGet()
        assertNotNull(retrieved)
        assertEquals("Account 1", retrieved.username)

        val nonexistent = usersDao.getUserWithUserId("nonexistent").blockingGet()
        assertNull(nonexistent)
    }

    @Test
    fun getUserWithUsernameAndServer() {
        val user = createUserEntity("user1", "Account 1", "https://server1.com")
        usersDao.saveUser(user)

        val retrieved = usersDao.getUserWithUsernameAndServer("Account 1", "https://server1.com").blockingGet()
        assertNotNull(retrieved)
        assertEquals("user1", retrieved.userId)
    }

    @Test
    fun updatePushState() {
        val user = createUserEntity("user1", "Account 1", "https://server1.com")
        val id = usersDao.saveUser(user)

        val newState = PushConfigurationState("token", "id", "sig", "key", true)
        val updatedCount = usersDao.updatePushState(id, newState).blockingGet()
        assertEquals(1, updatedCount)

        val retrieved = usersDao.getUserWithId(id).blockingGet()
        assertEquals(newState, retrieved.pushConfigurationState)
    }

    private fun createUserEntity(userId: String, userName: String, server: String) =
        UserEntity(
            userId = userId,
            username = userName,
            baseUrl = server,
            token = "token_$userId",
            displayName = "Display $userName",
            pushConfigurationState = PushConfigurationState("token", "id", "sig", "key", false),
            capabilities = null,
            serverVersion = null,
            clientCertificate = null,
            externalSignalingServer = null,
            current = false,
            scheduledForDeletion = false
        )
}
