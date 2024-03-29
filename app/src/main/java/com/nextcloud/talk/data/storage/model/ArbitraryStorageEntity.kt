/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.storage.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "ArbitraryStorage", primaryKeys = ["accountIdentifier", "key"])
data class ArbitraryStorageEntity(
    @ColumnInfo(name = "accountIdentifier")
    var accountIdentifier: Long = 0,

    @ColumnInfo(name = "key")
    var key: String = "",

    @ColumnInfo(name = "object")
    var storageObject: String? = null,

    @ColumnInfo(name = "value")
    var value: String? = null
) : Parcelable
