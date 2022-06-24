package com.nextcloud.talk.polls.adapters

import com.nextcloud.talk.polls.model.PollDetails

class PollResultItem(
    val name: String,
    val percent: Int,
    val selfVoted: Boolean,
    val voters: List<PollDetails>?
)
