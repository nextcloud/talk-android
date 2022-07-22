/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
        const val VIEW_TYPE: Int = R.layout.poll_result_header_item
    }
}
