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
public class HelloWebSocketMessage {
    @JsonField(name = "version")
    String version;

    @JsonField(name = "resumeid")
    String resumeid;

    @JsonField(name = "auth")
    AuthWebSocketMessage authWebSocketMessage;

    public String getVersion() {
        return this.version;
    }

    public String getResumeid() {
        return this.resumeid;
    }

    public AuthWebSocketMessage getAuthWebSocketMessage() {
        return this.authWebSocketMessage;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setResumeid(String resumeid) {
        this.resumeid = resumeid;
    }

    public void setAuthWebSocketMessage(AuthWebSocketMessage authWebSocketMessage) {
        this.authWebSocketMessage = authWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HelloWebSocketMessage)) {
            return false;
        }
        final HelloWebSocketMessage other = (HelloWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$version = this.getVersion();
        final Object other$version = other.getVersion();
        if (this$version == null ? other$version != null : !this$version.equals(other$version)) {
            return false;
        }
        final Object this$resumeid = this.getResumeid();
        final Object other$resumeid = other.getResumeid();
        if (this$resumeid == null ? other$resumeid != null : !this$resumeid.equals(other$resumeid)) {
            return false;
        }
        final Object this$authWebSocketMessage = this.getAuthWebSocketMessage();
        final Object other$authWebSocketMessage = other.getAuthWebSocketMessage();

        return this$authWebSocketMessage == null ? other$authWebSocketMessage == null : this$authWebSocketMessage.equals(other$authWebSocketMessage);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof HelloWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $version = this.getVersion();
        result = result * PRIME + ($version == null ? 43 : $version.hashCode());
        final Object $resumeid = this.getResumeid();
        result = result * PRIME + ($resumeid == null ? 43 : $resumeid.hashCode());
        final Object $authWebSocketMessage = this.getAuthWebSocketMessage();
        result = result * PRIME + ($authWebSocketMessage == null ? 43 : $authWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "HelloWebSocketMessage(version=" + this.getVersion() + ", resumeid=" + this.getResumeid() + ", authWebSocketMessage=" + this.getAuthWebSocketMessage() + ")";
    }
}
