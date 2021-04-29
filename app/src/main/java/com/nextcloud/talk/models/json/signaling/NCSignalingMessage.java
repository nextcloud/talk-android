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

package com.nextcloud.talk.models.json.signaling;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@JsonObject
@Parcel
public class NCSignalingMessage {
    @JsonField(name = "from")
    String from;
    @JsonField(name = "to")
    String to;
    @JsonField(name = "type")
    String type;
    @JsonField(name = "payload")
    NCMessagePayload payload;
    @JsonField(name = "roomType")
    String roomType;
    @JsonField(name = "sid")
    String sid;
    @JsonField(name = "prefix")
    String prefix;

    public String getFrom() {
        return this.from;
    }

    public String getTo() {
        return this.to;
    }

    public String getType() {
        return this.type;
    }

    public NCMessagePayload getPayload() {
        return this.payload;
    }

    public String getRoomType() {
        return this.roomType;
    }

    public String getSid() {
        return this.sid;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPayload(NCMessagePayload payload) {
        this.payload = payload;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NCSignalingMessage)) {
            return false;
        }
        final NCSignalingMessage other = (NCSignalingMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$from = this.getFrom();
        final Object other$from = other.getFrom();
        if (this$from == null ? other$from != null : !this$from.equals(other$from)) {
            return false;
        }
        final Object this$to = this.getTo();
        final Object other$to = other.getTo();
        if (this$to == null ? other$to != null : !this$to.equals(other$to)) {
            return false;
        }
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        final Object this$payload = this.getPayload();
        final Object other$payload = other.getPayload();
        if (this$payload == null ? other$payload != null : !this$payload.equals(other$payload)) {
            return false;
        }
        final Object this$roomType = this.getRoomType();
        final Object other$roomType = other.getRoomType();
        if (this$roomType == null ? other$roomType != null : !this$roomType.equals(other$roomType)) {
            return false;
        }
        final Object this$sid = this.getSid();
        final Object other$sid = other.getSid();
        if (this$sid == null ? other$sid != null : !this$sid.equals(other$sid)) {
            return false;
        }
        final Object this$prefix = this.getPrefix();
        final Object other$prefix = other.getPrefix();

        return this$prefix == null ? other$prefix == null : this$prefix.equals(other$prefix);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NCSignalingMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $from = this.getFrom();
        result = result * PRIME + ($from == null ? 43 : $from.hashCode());
        final Object $to = this.getTo();
        result = result * PRIME + ($to == null ? 43 : $to.hashCode());
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $payload = this.getPayload();
        result = result * PRIME + ($payload == null ? 43 : $payload.hashCode());
        final Object $roomType = this.getRoomType();
        result = result * PRIME + ($roomType == null ? 43 : $roomType.hashCode());
        final Object $sid = this.getSid();
        result = result * PRIME + ($sid == null ? 43 : $sid.hashCode());
        final Object $prefix = this.getPrefix();
        result = result * PRIME + ($prefix == null ? 43 : $prefix.hashCode());
        return result;
    }

    public String toString() {
        return "NCSignalingMessage(from=" + this.getFrom() + ", to=" + this.getTo() + ", type=" + this.getType() + ", payload=" + this.getPayload() + ", roomType=" + this.getRoomType() + ", sid=" + this.getSid() + ", prefix=" + this.getPrefix() + ")";
    }
}
