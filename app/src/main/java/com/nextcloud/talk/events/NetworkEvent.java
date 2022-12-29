/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.events;

public class NetworkEvent {
    private final NetworkConnectionEvent networkConnectionEvent;

    public NetworkEvent(NetworkConnectionEvent networkConnectionEvent) {
        this.networkConnectionEvent = networkConnectionEvent;
    }

    public NetworkConnectionEvent getNetworkConnectionEvent() {
        return this.networkConnectionEvent;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NetworkEvent)) {
            return false;
        }
        final NetworkEvent other = (NetworkEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$networkConnectionEvent = this.getNetworkConnectionEvent();
        final Object other$networkConnectionEvent = other.getNetworkConnectionEvent();

        return this$networkConnectionEvent == null ? other$networkConnectionEvent == null : this$networkConnectionEvent.equals(other$networkConnectionEvent);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NetworkEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $networkConnectionEvent = this.getNetworkConnectionEvent();
        return result * PRIME + ($networkConnectionEvent == null ? 43 : $networkConnectionEvent.hashCode());
    }

    public String toString() {
        return "NetworkEvent(networkConnectionEvent=" + this.getNetworkConnectionEvent() + ")";
    }

    public enum NetworkConnectionEvent {
        NETWORK_CONNECTED, NETWORK_DISCONNECTED
    }
}
