/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.models.json.converters

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter
import com.nextcloud.talk.models.json.conversations.Conversation

class ConversationObjectTypeConverter : StringBasedTypeConverter<Conversation.ObjectType>() {
    override fun getFromString(string: String?): Conversation.ObjectType {
        return when (string) {
            "share:password" -> Conversation.ObjectType.SHARE_PASSWORD
            "room" -> Conversation.ObjectType.ROOM
            "file" -> Conversation.ObjectType.FILE
            else -> Conversation.ObjectType.DEFAULT
        }
    }

    override fun convertToString(`object`: Conversation.ObjectType?): String {
        if (`object` == null) {
            return ""
        }

        return when (`object`) {
            Conversation.ObjectType.SHARE_PASSWORD -> "share:password"
            Conversation.ObjectType.ROOM -> "room"
            Conversation.ObjectType.FILE -> "file"
            else -> ""
        }
    }
}
