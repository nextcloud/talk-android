/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.newarch.local.converters

import androidx.room.TypeConverter
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType.*

class ConversationTypeConverter {
    @TypeConverter
    fun fromConversationTypeToInt(conversationType: ConversationType): Int {
        return conversationType.value
    }

    @TypeConverter
    fun fromIntToConversationType(value: Int): ConversationType {
        when (value) {
            1 -> return ONE_TO_ONE_CONVERSATION
            2 -> return GROUP_CONVERSATION
            3 -> return PUBLIC_CONVERSATION
            else -> return SYSTEM_CONVERSATION
        }
    }
}