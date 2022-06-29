package com.nextcloud.talk.polls.adapters

import com.nextcloud.talk.R

data class PollResultHeaderItem(
    val name: String,
    val percent: Int,
    val selfVoted: Boolean
) : PollResultItem {

    override fun getViewType(): Int {
        return VIEW_TYPE
    }

    companion object {
        // layout is used as view type for uniqueness
        public val VIEW_TYPE: Int = R.layout.poll_result_header_item
    }
}
