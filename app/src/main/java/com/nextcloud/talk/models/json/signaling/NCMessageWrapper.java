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
public class NCMessageWrapper {
    @JsonField(name = "fn")
    NCSignalingMessage signalingMessage;

    // always a "message"
    @JsonField(name = "ev")
    String ev;

    @JsonField(name = "sessionId")
    String sessionId;

    public NCMessageWrapper() {
    }

    public NCSignalingMessage getSignalingMessage() {
        return this.signalingMessage;
    }

    public String getEv() {
        return this.ev;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public void setSignalingMessage(NCSignalingMessage signalingMessage) {
        this.signalingMessage = signalingMessage;
    }

    public void setEv(String ev) {
        this.ev = ev;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof NCMessageWrapper)) return false;
        final NCMessageWrapper other = (NCMessageWrapper) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$signalingMessage = this.getSignalingMessage();
        final Object other$signalingMessage = other.getSignalingMessage();
        if (this$signalingMessage == null ? other$signalingMessage != null : !this$signalingMessage.equals(other$signalingMessage))
            return false;
        final Object this$ev = this.getEv();
        final Object other$ev = other.getEv();
        if (this$ev == null ? other$ev != null : !this$ev.equals(other$ev)) return false;
        final Object this$sessionId = this.getSessionId();
        final Object other$sessionId = other.getSessionId();
        if (this$sessionId == null ? other$sessionId != null : !this$sessionId.equals(other$sessionId)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NCMessageWrapper;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $signalingMessage = this.getSignalingMessage();
        result = result * PRIME + ($signalingMessage == null ? 43 : $signalingMessage.hashCode());
        final Object $ev = this.getEv();
        result = result * PRIME + ($ev == null ? 43 : $ev.hashCode());
        final Object $sessionId = this.getSessionId();
        result = result * PRIME + ($sessionId == null ? 43 : $sessionId.hashCode());
        return result;
    }

    public String toString() {
        return "NCMessageWrapper(signalingMessage=" + this.getSignalingMessage() + ", ev=" + this.getEv() + ", sessionId=" + this.getSessionId() + ")";
    }
}
