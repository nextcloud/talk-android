/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.model

import com.nextcloud.talk.models.json.participants.Participant

data class Poll(
    val id: String,
    val question: String?,
    val options: List<String>?,
    val votes: Map<String, Int>?,
    val actorType: Participant.ActorType?,
    val actorId: String?,
    val actorDisplayName: String?,
    val status: Int,
    val resultMode: Int,
    val maxVotes: Int,
    val votedSelf: List<Int>?,
    val numVoters: Int,
    val details: List<PollDetails>?
) {
    companion object {
        const val STATUS_OPEN: Int = 0
        const val STATUS_CLOSED: Int = 1
        const val RESULT_MODE_PUBLIC: Int = 0
        const val RESULT_MODE_HIDDEN: Int = 1
    }
}
