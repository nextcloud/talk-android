/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
