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

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.rooms.Conversation;

public class EnumReadOnlyConversationConverter extends IntBasedTypeConverter<Conversation.ConversationReadOnlyState> {
    @Override
    public Conversation.ConversationReadOnlyState getFromInt(int i) {
        switch (i) {
            case 0:
                return Conversation.ConversationReadOnlyState.CONVERSATION_READ_WRITE;
            case 1:
                return Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY;
            default:
                return Conversation.ConversationReadOnlyState.CONVERSATION_READ_WRITE;
        }
    }

    @Override
    public int convertToInt(Conversation.ConversationReadOnlyState object) {
        switch (object) {
            case CONVERSATION_READ_WRITE:
                return 0;
            case CONVERSATION_READ_ONLY:
                return 1;
            default:
                return 0;
        }
    }

}
