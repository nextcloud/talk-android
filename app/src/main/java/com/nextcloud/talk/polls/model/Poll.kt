package com.nextcloud.talk.polls.model

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
    val details: List<PollDetails>?
) {
    companion object {
        const val STATUS_OPEN: Int = 0
        const val STATUS_CLOSED: Int = 1
        const val RESULT_MODE_PUBLIC: Int = 0
        const val RESULT_MODE_HIDDEN: Int = 1
    }
}
