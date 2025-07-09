/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.storage

import com.nextcloud.talk.data.storage.model.ArbitraryStorage
import com.nextcloud.talk.data.storage.model.ArbitraryStorageEntity

object ArbitraryStorageMapper {
    fun toModel(entity: ArbitraryStorageEntity?): ArbitraryStorage? =
        entity?.let {
            ArbitraryStorage(
                it.accountIdentifier,
                it.key,
                it.storageObject,
                it.value
            )
        }

    fun toEntity(model: ArbitraryStorage): ArbitraryStorageEntity =
        ArbitraryStorageEntity(
            accountIdentifier = model.accountIdentifier,
            key = model.key,
            storageObject = model.storageObject,
            value = model.value
        )
}
