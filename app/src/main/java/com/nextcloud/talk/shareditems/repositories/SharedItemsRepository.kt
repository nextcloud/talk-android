/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.repositories

import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import io.reactivex.Observable

interface SharedItemsRepository {

    fun media(parameters: Parameters, type: SharedItemType): Observable<SharedItems>?

    fun media(parameters: Parameters, type: SharedItemType, lastKnownMessageId: Int?): Observable<SharedItems>?

    fun availableTypes(parameters: Parameters): Observable<Set<SharedItemType>>

    data class Parameters(val userName: String, val userToken: String, val baseUrl: String, val roomToken: String)
}
