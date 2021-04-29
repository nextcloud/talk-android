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
public class HelloResponseOverallWebSocketMessage extends BaseWebSocketMessage {
    @JsonField(name = "hello")
    HelloResponseWebSocketMessage helloResponseWebSocketMessage;

    public HelloResponseWebSocketMessage getHelloResponseWebSocketMessage() {
        return this.helloResponseWebSocketMessage;
    }

    public void setHelloResponseWebSocketMessage(HelloResponseWebSocketMessage helloResponseWebSocketMessage) {
        this.helloResponseWebSocketMessage = helloResponseWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HelloResponseOverallWebSocketMessage)) {
            return false;
        }
        final HelloResponseOverallWebSocketMessage other = (HelloResponseOverallWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$helloResponseWebSocketMessage = this.getHelloResponseWebSocketMessage();
        final Object other$helloResponseWebSocketMessage = other.getHelloResponseWebSocketMessage();

        return this$helloResponseWebSocketMessage == null ? other$helloResponseWebSocketMessage == null : this$helloResponseWebSocketMessage.equals(other$helloResponseWebSocketMessage);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof HelloResponseOverallWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $helloResponseWebSocketMessage = this.getHelloResponseWebSocketMessage();
        result = result * PRIME + ($helloResponseWebSocketMessage == null ? 43 : $helloResponseWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "HelloResponseOverallWebSocketMessage(helloResponseWebSocketMessage=" + this.getHelloResponseWebSocketMessage() + ")";
    }
}
