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

package com.nextcloud.talk.models.json.signaling.settings;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import java.util.List;

@JsonObject
public class Settings {
    @JsonField(name = "stunservers")
    List<IceServer> stunServers;

    @JsonField(name = "turnservers")
    List<IceServer> turnServers;

    @JsonField(name = "server")
    String externalSignalingServer;

    @JsonField(name = "ticket")
    String externalSignalingTicket;

    public Settings() {
    }

    public List<IceServer> getStunServers() {
        return this.stunServers;
    }

    public List<IceServer> getTurnServers() {
        return this.turnServers;
    }

    public String getExternalSignalingServer() {
        return this.externalSignalingServer;
    }

    public String getExternalSignalingTicket() {
        return this.externalSignalingTicket;
    }

    public void setStunServers(List<IceServer> stunServers) {
        this.stunServers = stunServers;
    }

    public void setTurnServers(List<IceServer> turnServers) {
        this.turnServers = turnServers;
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
        if (!(o instanceof Settings)) {
            return false;
        }
        final Settings other = (Settings) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$stunServers = this.getStunServers();
        final Object other$stunServers = other.getStunServers();
        if (this$stunServers == null ? other$stunServers != null : !this$stunServers.equals(other$stunServers)) {
            return false;
        }
        final Object this$turnServers = this.getTurnServers();
        final Object other$turnServers = other.getTurnServers();
        if (this$turnServers == null ? other$turnServers != null : !this$turnServers.equals(other$turnServers)) {
            return false;
        }
        final Object this$externalSignalingServer = this.getExternalSignalingServer();
        final Object other$externalSignalingServer = other.getExternalSignalingServer();
        if (this$externalSignalingServer == null ? other$externalSignalingServer != null : !this$externalSignalingServer.equals(other$externalSignalingServer)) {
            return false;
        }
        final Object this$externalSignalingTicket = this.getExternalSignalingTicket();
        final Object other$externalSignalingTicket = other.getExternalSignalingTicket();
        if (this$externalSignalingTicket == null ? other$externalSignalingTicket != null : !this$externalSignalingTicket.equals(other$externalSignalingTicket)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Settings;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $stunServers = this.getStunServers();
        result = result * PRIME + ($stunServers == null ? 43 : $stunServers.hashCode());
        final Object $turnServers = this.getTurnServers();
        result = result * PRIME + ($turnServers == null ? 43 : $turnServers.hashCode());
        final Object $externalSignalingServer = this.getExternalSignalingServer();
        result = result * PRIME + ($externalSignalingServer == null ? 43 : $externalSignalingServer.hashCode());
        final Object $externalSignalingTicket = this.getExternalSignalingTicket();
        result = result * PRIME + ($externalSignalingTicket == null ? 43 : $externalSignalingTicket.hashCode());
        return result;
    }

    public String toString() {
        return "Settings(stunServers=" + this.getStunServers() + ", turnServers=" + this.getTurnServers() + ", externalSignalingServer=" + this.getExternalSignalingServer() + ", externalSignalingTicket=" + this.getExternalSignalingTicket() + ")";
    }
}
