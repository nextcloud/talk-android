/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
 *
 * model program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * model program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with model program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.data.storage

import com.nextcloud.talk.data.storage.model.ArbitraryStorage
import com.nextcloud.talk.data.storage.model.ArbitraryStorageEntity

object ArbitraryStorageMapper {
    fun toModel(entity: ArbitraryStorageEntity?): ArbitraryStorage? {
        return if (entity == null) {
            null
        } else {
            ArbitraryStorage(
                entity.accountIdentifier,
                entity.key,
                entity.storageObject,
                entity.value
            )
        }
    }

    fun toEntity(model: ArbitraryStorage): ArbitraryStorageEntity {
        return ArbitraryStorageEntity(
            accountIdentifier = model.accountIdentifier,
            key = model.key,
            storageObject = model.storageObject,
            value = model.value
        )
    }
}
