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
public class SignalingDataWebSocketMessageForOffer {
    @JsonField(name = "type")
    String type;

    @JsonField(name = "roomType")
    String roomType;

    public String getType() {
        return this.type;
    }

    public String getRoomType() {
        return this.roomType;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SignalingDataWebSocketMessageForOffer)) {
            return false;
        }
        final SignalingDataWebSocketMessageForOffer other = (SignalingDataWebSocketMessageForOffer) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        final Object this$roomType = this.getRoomType();
        final Object other$roomType = other.getRoomType();

        return this$roomType == null ? other$roomType == null : this$roomType.equals(other$roomType);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SignalingDataWebSocketMessageForOffer;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $roomType = this.getRoomType();
        result = result * PRIME + ($roomType == null ? 43 : $roomType.hashCode());
        return result;
    }

    public String toString() {
        return "SignalingDataWebSocketMessageForOffer(type=" + this.getType() + ", roomType=" + this.getRoomType() + ")";
    }
}
