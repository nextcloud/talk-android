/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.test.fakes

import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import io.reactivex.Observable

class FakeUnifiedSearchRepository : UnifiedSearchRepository {

    lateinit var response: UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>
    var lastRequestedCursor = -1

    override fun searchMessages(
        userEntity: UserEntity,
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): Observable<UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>> {
        lastRequestedCursor = cursor
        return Observable.just(response)
    }

    override fun searchInRoom(
        userEntity: UserEntity,
        roomToken: String,
        searchTerm: String,
        cursor: Int,
        limit: Int
    ): Observable<UnifiedSearchRepository.UnifiedSearchResults<SearchMessageEntry>> {
        lastRequestedCursor = cursor
        return Observable.just(response)
    }
}
