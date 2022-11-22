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

package com.nextcloud.talk.events;

public class PeerConnectionEvent {
    private final PeerConnectionEventType peerConnectionEventType;

    public PeerConnectionEvent(PeerConnectionEventType peerConnectionEventType) {
        this.peerConnectionEventType = peerConnectionEventType;
    }

    public PeerConnectionEventType getPeerConnectionEventType() {
        return this.peerConnectionEventType;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PeerConnectionEvent)) {
            return false;
        }
        final PeerConnectionEvent other = (PeerConnectionEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$peerConnectionEventType = this.getPeerConnectionEventType();
        final Object other$peerConnectionEventType = other.getPeerConnectionEventType();
        if (this$peerConnectionEventType == null ? other$peerConnectionEventType != null : !this$peerConnectionEventType.equals(other$peerConnectionEventType)) {
            return false;
        }

        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof PeerConnectionEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $peerConnectionEventType = this.getPeerConnectionEventType();
        result = result * PRIME + ($peerConnectionEventType == null ? 43 : $peerConnectionEventType.hashCode());
        return result;
    }

    public String toString() {
        return "PeerConnectionEvent(peerConnectionEventType=" + this.getPeerConnectionEventType() + ")";
    }

    public enum PeerConnectionEventType {
        SENSOR_FAR, SENSOR_NEAR
    }
}
