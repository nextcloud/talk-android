/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
