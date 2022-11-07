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

import androidx.annotation.Nullable;

public class PeerConnectionEvent {
    private final PeerConnectionEventType peerConnectionEventType;
    private final String sessionId;
    private final String nick;
    private final Boolean changeValue;
    private final String videoStreamType;

    public PeerConnectionEvent(PeerConnectionEventType peerConnectionEventType, @Nullable String sessionId,
                               @Nullable String nick, Boolean changeValue, @Nullable String videoStreamType) {
        this.peerConnectionEventType = peerConnectionEventType;
        this.nick = nick;
        this.changeValue = changeValue;
        this.sessionId = sessionId;
        this.videoStreamType = videoStreamType;
    }

    public PeerConnectionEventType getPeerConnectionEventType() {
        return this.peerConnectionEventType;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public String getNick() {
        return this.nick;
    }

    public Boolean getChangeValue() {
        return this.changeValue;
    }

    public String getVideoStreamType() {
        return this.videoStreamType;
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
        final Object this$sessionId = this.getSessionId();
        final Object other$sessionId = other.getSessionId();
        if (this$sessionId == null ? other$sessionId != null : !this$sessionId.equals(other$sessionId)) {
            return false;
        }
        final Object this$nick = this.getNick();
        final Object other$nick = other.getNick();
        if (this$nick == null ? other$nick != null : !this$nick.equals(other$nick)) {
            return false;
        }
        final Object this$changeValue = this.getChangeValue();
        final Object other$changeValue = other.getChangeValue();
        if (this$changeValue == null ? other$changeValue != null : !this$changeValue.equals(other$changeValue)) {
            return false;
        }
        final Object this$videoStreamType = this.getVideoStreamType();
        final Object other$videoStreamType = other.getVideoStreamType();

        return this$videoStreamType == null ? other$videoStreamType == null : this$videoStreamType.equals(other$videoStreamType);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof PeerConnectionEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $peerConnectionEventType = this.getPeerConnectionEventType();
        result = result * PRIME + ($peerConnectionEventType == null ? 43 : $peerConnectionEventType.hashCode());
        final Object $sessionId = this.getSessionId();
        result = result * PRIME + ($sessionId == null ? 43 : $sessionId.hashCode());
        final Object $nick = this.getNick();
        result = result * PRIME + ($nick == null ? 43 : $nick.hashCode());
        final Object $changeValue = this.getChangeValue();
        result = result * PRIME + ($changeValue == null ? 43 : $changeValue.hashCode());
        final Object $videoStreamType = this.getVideoStreamType();
        result = result * PRIME + ($videoStreamType == null ? 43 : $videoStreamType.hashCode());
        return result;
    }

    public String toString() {
        return "PeerConnectionEvent(peerConnectionEventType=" + this.getPeerConnectionEventType() + ", sessionId=" + this.getSessionId() + ", nick=" + this.getNick() + ", changeValue=" + this.getChangeValue() + ", videoStreamType=" + this.getVideoStreamType() + ")";
    }

    public enum PeerConnectionEventType {
        PEER_CONNECTED, PEER_DISCONNECTED, PEER_CLOSED, SENSOR_FAR, SENSOR_NEAR, PUBLISHER_FAILED
    }
}
