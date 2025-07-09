/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.arbitrarystorage

import com.nextcloud.talk.data.storage.ArbitraryStoragesRepository
import com.nextcloud.talk.data.storage.model.ArbitraryStorage
import io.reactivex.Maybe

class ArbitraryStorageManager(private val arbitraryStoragesRepository: ArbitraryStoragesRepository) {
    fun storeStorageSetting(accountIdentifier: Long, key: String, value: String?, objectString: String?) {
        arbitraryStoragesRepository.saveArbitraryStorage(ArbitraryStorage(accountIdentifier, key, objectString, value))
    }

    fun getStorageSetting(accountIdentifier: Long, key: String, objectString: String): Maybe<ArbitraryStorage> =
        arbitraryStoragesRepository.getStorageSetting(accountIdentifier, key, objectString)

    fun deleteAllEntriesForAccountIdentifier(accountIdentifier: Long): Int =
        arbitraryStoragesRepository.deleteArbitraryStorage(accountIdentifier)
}
