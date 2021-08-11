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
public class RequestOfferSignalingMessage {
    @JsonField(name = "recipient")
    ActorWebSocketMessage actorWebSocketMessage;

    @JsonField(name = "data")
    SignalingDataWebSocketMessageForOffer signalingDataWebSocketMessageForOffer;

    public ActorWebSocketMessage getActorWebSocketMessage() {
        return this.actorWebSocketMessage;
    }

    public SignalingDataWebSocketMessageForOffer getSignalingDataWebSocketMessageForOffer() {
        return this.signalingDataWebSocketMessageForOffer;
    }

    public void setActorWebSocketMessage(ActorWebSocketMessage actorWebSocketMessage) {
        this.actorWebSocketMessage = actorWebSocketMessage;
    }

    public void setSignalingDataWebSocketMessageForOffer(SignalingDataWebSocketMessageForOffer signalingDataWebSocketMessageForOffer) {
        this.signalingDataWebSocketMessageForOffer = signalingDataWebSocketMessageForOffer;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RequestOfferSignalingMessage)) {
            return false;
        }
        final RequestOfferSignalingMessage other = (RequestOfferSignalingMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$actorWebSocketMessage = this.getActorWebSocketMessage();
        final Object other$actorWebSocketMessage = other.getActorWebSocketMessage();
        if (this$actorWebSocketMessage == null ? other$actorWebSocketMessage != null : !this$actorWebSocketMessage.equals(other$actorWebSocketMessage)) {
            return false;
        }
        final Object this$signalingDataWebSocketMessageForOffer = this.getSignalingDataWebSocketMessageForOffer();
        final Object other$signalingDataWebSocketMessageForOffer = other.getSignalingDataWebSocketMessageForOffer();

        return this$signalingDataWebSocketMessageForOffer == null ? other$signalingDataWebSocketMessageForOffer == null : this$signalingDataWebSocketMessageForOffer.equals(other$signalingDataWebSocketMessageForOffer);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof RequestOfferSignalingMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $actorWebSocketMessage = this.getActorWebSocketMessage();
        result = result * PRIME + ($actorWebSocketMessage == null ? 43 : $actorWebSocketMessage.hashCode());
        final Object $signalingDataWebSocketMessageForOffer = this.getSignalingDataWebSocketMessageForOffer();
        result = result * PRIME + ($signalingDataWebSocketMessageForOffer == null ? 43 : $signalingDataWebSocketMessageForOffer.hashCode());
        return result;
    }

    public String toString() {
        return "RequestOfferSignalingMessage(actorWebSocketMessage=" + this.getActorWebSocketMessage() + ", signalingDataWebSocketMessageForOffer=" + this.getSignalingDataWebSocketMessageForOffer() + ")";
    }
}
