/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.arbitrarystorage

import com.nextcloud.talk.data.storage.ArbitraryStoragesRepository
import com.nextcloud.talk.data.storage.model.ArbitraryStorage
import io.reactivex.Maybe

class ArbitraryStorageManager(private val arbitraryStoragesRepository: ArbitraryStoragesRepository) {
    fun storeStorageSetting(accountIdentifier: Long, key: String?, value: String?, objectString: String?) {
        arbitraryStoragesRepository.saveArbitraryStorage(ArbitraryStorage(accountIdentifier, key, objectString, value))
    }

    fun getStorageSetting(accountIdentifier: Long, key: String, objectString: String): Maybe<ArbitraryStorage> {
        return arbitraryStoragesRepository.getStorageSetting(accountIdentifier, key, objectString)
    }

    fun deleteAllEntriesForAccountIdentifier(accountIdentifier: Long): Int {
        return arbitraryStoragesRepository.deleteArbitraryStorage(accountIdentifier)
    }
}
