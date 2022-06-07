package com.nextcloud.talk.polls.repositories

interface DialogPollRepository {

    data class Parameters(
        val userName: String,
        val userToken: String,
        val baseUrl: String,
        val roomToken: String,
        val pollId: Int
    )
}
