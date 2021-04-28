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
public class HelloResponseWebSocketMessage {
    @JsonField(name = "resumeid")
    String resumeId;

    @JsonField(name = "sessionid")
    String sessionId;

    @JsonField(name = "server")
    ServerHelloResponseFeaturesWebSocketMessage serverHelloResponseFeaturesWebSocketMessage;

    public HelloResponseWebSocketMessage() {
    }

    public boolean serverHasMCUSupport() {
        return serverHelloResponseFeaturesWebSocketMessage != null && serverHelloResponseFeaturesWebSocketMessage.getFeatures() != null
                && serverHelloResponseFeaturesWebSocketMessage.getFeatures().contains("mcu");
    }

    public String getResumeId() {
        return this.resumeId;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public ServerHelloResponseFeaturesWebSocketMessage getServerHelloResponseFeaturesWebSocketMessage() {
        return this.serverHelloResponseFeaturesWebSocketMessage;
    }

    public void setResumeId(String resumeId) {
        this.resumeId = resumeId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setServerHelloResponseFeaturesWebSocketMessage(ServerHelloResponseFeaturesWebSocketMessage serverHelloResponseFeaturesWebSocketMessage) {
        this.serverHelloResponseFeaturesWebSocketMessage = serverHelloResponseFeaturesWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HelloResponseWebSocketMessage)) {
            return false;
        }
        final HelloResponseWebSocketMessage other = (HelloResponseWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$resumeId = this.getResumeId();
        final Object other$resumeId = other.getResumeId();
        if (this$resumeId == null ? other$resumeId != null : !this$resumeId.equals(other$resumeId)) {
            return false;
        }
        final Object this$sessionId = this.getSessionId();
        final Object other$sessionId = other.getSessionId();
        if (this$sessionId == null ? other$sessionId != null : !this$sessionId.equals(other$sessionId)) {
            return false;
        }
        final Object this$serverHelloResponseFeaturesWebSocketMessage = this.getServerHelloResponseFeaturesWebSocketMessage();
        final Object other$serverHelloResponseFeaturesWebSocketMessage = other.getServerHelloResponseFeaturesWebSocketMessage();
        if (this$serverHelloResponseFeaturesWebSocketMessage == null ? other$serverHelloResponseFeaturesWebSocketMessage != null : !this$serverHelloResponseFeaturesWebSocketMessage.equals(other$serverHelloResponseFeaturesWebSocketMessage)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof HelloResponseWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $resumeId = this.getResumeId();
        result = result * PRIME + ($resumeId == null ? 43 : $resumeId.hashCode());
        final Object $sessionId = this.getSessionId();
        result = result * PRIME + ($sessionId == null ? 43 : $sessionId.hashCode());
        final Object $serverHelloResponseFeaturesWebSocketMessage = this.getServerHelloResponseFeaturesWebSocketMessage();
        result = result * PRIME + ($serverHelloResponseFeaturesWebSocketMessage == null ? 43 : $serverHelloResponseFeaturesWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "HelloResponseWebSocketMessage(resumeId=" + this.getResumeId() + ", sessionId=" + this.getSessionId() + ", serverHelloResponseFeaturesWebSocketMessage=" + this.getServerHelloResponseFeaturesWebSocketMessage() + ")";
    }
}
