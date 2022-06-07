package com.nextcloud.talk.polls.model

import com.nextcloud.talk.polls.repositories.model.PollDetails

data class Poll(
    val id: String,
    val question: String?,
    val options: List<String>?,
    val votes: List<Int>?,
    val actorType: String?,
    val actorId: String?,
    val actorDisplayName: String?,
    val status: Int,
    val resultMode: Int,
    val maxVotes: Int,
    val votedSelf: List<Int>?,
    val numVoters: Int,
    // TODO PollDetails needs own model class
    val details: List<PollDetails>?
)
