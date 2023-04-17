/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2023 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.adapters.items

class FlexibleItemViewType {
    companion object {
        const val CONVERSATION_ITEM: Int = 1120391230
        const val LOAD_MORE_RESULTS_ITEM: Int = 1120391231
        const val MESSAGE_RESULT_ITEM: Int = 1120391232
        const val MESSAGES_TEXT_HEADER_ITEM: Int = 1120391233
        const val POLL_RESULT_HEADER_ITEM: Int = 1120391234
        const val POLL_RESULT_VOTER_ITEM: Int = 1120391235
        const val POLL_RESULT_VOTERS_OVERVIEW_ITEM: Int = 1120391236
    }
}
