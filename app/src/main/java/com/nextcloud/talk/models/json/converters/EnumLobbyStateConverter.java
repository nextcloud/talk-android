/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.conversations.Conversation;

public class EnumLobbyStateConverter extends IntBasedTypeConverter<Conversation.LobbyState> {
    @Override
    public Conversation.LobbyState getFromInt(int i) {
        switch (i) {
            case 0:
                return Conversation.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS;
            case 1:
                return Conversation.LobbyState.LOBBY_STATE_MODERATORS_ONLY;
            default:
                return Conversation.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS;
        }
    }

    @Override
    public int convertToInt(Conversation.LobbyState object) {
        switch (object) {
            case LOBBY_STATE_ALL_PARTICIPANTS:
                return 0;
            case LOBBY_STATE_MODERATORS_ONLY:
                return 1;
            default:
                return 0;
        }
    }
}
