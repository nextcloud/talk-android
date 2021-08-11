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

package com.moyn.talk.models;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class ExternalSignalingServer {
    @JsonField(name = "externalSignalingServer")
    String externalSignalingServer;
    @JsonField(name = "externalSignalingTicket")
    String externalSignalingTicket;

    public String getExternalSignalingServer() {
        return this.externalSignalingServer;
    }

    public String getExternalSignalingTicket() {
        return this.externalSignalingTicket;
    }

    public void setExternalSignalingServer(String externalSignalingServer) {
        this.externalSignalingServer = externalSignalingServer;
    }

    public void setExternalSignalingTicket(String externalSignalingTicket) {
        this.externalSignalingTicket = externalSignalingTicket;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ExternalSignalingServer)) {
            return false;
        }
        final ExternalSignalingServer other = (ExternalSignalingServer) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$externalSignalingServer = this.getExternalSignalingServer();
        final Object other$externalSignalingServer = other.getExternalSignalingServer();
        if (this$externalSignalingServer == null ? other$externalSignalingServer != null : !this$externalSignalingServer.equals(other$externalSignalingServer)) {
            return false;
        }
        final Object this$externalSignalingTicket = this.getExternalSignalingTicket();
        final Object other$externalSignalingTicket = other.getExternalSignalingTicket();

        return this$externalSignalingTicket == null ? other$externalSignalingTicket == null : this$externalSignalingTicket.equals(other$externalSignalingTicket);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ExternalSignalingServer;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $externalSignalingServer = this.getExternalSignalingServer();
        result = result * PRIME + ($externalSignalingServer == null ? 43 : $externalSignalingServer.hashCode());
        final Object $externalSignalingTicket = this.getExternalSignalingTicket();
        result = result * PRIME + ($externalSignalingTicket == null ? 43 : $externalSignalingTicket.hashCode());
        return result;
    }

    public String toString() {
        return "ExternalSignalingServer(externalSignalingServer=" + this.getExternalSignalingServer() + ", externalSignalingTicket=" + this.getExternalSignalingTicket() + ")";
    }
}
