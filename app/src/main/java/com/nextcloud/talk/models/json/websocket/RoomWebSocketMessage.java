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

package com.nextcloud.talk.models.json.websocket;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@JsonObject
@Parcel
public class RoomWebSocketMessage {
    @JsonField(name = "roomid")
    String roomId;

    @JsonField(name = "sessionid")
    String sessiondId;

    @JsonField(name = "properties")
    RoomPropertiesWebSocketMessage roomPropertiesWebSocketMessage;

    public String getRoomId() {
        return this.roomId;
    }

    public String getSessiondId() {
        return this.sessiondId;
    }

    public RoomPropertiesWebSocketMessage getRoomPropertiesWebSocketMessage() {
        return this.roomPropertiesWebSocketMessage;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public void setSessiondId(String sessiondId) {
        this.sessiondId = sessiondId;
    }

    public void setRoomPropertiesWebSocketMessage(RoomPropertiesWebSocketMessage roomPropertiesWebSocketMessage) {
        this.roomPropertiesWebSocketMessage = roomPropertiesWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RoomWebSocketMessage)) {
            return false;
        }
        final RoomWebSocketMessage other = (RoomWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$roomId = this.getRoomId();
        final Object other$roomId = other.getRoomId();
        if (this$roomId == null ? other$roomId != null : !this$roomId.equals(other$roomId)) {
            return false;
        }
        final Object this$sessiondId = this.getSessiondId();
        final Object other$sessiondId = other.getSessiondId();
        if (this$sessiondId == null ? other$sessiondId != null : !this$sessiondId.equals(other$sessiondId)) {
            return false;
        }
        final Object this$roomPropertiesWebSocketMessage = this.getRoomPropertiesWebSocketMessage();
        final Object other$roomPropertiesWebSocketMessage = other.getRoomPropertiesWebSocketMessage();

        return this$roomPropertiesWebSocketMessage == null ? other$roomPropertiesWebSocketMessage == null : this$roomPropertiesWebSocketMessage.equals(other$roomPropertiesWebSocketMessage);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof RoomWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $roomId = this.getRoomId();
        result = result * PRIME + ($roomId == null ? 43 : $roomId.hashCode());
        final Object $sessiondId = this.getSessiondId();
        result = result * PRIME + ($sessiondId == null ? 43 : $sessiondId.hashCode());
        final Object $roomPropertiesWebSocketMessage = this.getRoomPropertiesWebSocketMessage();
        result = result * PRIME + ($roomPropertiesWebSocketMessage == null ? 43 : $roomPropertiesWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "RoomWebSocketMessage(roomId=" + this.getRoomId() + ", sessiondId=" + this.getSessiondId() + ", roomPropertiesWebSocketMessage=" + this.getRoomPropertiesWebSocketMessage() + ")";
    }
}
