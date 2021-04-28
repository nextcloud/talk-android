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
public class HelloOverallWebSocketMessage extends BaseWebSocketMessage {
    @JsonField(name = "hello")
    HelloWebSocketMessage helloWebSocketMessage;

    public HelloOverallWebSocketMessage() {
    }

    public HelloWebSocketMessage getHelloWebSocketMessage() {
        return this.helloWebSocketMessage;
    }

    public void setHelloWebSocketMessage(HelloWebSocketMessage helloWebSocketMessage) {
        this.helloWebSocketMessage = helloWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HelloOverallWebSocketMessage)) {
            return false;
        }
        final HelloOverallWebSocketMessage other = (HelloOverallWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$helloWebSocketMessage = this.getHelloWebSocketMessage();
        final Object other$helloWebSocketMessage = other.getHelloWebSocketMessage();
        if (this$helloWebSocketMessage == null ? other$helloWebSocketMessage != null : !this$helloWebSocketMessage.equals(other$helloWebSocketMessage)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof HelloOverallWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $helloWebSocketMessage = this.getHelloWebSocketMessage();
        result = result * PRIME + ($helloWebSocketMessage == null ? 43 : $helloWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "HelloOverallWebSocketMessage(helloWebSocketMessage=" + this.getHelloWebSocketMessage() + ")";
    }
}
