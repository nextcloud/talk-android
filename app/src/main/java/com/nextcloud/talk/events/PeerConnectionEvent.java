/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.events;

import androidx.annotation.Nullable;
import lombok.Data;

@Data
public class PeerConnectionEvent {
    private final PeerConnectionEventType peerConnectionEventType;
    private final String sessionId;
    private final String nick;
    private final Boolean changeValue;
    private final String videoStreamType;

    public PeerConnectionEvent(PeerConnectionEventType peerConnectionEventType, @Nullable String sessionId,
                               @Nullable String nick, Boolean changeValue, @Nullable String videoStreamType) {
        this.peerConnectionEventType = peerConnectionEventType;
        this.nick = nick;
        this.changeValue = changeValue;
        this.sessionId = sessionId;
        this.videoStreamType = videoStreamType;
    }

    public enum PeerConnectionEventType {
        PEER_CONNECTED, PEER_CLOSED, SENSOR_FAR, SENSOR_NEAR, NICK_CHANGE, AUDIO_CHANGE, VIDEO_CHANGE
    }
}
