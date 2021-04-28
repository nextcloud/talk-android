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

@Parcel
@JsonObject
public class ErrorOverallWebSocketMessage extends BaseWebSocketMessage {
    @JsonField(name = "error")
    ErrorWebSocketMessage errorWebSocketMessage;

    public ErrorOverallWebSocketMessage() {
    }

    public ErrorWebSocketMessage getErrorWebSocketMessage() {
        return this.errorWebSocketMessage;
    }

    public void setErrorWebSocketMessage(ErrorWebSocketMessage errorWebSocketMessage) {
        this.errorWebSocketMessage = errorWebSocketMessage;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ErrorOverallWebSocketMessage)) {
            return false;
        }
        final ErrorOverallWebSocketMessage other = (ErrorOverallWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$errorWebSocketMessage = this.getErrorWebSocketMessage();
        final Object other$errorWebSocketMessage = other.getErrorWebSocketMessage();
        if (this$errorWebSocketMessage == null ? other$errorWebSocketMessage != null : !this$errorWebSocketMessage.equals(other$errorWebSocketMessage)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ErrorOverallWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $errorWebSocketMessage = this.getErrorWebSocketMessage();
        result = result * PRIME + ($errorWebSocketMessage == null ? 43 : $errorWebSocketMessage.hashCode());
        return result;
    }

    public String toString() {
        return "ErrorOverallWebSocketMessage(errorWebSocketMessage=" + this.getErrorWebSocketMessage() + ")";
    }
}
