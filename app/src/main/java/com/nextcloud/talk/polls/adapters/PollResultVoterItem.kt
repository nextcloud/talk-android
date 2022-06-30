package com.nextcloud.talk.polls.adapters

import com.nextcloud.talk.R
import com.nextcloud.talk.polls.model.PollDetails

data class PollResultVoterItem(
    val details: PollDetails
) : PollResultItem {

    override fun getViewType(): Int {
        return VIEW_TYPE
    }

    companion object {
        // layout is used as view type for uniqueness
        const val VIEW_TYPE: Int = R.layout.poll_result_voter_item
    }
}
