/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter;
import com.nextcloud.talk.models.json.chat.ChatMessage;

import static com.nextcloud.talk.models.json.chat.ChatMessage.SystemMessageType.*;

/*

    conversation_created - {actor} created the conversation
    conversation_renamed - {actor} renamed the conversation from "foo" to "bar"
    call_joined - {actor} joined the call
    call_left - {actor} left the call
    call_ended - Call with {user1}, {user2}, {user3}, {user4} and {user5} (Duration 30:23)
    guests_allowed - {actor} allowed guests in the conversation
    guests_disallowed - {actor} disallowed guests in the conversation
    password_set - {actor} set a password for the conversation
    password_removed - {actor} removed the password for the conversation
    user_added - {actor} added {user} to the conversation
    user_removed - {actor} removed {user} from the conversation
    moderator_promoted - {actor} promoted {user} to moderator
    moderator_demoted - {actor} demoted {user} from moderator

 */
public class EnumSystemMessageTypeConverter extends StringBasedTypeConverter<ChatMessage.SystemMessageType> {
    @Override
    public ChatMessage.SystemMessageType getFromString(String string) {
        switch (string) {
            case "conversation_created":
                return CONVERSATION_CREATED;
            case "conversation_renamed":
                return CONVERSATION_RENAMED;
            case "call_started":
                return CALL_STARTED;
            case "call_joined":
                return CALL_JOINED;
            case "call_left":
                return CALL_LEFT;
            case "call_ended":
                return CALL_ENDED;
            case "guests_allowed":
                return GUESTS_ALLOWED;
            case "guests_disallowed":
                return GUESTS_DISALLOWED;
            case "password_set":
                return PASSWORD_SET;
            case "password_removed":
                return PASSWORD_REMOVED;
            case "user_added":
                return USER_ADDED;
            case "user_removed":
                return USER_REMOVED;
            case "moderator_promoted":
                return MODERATOR_PROMOTED;
            case "moderator_demoted":
                return MODERATOR_DEMOTED;
            case "file_shared":
                return FILE_SHARED;
            default:
                return DUMMY;
        }
    }

    @Override
    public String convertToString(ChatMessage.SystemMessageType object) {

        if (object == null) {
            return "";
        }

        switch (object) {
            case CONVERSATION_CREATED:
                return "conversation_created";
            case CONVERSATION_RENAMED:
                return "conversation_renamed";
            case CALL_STARTED:
                return "call_started";
            case CALL_JOINED:
                return "call_joined";
            case CALL_LEFT:
                return "call_left";
            case CALL_ENDED:
                return "call_ended";
            case GUESTS_ALLOWED:
                return "guests_allowed";
            case GUESTS_DISALLOWED:
                return "guests_disallowed";
            case PASSWORD_SET:
                return "password_set";
            case PASSWORD_REMOVED:
                return "password_removed";
            case USER_ADDED:
                return "user_added";
            case USER_REMOVED:
                return "user_removed";
            case MODERATOR_PROMOTED:
                return "moderator_promoted";
            case MODERATOR_DEMOTED:
                return "moderator_demoted";
            case FILE_SHARED:
                return "file_shared";
            default:
                return "";
        }
    }
}
