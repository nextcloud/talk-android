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
public class CallOverallWebSocketMessage extends BaseWebSocketMessage {
    @JsonField(name = "message")
    CallWebSocketMessage callWebSocketMessage;

    public CallOverallWebSocketMessage() {
    }

    public CallWebSocketMessage getCallWebSocketMessage() {
        return this.callWebSocketMessage;
    }

    public void setCallWebSocketMessage(CallWebSocketMessage callWebSocketMessage) {
        this.callWebSocketMessage = callWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof CallOverallWebSocketMessage)) return false;
        final CallOverallWebSocketMessage other = (CallOverallWebSocketMessage) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$callWebSocketMessage = this.getCallWebSocketMessage();
        final Object other$callWebSocketMessage = other.getCallWebSocketMessage();
        if (this$callWebSocketMessage == null ? other$callWebSocketMessage != null : !this$callWebSocketMessage.equals(other$callWebSocketMessage))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof CallOverallWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $callWebSocketMessage = this.getCallWebSocketMessage();
        result = result * PRIME + ($callWebSocketMessage == null ? 43 : $callWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "CallOverallWebSocketMessage(callWebSocketMessage=" + this.getCallWebSocketMessage() + ")";
    }
}
