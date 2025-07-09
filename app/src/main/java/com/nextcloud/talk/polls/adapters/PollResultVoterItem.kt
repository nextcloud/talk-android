/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import com.nextcloud.talk.adapters.items.FlexibleItemViewType
import com.nextcloud.talk.polls.model.PollDetails

data class PollResultVoterItem(val details: PollDetails) : PollResultItem {

    override fun getViewType(): Int = VIEW_TYPE

    companion object {
        const val VIEW_TYPE = FlexibleItemViewType.POLL_RESULT_VOTER_ITEM
    }
}
