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
public class ActorWebSocketMessage {
    @JsonField(name = "type")
    String type;

    @JsonField(name = "sessionid")
    String sessionId;

    @JsonField(name = "userid")
    String userid;

    public ActorWebSocketMessage() {
    }

    public String getType() {
        return this.type;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public String getUserid() {
        return this.userid;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ActorWebSocketMessage)) return false;
        final ActorWebSocketMessage other = (ActorWebSocketMessage) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
        final Object this$sessionId = this.getSessionId();
        final Object other$sessionId = other.getSessionId();
        if (this$sessionId == null ? other$sessionId != null : !this$sessionId.equals(other$sessionId)) return false;
        final Object this$userid = this.getUserid();
        final Object other$userid = other.getUserid();
        if (this$userid == null ? other$userid != null : !this$userid.equals(other$userid)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ActorWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $sessionId = this.getSessionId();
        result = result * PRIME + ($sessionId == null ? 43 : $sessionId.hashCode());
        final Object $userid = this.getUserid();
        result = result * PRIME + ($userid == null ? 43 : $userid.hashCode());
        return result;
    }

    public String toString() {
        return "ActorWebSocketMessage(type=" + this.getType() + ", sessionId=" + this.getSessionId() + ", userid=" + this.getUserid() + ")";
    }
}
