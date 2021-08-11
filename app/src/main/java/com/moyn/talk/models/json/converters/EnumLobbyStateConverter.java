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

package com.moyn.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.moyn.talk.models.json.conversations.Conversation;

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
