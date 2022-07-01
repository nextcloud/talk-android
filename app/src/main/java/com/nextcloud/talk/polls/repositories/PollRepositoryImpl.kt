/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
import com.nextcloud.talk.polls.model.PollDetails
import com.nextcloud.talk.polls.repositories.model.PollDetailsResponse
import com.nextcloud.talk.polls.repositories.model.PollResponse
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import io.reactivex.Observable

class PollRepositoryImpl(private val ncApi: NcApi, private val currentUserProvider: CurrentUserProvider) :
    PollRepository {

    val credentials = ApiUtils.getCredentials(
        currentUserProvider.currentUser?.username,
        currentUserProvider.currentUser?.token
    )

    override fun createPoll(
        roomToken: String,
        question: String,
        options: List<String>,
        resultMode: Int,
        maxVotes:
            Int
    ): Observable<Poll>? {
        return ncApi.createPoll(
            credentials,
            ApiUtils.getUrlForPoll(
                currentUserProvider.currentUser?.baseUrl,
                roomToken
            ),
            question,
            options,
            resultMode,
            maxVotes
        ).map { mapToPoll(it.ocs?.data!!) }
    }

    override fun getPoll(roomToken: String, pollId: String): Observable<Poll> {

        return ncApi.getPoll(
            credentials,
            ApiUtils.getUrlForPoll(
                currentUserProvider.currentUser?.baseUrl,
                roomToken,
                pollId
            ),
        ).map { mapToPoll(it.ocs?.data!!) }

        // return Observable.just(
        //     Poll(
        //         id = "aaa",
        //         question = "what if?",
        //         options = listOf("yes", "no", "maybe", "I don't know"),
        //         votes = listOf(0, 0, 0, 0),
        //         actorType = "",
        //         actorId = "",
        //         actorDisplayName = "",
        //         status = 0,
        //         resultMode = 0,
        //         maxVotes = 1,
        //         votedSelf = listOf(0, 0, 0, 0),
        //         numVoters = 0,
        //         details = emptyList()
        //     )
        // )
    }

    override fun vote(roomToken: String, pollId: String, options: List<Int>): Observable<Poll>? {

        return ncApi.votePoll(
            credentials,
            ApiUtils.getUrlForPoll(
                currentUserProvider.currentUser?.baseUrl,
                roomToken,
                pollId
            ),
            options
        ).map { mapToPoll(it.ocs?.data!!) }
    }

    override fun closePoll(roomToken: String, pollId: String): Observable<Poll> {

        return ncApi.closePoll(
            credentials,
            ApiUtils.getUrlForPoll(
                currentUserProvider.currentUser?.baseUrl,
                roomToken,
                pollId
            ),
        ).map { mapToPoll(it.ocs?.data!!) }
    }

    companion object {

        private fun mapToPoll(pollResponse: PollResponse): Poll {
            val pollDetails = pollResponse.details?.map { it -> mapToPollDetails(it) }

            return Poll(
                pollResponse.id,
                pollResponse.question,
                pollResponse.options,
                convertVotes(pollResponse.votes),
                pollResponse.actorType,
                pollResponse.actorId,
                pollResponse.actorDisplayName,
                pollResponse.status,
                pollResponse.resultMode,
                pollResponse.maxVotes,
                pollResponse.votedSelf,
                pollResponse.numVoters,
                pollDetails,
            )
        }

        private fun convertVotes(votes: Map<String, Int>?): Map<String, Int> {
            val resultMap: MutableMap<String, Int> = HashMap()
            votes?.forEach {
                resultMap[it.key.replace("option-", "")] = it.value
            }
            return resultMap
        }

        private fun mapToPollDetails(pollDetailsResponse: PollDetailsResponse): PollDetails {
            return PollDetails(
                pollDetailsResponse.actorType,
                pollDetailsResponse.actorId,
                pollDetailsResponse.actorDisplayName,
                pollDetailsResponse.optionId,
            )
        }
    }
}
