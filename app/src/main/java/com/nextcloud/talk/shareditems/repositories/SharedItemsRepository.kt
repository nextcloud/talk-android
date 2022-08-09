/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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

package com.nextcloud.talk.shareditems.repositories

import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import io.reactivex.Observable

interface SharedItemsRepository {

    fun media(
        parameters: Parameters,
        type: SharedItemType
    ): Observable<SharedItems>?

    fun media(
        parameters: Parameters,
        type: SharedItemType,
        lastKnownMessageId: Int?
    ): Observable<SharedItems>?

    fun availableTypes(parameters: Parameters): Observable<Set<SharedItemType>>

    data class Parameters(
        val userName: String,
        val userToken: String,
        val baseUrl: String,
        val roomToken: String
    )
}
