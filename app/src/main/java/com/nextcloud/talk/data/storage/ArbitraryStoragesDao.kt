/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextcloud.talk.data.storage.model.ArbitraryStorageEntity
import io.reactivex.Maybe

@Dao
abstract class ArbitraryStoragesDao {
    @Query(
        "SELECT * FROM ArbitraryStorage WHERE " +
            "accountIdentifier = :accountIdentifier AND " +
            "\"key\" = :key AND " +
            "object = :objectString"
    )
    abstract fun getStorageSetting(
        accountIdentifier: Long,
        key: String,
        objectString: String
    ): Maybe<ArbitraryStorageEntity>

    @Query(
        "SELECT * FROM ArbitraryStorage"
    )
    abstract fun getAll(): Maybe<List<ArbitraryStorageEntity>>

    @Query("DELETE FROM ArbitraryStorage WHERE accountIdentifier = :accountIdentifier")
    abstract fun deleteArbitraryStorage(accountIdentifier: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveArbitraryStorage(arbitraryStorage: ArbitraryStorageEntity): Long
}
