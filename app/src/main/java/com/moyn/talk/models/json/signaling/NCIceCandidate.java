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

package com.moyn.talk.models.json.signaling;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@JsonObject
@Parcel
public class NCIceCandidate {
    @JsonField(name = "sdpMLineIndex")
    int sdpMLineIndex;

    @JsonField(name = "sdpMid")
    String sdpMid;

    @JsonField(name = "candidate")
    String candidate;

    public int getSdpMLineIndex() {
        return this.sdpMLineIndex;
    }

    public String getSdpMid() {
        return this.sdpMid;
    }

    public String getCandidate() {
        return this.candidate;
    }

    public void setSdpMLineIndex(int sdpMLineIndex) {
        this.sdpMLineIndex = sdpMLineIndex;
    }

    public void setSdpMid(String sdpMid) {
        this.sdpMid = sdpMid;
    }

    public void setCandidate(String candidate) {
        this.candidate = candidate;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NCIceCandidate)) {
            return false;
        }
        final NCIceCandidate other = (NCIceCandidate) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.getSdpMLineIndex() != other.getSdpMLineIndex()) {
            return false;
        }
        final Object this$sdpMid = this.getSdpMid();
        final Object other$sdpMid = other.getSdpMid();
        if (this$sdpMid == null ? other$sdpMid != null : !this$sdpMid.equals(other$sdpMid)) {
            return false;
        }
        final Object this$candidate = this.getCandidate();
        final Object other$candidate = other.getCandidate();

        return this$candidate == null ? other$candidate == null : this$candidate.equals(other$candidate);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NCIceCandidate;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getSdpMLineIndex();
        final Object $sdpMid = this.getSdpMid();
        result = result * PRIME + ($sdpMid == null ? 43 : $sdpMid.hashCode());
        final Object $candidate = this.getCandidate();
        result = result * PRIME + ($candidate == null ? 43 : $candidate.hashCode());
        return result;
    }

    public String toString() {
        return "NCIceCandidate(sdpMLineIndex=" + this.getSdpMLineIndex() + ", sdpMid=" + this.getSdpMid() + ", candidate=" + this.getCandidate() + ")";
    }
}
