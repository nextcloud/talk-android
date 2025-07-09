/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import com.nextcloud.talk.adapters.items.FlexibleItemViewType

data class PollResultHeaderItem(val name: String, val percent: Int, val selfVoted: Boolean) : PollResultItem {

    override fun getViewType(): Int = VIEW_TYPE

    companion object {
        const val VIEW_TYPE = FlexibleItemViewType.POLL_RESULT_HEADER_ITEM
    }
}
