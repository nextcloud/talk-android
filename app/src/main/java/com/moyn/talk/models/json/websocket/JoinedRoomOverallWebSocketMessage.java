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

package com.moyn.talk.models.json.websocket;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@JsonObject
@Parcel
public class JoinedRoomOverallWebSocketMessage extends BaseWebSocketMessage {
    @JsonField(name = "room")
    RoomWebSocketMessage roomWebSocketMessage;

    public RoomWebSocketMessage getRoomWebSocketMessage() {
        return this.roomWebSocketMessage;
    }

    public void setRoomWebSocketMessage(RoomWebSocketMessage roomWebSocketMessage) {
        this.roomWebSocketMessage = roomWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof JoinedRoomOverallWebSocketMessage)) {
            return false;
        }
        final JoinedRoomOverallWebSocketMessage other = (JoinedRoomOverallWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$roomWebSocketMessage = this.getRoomWebSocketMessage();
        final Object other$roomWebSocketMessage = other.getRoomWebSocketMessage();

        return this$roomWebSocketMessage == null ? other$roomWebSocketMessage == null : this$roomWebSocketMessage.equals(other$roomWebSocketMessage);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof JoinedRoomOverallWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $roomWebSocketMessage = this.getRoomWebSocketMessage();
        result = result * PRIME + ($roomWebSocketMessage == null ? 43 : $roomWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "JoinedRoomOverallWebSocketMessage(roomWebSocketMessage=" + this.getRoomWebSocketMessage() + ")";
    }
}
