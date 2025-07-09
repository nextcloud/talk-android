/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import com.nextcloud.talk.adapters.items.FlexibleItemViewType
import com.nextcloud.talk.polls.model.PollDetails

data class PollResultVotersOverviewItem(val detailsList: List<PollDetails>) : PollResultItem {

    override fun getViewType(): Int = VIEW_TYPE

    companion object {
        // layout is used as view type for uniqueness
        const val VIEW_TYPE = FlexibleItemViewType.POLL_RESULT_VOTERS_OVERVIEW_ITEM
    }
}
