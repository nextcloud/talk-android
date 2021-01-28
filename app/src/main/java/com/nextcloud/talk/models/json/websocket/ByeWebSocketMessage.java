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

import java.util.HashMap;

@JsonObject
@Parcel
public class ByeWebSocketMessage extends BaseWebSocketMessage {
    @JsonField(name = "bye")
    HashMap<String, Object> bye;

    public ByeWebSocketMessage() {
    }

    public HashMap<String, Object> getBye() {
        return this.bye;
    }

    public void setBye(HashMap<String, Object> bye) {
        this.bye = bye;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ByeWebSocketMessage)) return false;
        final ByeWebSocketMessage other = (ByeWebSocketMessage) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$bye = this.getBye();
        final Object other$bye = other.getBye();
        if (this$bye == null ? other$bye != null : !this$bye.equals(other$bye)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ByeWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $bye = this.getBye();
        result = result * PRIME + ($bye == null ? 43 : $bye.hashCode());
        return result;
    }

    public String toString() {
        return "ByeWebSocketMessage(bye=" + this.getBye() + ")";
    }
}
