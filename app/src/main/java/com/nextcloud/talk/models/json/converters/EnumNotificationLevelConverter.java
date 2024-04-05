/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.conversations.Conversation;

public class EnumNotificationLevelConverter extends IntBasedTypeConverter<Conversation.NotificationLevel> {
    @Override
    public Conversation.NotificationLevel getFromInt(int i) {
        switch (i) {
            case 0:
                return Conversation.NotificationLevel.DEFAULT;
            case 1:
                return Conversation.NotificationLevel.ALWAYS;
            case 2:
                return Conversation.NotificationLevel.MENTION;
            case 3:
                return Conversation.NotificationLevel.NEVER;
            default:
                return Conversation.NotificationLevel.DEFAULT;
        }
    }

    @Override
    public int convertToInt(Conversation.NotificationLevel object) {
        switch (object) {
            case DEFAULT:
                return 0;
            case ALWAYS:
                return 1;
            case MENTION:
                return 2;
            case NEVER:
                return 3;
            default:
                return 0;
        }
    }

}
