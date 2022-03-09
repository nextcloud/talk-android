/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * @author Marcel Hibbe
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CALL_ENDED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CALL_ENDED_EVERYONE
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CALL_JOINED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CALL_LEFT
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CALL_MISSED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CALL_STARTED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CALL_TRIED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CIRCLE_ADDED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CIRCLE_REMOVED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CLEARED_CHAT
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CONVERSATION_CREATED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.CONVERSATION_RENAMED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.DESCRIPTION_REMOVED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.DESCRIPTION_SET
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.DUMMY
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.FILE_SHARED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.GROUP_ADDED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.GROUP_REMOVED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.GUESTS_ALLOWED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.GUESTS_DISALLOWED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.GUEST_MODERATOR_DEMOTED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.GUEST_MODERATOR_PROMOTED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.LISTABLE_ALL
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.LISTABLE_NONE
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.LISTABLE_USERS
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.LOBBY_NONE
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.LOBBY_NON_MODERATORS
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.LOBBY_OPEN_TO_EVERYONE
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_ADDED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_DISABLED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_EDITED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_ENABLED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_REMOVED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.MESSAGE_DELETED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.MODERATOR_DEMOTED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.MODERATOR_PROMOTED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.OBJECT_SHARED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.PASSWORD_REMOVED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.PASSWORD_SET
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.READ_ONLY
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.READ_ONLY_OFF
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.USER_ADDED
import com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.USER_REMOVED

/*
* see https://nextcloud-talk.readthedocs.io/en/latest/chat/#system-messages
*
* `conversation_created` - {actor} created the conversation
* `conversation_renamed` - {actor} renamed the conversation from "foo" to "bar"
* `description_set` - {actor} set the description to "Hello world"
* `description_removed` - {actor} removed the description
* `call_started` - {actor} started a call
* `call_joined` - {actor} joined the call
* `call_left` - {actor} left the call
* `call_ended` - Call with {user1}, {user2}, {user3}, {user4} and {user5} (Duration 30:23)
* `call_ended_everyone` - {user1} ended the call with {user2}, {user3}, {user4} and {user5} (Duration 30:23)
* `call_missed` - You missed a call from {user}
* `call_tried` - You tried to call {user}
* `read_only_off` - {actor} unlocked the conversation
* `read_only` - {actor} locked the conversation
* `listable_none` - {actor} limited the conversation to the current participants
* `listable_users` - {actor} opened the conversation accessible to registered users
* `listable_all` - {actor} opened the conversation accessible to registered and guest app users
* `lobby_timer_reached` - The conversation is now open to everyone
* `lobby_none` - {actor} opened the conversation to everyone
* `lobby_non_moderators` - {actor} restricted the conversation to moderators
* `guests_allowed` - {actor} allowed guests in the conversation
* `guests_disallowed` - {actor} disallowed guests in the conversation
* `password_set` - {actor} set a password for the conversation
* `password_removed` - {actor} removed the password for the conversation
* `user_added` - {actor} added {user} to the conversation
* `user_removed` - {actor} removed {user} from the conversation
* `group_added` - {actor} added group {group} to the conversation
* `group_removed` - {actor} removed group {group} from the conversation
* `circle_added` - {actor} added circle {circle} to the conversation
* `circle_removed` - {actor} removed circle {circle} from the conversation
* `moderator_promoted` - {actor} promoted {user} to moderator
* `moderator_demoted` - {actor} demoted {user} from moderator
* `guest_moderator_promoted` - {actor} promoted {user} to moderator
* `guest_moderator_demoted` - {actor} demoted {user} from moderator
* `message_deleted` - Message deleted by {actor} (Should not be shown to the user)
* `history_cleared` - {actor} cleared the history of the conversation
* `file_shared` - {file}
* `object_shared` - {object}
* `matterbridge_config_added` - {actor} set up Matterbridge to synchronize this conversation with other chats
* `matterbridge_config_edited` - {actor} updated the Matterbridge configuration
* `matterbridge_config_removed` - {actor} removed the Matterbridge configuration
* `matterbridge_config_enabled` - {actor} started Matterbridge
* `matterbridge_config_disabled` - {actor} stopped Matterbridge
*/
class EnumSystemMessageTypeConverter : StringBasedTypeConverter<ChatMessage.SystemMessageType>() {
    override fun getFromString(string: String): ChatMessage.SystemMessageType {
        when (string) {
            "conversation_created" -> return CONVERSATION_CREATED
            "conversation_renamed" -> return CONVERSATION_RENAMED
            "description_set" -> return DESCRIPTION_SET
            "description_removed" -> return DESCRIPTION_REMOVED
            "call_started" -> return CALL_STARTED
            "call_joined" -> return CALL_JOINED
            "call_left" -> return CALL_LEFT
            "call_ended" -> return CALL_ENDED
            "call_ended_everyone" -> return CALL_ENDED_EVERYONE
            "call_missed" -> return CALL_MISSED
            "call_tried" -> return CALL_TRIED
            "read_only_off" -> return READ_ONLY_OFF
            "read_only" -> return READ_ONLY
            "listable_none" -> return LISTABLE_NONE
            "listable_users" -> return LISTABLE_USERS
            "listable_all" -> return LISTABLE_ALL
            "lobby_none" -> return LOBBY_NONE
            "lobby_non_moderators" -> return LOBBY_NON_MODERATORS
            "lobby_timer_reached" -> return LOBBY_OPEN_TO_EVERYONE
            "guests_allowed" -> return GUESTS_ALLOWED
            "guests_disallowed" -> return GUESTS_DISALLOWED
            "password_set" -> return PASSWORD_SET
            "password_removed" -> return PASSWORD_REMOVED
            "user_added" -> return USER_ADDED
            "user_removed" -> return USER_REMOVED
            "group_added" -> return GROUP_ADDED
            "group_removed" -> return GROUP_REMOVED
            "circle_added" -> return CIRCLE_ADDED
            "circle_removed" -> return CIRCLE_REMOVED
            "moderator_promoted" -> return MODERATOR_PROMOTED
            "moderator_demoted" -> return MODERATOR_DEMOTED
            "guest_moderator_promoted" -> return GUEST_MODERATOR_PROMOTED
            "guest_moderator_demoted" -> return GUEST_MODERATOR_DEMOTED
            "message_deleted" -> return MESSAGE_DELETED
            "file_shared" -> return FILE_SHARED
            "object_shared" -> return OBJECT_SHARED
            "matterbridge_config_added" -> return MATTERBRIDGE_CONFIG_ADDED
            "matterbridge_config_edited" -> return MATTERBRIDGE_CONFIG_EDITED
            "matterbridge_config_removed" -> return MATTERBRIDGE_CONFIG_REMOVED
            "matterbridge_config_enabled" -> return MATTERBRIDGE_CONFIG_ENABLED
            "matterbridge_config_disabled" -> return MATTERBRIDGE_CONFIG_DISABLED
            "history_cleared" -> return CLEARED_CHAT
            else -> return DUMMY
        }
    }

    override fun convertToString(`object`: ChatMessage.SystemMessageType?): String {

        if (`object` == null) {
            return ""
        }

        when (`object`) {
            CONVERSATION_CREATED -> return "conversation_created"
            CONVERSATION_RENAMED -> return "conversation_renamed"
            DESCRIPTION_REMOVED -> return "description_removed"
            DESCRIPTION_SET -> return "description_set"
            CALL_STARTED -> return "call_started"
            CALL_JOINED -> return "call_joined"
            CALL_LEFT -> return "call_left"
            CALL_ENDED -> return "call_ended"
            CALL_ENDED_EVERYONE -> return "call_ended_everyone"
            CALL_MISSED -> return "call_missed"
            CALL_TRIED -> return "call_tried"
            READ_ONLY_OFF -> return "read_only_off"
            READ_ONLY -> return "read_only"
            LISTABLE_NONE -> return "listable_none"
            LISTABLE_USERS -> return "listable_users"
            LISTABLE_ALL -> return "listable_all"
            LOBBY_NONE -> return "lobby_none"
            LOBBY_NON_MODERATORS -> return "lobby_non_moderators"
            LOBBY_OPEN_TO_EVERYONE -> return "lobby_timer_reached"
            GUESTS_ALLOWED -> return "guests_allowed"
            GUESTS_DISALLOWED -> return "guests_disallowed"
            PASSWORD_SET -> return "password_set"
            PASSWORD_REMOVED -> return "password_removed"
            USER_ADDED -> return "user_added"
            USER_REMOVED -> return "user_removed"
            GROUP_ADDED -> return "group_added"
            GROUP_REMOVED -> return "group_removed"
            CIRCLE_ADDED -> return "circle_added"
            CIRCLE_REMOVED -> return "circle_removed"
            MODERATOR_PROMOTED -> return "moderator_promoted"
            MODERATOR_DEMOTED -> return "moderator_demoted"
            GUEST_MODERATOR_PROMOTED -> return "guest_moderator_promoted"
            GUEST_MODERATOR_DEMOTED -> return "guest_moderator_demoted"
            MESSAGE_DELETED -> return "message_deleted"
            FILE_SHARED -> return "file_shared"
            OBJECT_SHARED -> return "object_shared"
            MATTERBRIDGE_CONFIG_ADDED -> return "matterbridge_config_added"
            MATTERBRIDGE_CONFIG_EDITED -> return "matterbridge_config_edited"
            MATTERBRIDGE_CONFIG_REMOVED -> return "matterbridge_config_removed"
            MATTERBRIDGE_CONFIG_ENABLED -> return "matterbridge_config_enabled"
            MATTERBRIDGE_CONFIG_DISABLED -> return "matterbridge_config_disabled"
            CLEARED_CHAT -> return "clear_history"
            else -> return ""
        }
    }
}
