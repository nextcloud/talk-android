/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.repositories

import com.nextcloud.talk.polls.model.Poll
import io.reactivex.Observable

interface PollRepository {

    fun createPoll(
        roomToken: String,
        question: String,
        options: List<String>,
        resultMode: Int,
        maxVotes: Int
    ): Observable<Poll>

    fun getPoll(roomToken: String, pollId: String): Observable<Poll>

    fun vote(roomToken: String, pollId: String, options: List<Int>): Observable<Poll>

    fun closePoll(roomToken: String, pollId: String): Observable<Poll>
}
