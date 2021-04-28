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

import com.nextcloud.talk.models.json.signaling.NCIceCandidate;

import org.webrtc.SessionDescription;

import androidx.annotation.Nullable;

public class SessionDescriptionSendEvent {
    @Nullable
    private final SessionDescription sessionDescription;
    private final String peerId;
    private final String type;
    @Nullable
    private final NCIceCandidate ncIceCandidate;
    private final String videoStreamType;

    public SessionDescriptionSendEvent(@Nullable SessionDescription sessionDescription, String peerId, String type,
                                       @Nullable NCIceCandidate ncIceCandidate, @Nullable String videoStreamType) {
        this.sessionDescription = sessionDescription;
        this.peerId = peerId;
        this.type = type;
        this.ncIceCandidate = ncIceCandidate;
        this.videoStreamType = videoStreamType;
    }

    @Nullable
    public SessionDescription getSessionDescription() {
        return this.sessionDescription;
    }

    public String getPeerId() {
        return this.peerId;
    }

    public String getType() {
        return this.type;
    }

    @Nullable
    public NCIceCandidate getNcIceCandidate() {
        return this.ncIceCandidate;
    }

    public String getVideoStreamType() {
        return this.videoStreamType;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SessionDescriptionSendEvent)) {
            return false;
        }
        final SessionDescriptionSendEvent other = (SessionDescriptionSendEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$sessionDescription = this.getSessionDescription();
        final Object other$sessionDescription = other.getSessionDescription();
        if (this$sessionDescription == null ? other$sessionDescription != null : !this$sessionDescription.equals(other$sessionDescription)) {
            return false;
        }
        final Object this$peerId = this.getPeerId();
        final Object other$peerId = other.getPeerId();
        if (this$peerId == null ? other$peerId != null : !this$peerId.equals(other$peerId)) {
            return false;
        }
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        final Object this$ncIceCandidate = this.getNcIceCandidate();
        final Object other$ncIceCandidate = other.getNcIceCandidate();
        if (this$ncIceCandidate == null ? other$ncIceCandidate != null : !this$ncIceCandidate.equals(other$ncIceCandidate)) {
            return false;
        }
        final Object this$videoStreamType = this.getVideoStreamType();
        final Object other$videoStreamType = other.getVideoStreamType();
        if (this$videoStreamType == null ? other$videoStreamType != null : !this$videoStreamType.equals(other$videoStreamType)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SessionDescriptionSendEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $sessionDescription = this.getSessionDescription();
        result = result * PRIME + ($sessionDescription == null ? 43 : $sessionDescription.hashCode());
        final Object $peerId = this.getPeerId();
        result = result * PRIME + ($peerId == null ? 43 : $peerId.hashCode());
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $ncIceCandidate = this.getNcIceCandidate();
        result = result * PRIME + ($ncIceCandidate == null ? 43 : $ncIceCandidate.hashCode());
        final Object $videoStreamType = this.getVideoStreamType();
        result = result * PRIME + ($videoStreamType == null ? 43 : $videoStreamType.hashCode());
        return result;
    }

    public String toString() {
        return "SessionDescriptionSendEvent(sessionDescription=" + this.getSessionDescription() + ", peerId=" + this.getPeerId() + ", type=" + this.getType() + ", ncIceCandidate=" + this.getNcIceCandidate() + ", videoStreamType=" + this.getVideoStreamType() + ")";
    }
}
