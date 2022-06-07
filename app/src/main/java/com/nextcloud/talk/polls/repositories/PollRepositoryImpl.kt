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

package com.nextcloud.talk.polls.repositories

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import io.reactivex.Observable

class PollRepositoryImpl(private val api: NcApi, private val currentUserProvider: CurrentUserProvider) :
    PollRepository {

    override fun getPoll(roomToken: String, pollId: String): Observable<Poll> {
        // TODO actual api call
        return Observable.just(
            Poll(
                id = "aaa",
                question = "what if?",
                options = listOf("yes", "no", "maybe", "I don't know"),
                votes = listOf(0, 0, 0, 0),
                actorType = "",
                actorId = "",
                actorDisplayName = "",
                status = 0,
                resultMode = 0,
                maxVotes = 1,
                votedSelf = listOf(0, 0, 0, 0),
                numVoters = 0,
                details = emptyList()
            )
        )
    }
}
