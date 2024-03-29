/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.conversations.Conversation;

public class EnumRoomTypeConverter extends IntBasedTypeConverter<Conversation.ConversationType> {
    @Override
    public Conversation.ConversationType getFromInt(int i) {
        switch (i) {
            case 1:
                return Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL;
            case 2:
                return Conversation.ConversationType.ROOM_GROUP_CALL;
            case 3:
                return Conversation.ConversationType.ROOM_PUBLIC_CALL;
            case 4:
                return Conversation.ConversationType.ROOM_SYSTEM;
            case 5:
                return Conversation.ConversationType.FORMER_ONE_TO_ONE;
            default:
                return Conversation.ConversationType.DUMMY;
        }
    }

    @Override
    public int convertToInt(Conversation.ConversationType object) {
        switch (object) {
            case DUMMY:
                return 0;
            case ROOM_TYPE_ONE_TO_ONE_CALL:
                return 1;
            case ROOM_GROUP_CALL:
                return 2;
            case ROOM_PUBLIC_CALL:
                return 3;
            case ROOM_SYSTEM:
                return 4;
            case FORMER_ONE_TO_ONE:
                return 5;
            default:
                return 0;
        }
    }
}
