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
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;

import org.parceler.Parcel;

@JsonObject
@Parcel
public class CallWebSocketMessage {
    @JsonField(name = "recipient")
    ActorWebSocketMessage recipientWebSocketMessage;

    @JsonField(name = "sender")
    ActorWebSocketMessage senderWebSocketMessage;

    @JsonField(name = "data")
    NCSignalingMessage ncSignalingMessage;

    public CallWebSocketMessage() {
    }

    public ActorWebSocketMessage getRecipientWebSocketMessage() {
        return this.recipientWebSocketMessage;
    }

    public ActorWebSocketMessage getSenderWebSocketMessage() {
        return this.senderWebSocketMessage;
    }

    public NCSignalingMessage getNcSignalingMessage() {
        return this.ncSignalingMessage;
    }

    public void setRecipientWebSocketMessage(ActorWebSocketMessage recipientWebSocketMessage) {
        this.recipientWebSocketMessage = recipientWebSocketMessage;
    }

    public void setSenderWebSocketMessage(ActorWebSocketMessage senderWebSocketMessage) {
        this.senderWebSocketMessage = senderWebSocketMessage;
    }

    public void setNcSignalingMessage(NCSignalingMessage ncSignalingMessage) {
        this.ncSignalingMessage = ncSignalingMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof CallWebSocketMessage)) return false;
        final CallWebSocketMessage other = (CallWebSocketMessage) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$recipientWebSocketMessage = this.getRecipientWebSocketMessage();
        final Object other$recipientWebSocketMessage = other.getRecipientWebSocketMessage();
        if (this$recipientWebSocketMessage == null ? other$recipientWebSocketMessage != null : !this$recipientWebSocketMessage.equals(other$recipientWebSocketMessage))
            return false;
        final Object this$senderWebSocketMessage = this.getSenderWebSocketMessage();
        final Object other$senderWebSocketMessage = other.getSenderWebSocketMessage();
        if (this$senderWebSocketMessage == null ? other$senderWebSocketMessage != null : !this$senderWebSocketMessage.equals(other$senderWebSocketMessage))
            return false;
        final Object this$ncSignalingMessage = this.getNcSignalingMessage();
        final Object other$ncSignalingMessage = other.getNcSignalingMessage();
        if (this$ncSignalingMessage == null ? other$ncSignalingMessage != null : !this$ncSignalingMessage.equals(other$ncSignalingMessage))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof CallWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $recipientWebSocketMessage = this.getRecipientWebSocketMessage();
        result = result * PRIME + ($recipientWebSocketMessage == null ? 43 : $recipientWebSocketMessage.hashCode());
        final Object $senderWebSocketMessage = this.getSenderWebSocketMessage();
        result = result * PRIME + ($senderWebSocketMessage == null ? 43 : $senderWebSocketMessage.hashCode());
        final Object $ncSignalingMessage = this.getNcSignalingMessage();
        result = result * PRIME + ($ncSignalingMessage == null ? 43 : $ncSignalingMessage.hashCode());
        return result;
    }

    public String toString() {
        return "CallWebSocketMessage(recipientWebSocketMessage=" + this.getRecipientWebSocketMessage() + ", senderWebSocketMessage=" + this.getSenderWebSocketMessage() + ", ncSignalingMessage=" + this.getNcSignalingMessage() + ")";
    }
}
