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
public class AuthWebSocketMessage {
    @JsonField(name = "url")
    String url;

    @JsonField(name = "params")
    AuthParametersWebSocketMessage authParametersWebSocketMessage;

    public String getUrl() {
        return this.url;
    }

    public AuthParametersWebSocketMessage getAuthParametersWebSocketMessage() {
        return this.authParametersWebSocketMessage;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setAuthParametersWebSocketMessage(AuthParametersWebSocketMessage authParametersWebSocketMessage) {
        this.authParametersWebSocketMessage = authParametersWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AuthWebSocketMessage)) {
            return false;
        }
        final AuthWebSocketMessage other = (AuthWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$url = this.getUrl();
        final Object other$url = other.getUrl();
        if (this$url == null ? other$url != null : !this$url.equals(other$url)) {
            return false;
        }
        final Object this$authParametersWebSocketMessage = this.getAuthParametersWebSocketMessage();
        final Object other$authParametersWebSocketMessage = other.getAuthParametersWebSocketMessage();

        return this$authParametersWebSocketMessage == null ? other$authParametersWebSocketMessage == null : this$authParametersWebSocketMessage.equals(other$authParametersWebSocketMessage);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof AuthWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $url = this.getUrl();
        result = result * PRIME + ($url == null ? 43 : $url.hashCode());
        final Object $authParametersWebSocketMessage = this.getAuthParametersWebSocketMessage();
        result = result * PRIME + ($authParametersWebSocketMessage == null ? 43 : $authParametersWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "AuthWebSocketMessage(url=" + this.getUrl() + ", authParametersWebSocketMessage=" + this.getAuthParametersWebSocketMessage() + ")";
    }
}
