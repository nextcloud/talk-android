/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.storage

import com.nextcloud.talk.data.storage.model.ArbitraryStorage
import com.nextcloud.talk.data.storage.model.ArbitraryStorageEntity
import io.reactivex.Maybe

class ArbitraryStoragesRepositoryImpl(private val arbitraryStoragesDao: ArbitraryStoragesDao) :
    ArbitraryStoragesRepository {
    override fun getStorageSetting(
        accountIdentifier: Long,
        key: String,
        objectString: String
    ): Maybe<ArbitraryStorage> =
        arbitraryStoragesDao
            .getStorageSetting(accountIdentifier, key, objectString)
            .map { ArbitraryStorageMapper.toModel(it) }

    override fun getAll(): Maybe<List<ArbitraryStorageEntity>> = arbitraryStoragesDao.getAll()

    override fun deleteArbitraryStorage(accountIdentifier: Long): Int =
        arbitraryStoragesDao.deleteArbitraryStorage(accountIdentifier)

    override fun saveArbitraryStorage(arbitraryStorage: ArbitraryStorage): Long =
        arbitraryStoragesDao.saveArbitraryStorage(ArbitraryStorageMapper.toEntity(arbitraryStorage))
}
