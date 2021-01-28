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
import com.nextcloud.talk.models.json.converters.ObjectParcelConverter;

import org.parceler.ParcelPropertyConverter;

import java.util.HashMap;

@JsonObject
public class DataChannelMessageNick {
    @JsonField(name = "type")
    String type;

    @ParcelPropertyConverter(ObjectParcelConverter.class)
    @JsonField(name = "payload")
    HashMap<String, String> payload;

    public DataChannelMessageNick(String type) {
        this.type = type;
    }

    public DataChannelMessageNick() {
    }

    public String getType() {
        return this.type;
    }

    public HashMap<String, String> getPayload() {
        return this.payload;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPayload(HashMap<String, String> payload) {
        this.payload = payload;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof DataChannelMessageNick)) return false;
        final DataChannelMessageNick other = (DataChannelMessageNick) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
        final Object this$payload = this.getPayload();
        final Object other$payload = other.getPayload();
        if (this$payload == null ? other$payload != null : !this$payload.equals(other$payload)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof DataChannelMessageNick;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $payload = this.getPayload();
        result = result * PRIME + ($payload == null ? 43 : $payload.hashCode());
        return result;
    }

    public String toString() {
        return "DataChannelMessageNick(type=" + this.getType() + ", payload=" + this.getPayload() + ")";
    }
}
