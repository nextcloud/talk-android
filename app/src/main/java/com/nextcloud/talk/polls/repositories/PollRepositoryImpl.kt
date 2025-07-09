/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.repositories

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.model.PollDetails
import com.nextcloud.talk.polls.repositories.model.PollDetailsResponse
import com.nextcloud.talk.polls.repositories.model.PollResponse
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable
import kotlin.collections.forEach as kForEach

class PollRepositoryImpl(private val ncApi: NcApi, private val currentUserProvider: CurrentUserProviderNew) :
    PollRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!

    override fun createPoll(
        roomToken: String,
        question: String,
        options: List<String>,
        resultMode: Int,
        maxVotes: Int
    ): Observable<Poll> =
        ncApi.createPoll(
            credentials,
            ApiUtils.getUrlForPoll(
                currentUser.baseUrl!!,
                roomToken
            ),
            question,
            options,
            resultMode,
            maxVotes
        ).map { mapToPoll(it.ocs?.data!!) }

    override fun getPoll(roomToken: String, pollId: String): Observable<Poll> =
        ncApi.getPoll(
            credentials,
            ApiUtils.getUrlForPoll(
                currentUser.baseUrl!!,
                roomToken,
                pollId
            )
        ).map { mapToPoll(it.ocs?.data!!) }

    override fun vote(roomToken: String, pollId: String, options: List<Int>): Observable<Poll> =
        ncApi.votePoll(
            credentials,
            ApiUtils.getUrlForPoll(
                currentUser.baseUrl!!,
                roomToken,
                pollId
            ),
            options
        ).map { mapToPoll(it.ocs?.data!!) }

    override fun closePoll(roomToken: String, pollId: String): Observable<Poll> =
        ncApi.closePoll(
            credentials,
            ApiUtils.getUrlForPoll(
                currentUser.baseUrl!!,
                roomToken,
                pollId
            )
        ).map { mapToPoll(it.ocs?.data!!) }

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
                pollDetails
            )
        }

        private fun convertVotes(votes: Map<String, Int>?): Map<String, Int> {
            val resultMap: MutableMap<String, Int> = HashMap()
            votes?.kForEach { (key, value) ->
                resultMap[key.replace("option-", "")] = value
            }
            return resultMap
        }

        private fun mapToPollDetails(pollDetailsResponse: PollDetailsResponse): PollDetails =
            PollDetails(
                pollDetailsResponse.actorType,
                pollDetailsResponse.actorId,
                pollDetailsResponse.actorDisplayName,
                pollDetailsResponse.optionId
            )
    }
}
