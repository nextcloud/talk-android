/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.ConversationEnums;

public class EnumReadOnlyConversationConverter extends IntBasedTypeConverter<ConversationEnums.ConversationReadOnlyState> {
    @Override
    public ConversationEnums.ConversationReadOnlyState getFromInt(int i) {
        switch (i) {
            case 0:
                return ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE;
            case 1:
                return ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_ONLY;
            default:
                return ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE;
        }
    }

    @Override
    public int convertToInt(ConversationEnums.ConversationReadOnlyState object) {
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
