/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.storage.model.ArbitraryStorageEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArbitraryStoragesDaoTest {
    private lateinit var arbitraryStoragesDao: ArbitraryStoragesDao
    private lateinit var db: TalkDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            TalkDatabase::class.java
        ).allowMainThreadQueries().build()
        arbitraryStoragesDao = db.arbitraryStoragesDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun saveAndGetStorageSetting() {
        val entity = ArbitraryStorageEntity(1, "key1", "object1", "value1")
        arbitraryStoragesDao.saveArbitraryStorage(entity)

        val retrieved = arbitraryStoragesDao.getStorageSetting(1, "key1", "object1").blockingGet()
        assertEquals("value1", retrieved?.value)
    }

    @Test
    fun getAll() {
        val entity1 = ArbitraryStorageEntity(1, "key1", "object1", "value1")
        val entity2 = ArbitraryStorageEntity(1, "key2", "object2", "value2")
        arbitraryStoragesDao.saveArbitraryStorage(entity1)
        arbitraryStoragesDao.saveArbitraryStorage(entity2)

        val list = arbitraryStoragesDao.getAll().blockingGet()
        assertEquals(2, list?.size)
    }

    @Test
    fun deleteArbitraryStorage() {
        val entity1 = ArbitraryStorageEntity(1, "key1", "object1", "value1")
        val entity2 = ArbitraryStorageEntity(2, "key1", "object1", "value1")
        arbitraryStoragesDao.saveArbitraryStorage(entity1)
        arbitraryStoragesDao.saveArbitraryStorage(entity2)

        val deletedCount = arbitraryStoragesDao.deleteArbitraryStorage(1)
        assertEquals(1, deletedCount)

        val list = arbitraryStoragesDao.getAll().blockingGet()
        assertEquals(1, list?.size)
        assertEquals(2L, list?.get(0)?.accountIdentifier)
    }
}
