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
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.converters.EnumRoomTypeConverter;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class RoomPropertiesWebSocketMessage {
    @JsonField(name = "name")
    String name;

    @JsonField(name = "type", typeConverter = EnumRoomTypeConverter.class)
    Conversation.ConversationType roomType;

    public String getName() {
        return this.name;
    }

    public Conversation.ConversationType getRoomType() {
        return this.roomType;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRoomType(Conversation.ConversationType roomType) {
        this.roomType = roomType;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RoomPropertiesWebSocketMessage)) {
            return false;
        }
        final RoomPropertiesWebSocketMessage other = (RoomPropertiesWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
            return false;
        }
        final Object this$roomType = this.getRoomType();
        final Object other$roomType = other.getRoomType();

        return this$roomType == null ? other$roomType == null : this$roomType.equals(other$roomType);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof RoomPropertiesWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $roomType = this.getRoomType();
        result = result * PRIME + ($roomType == null ? 43 : $roomType.hashCode());
        return result;
    }

    public String toString() {
        return "RoomPropertiesWebSocketMessage(name=" + this.getName() + ", roomType=" + this.getRoomType() + ")";
    }
}
