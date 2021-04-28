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
public class AuthParametersWebSocketMessage {
    @JsonField(name = "userid")
    String userid;

    @JsonField(name = "ticket")
    String ticket;

    public AuthParametersWebSocketMessage() {
    }

    public String getUserid() {
        return this.userid;
    }

    public String getTicket() {
        return this.ticket;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof AuthParametersWebSocketMessage)) {
            return false;
        }
        final AuthParametersWebSocketMessage other = (AuthParametersWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$userid = this.getUserid();
        final Object other$userid = other.getUserid();
        if (this$userid == null ? other$userid != null : !this$userid.equals(other$userid)) {
            return false;
        }
        final Object this$ticket = this.getTicket();
        final Object other$ticket = other.getTicket();
        if (this$ticket == null ? other$ticket != null : !this$ticket.equals(other$ticket)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof AuthParametersWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $userid = this.getUserid();
        result = result * PRIME + ($userid == null ? 43 : $userid.hashCode());
        final Object $ticket = this.getTicket();
        result = result * PRIME + ($ticket == null ? 43 : $ticket.hashCode());
        return result;
    }

    public String toString() {
        return "AuthParametersWebSocketMessage(userid=" + this.getUserid() + ", ticket=" + this.getTicket() + ")";
    }
}
