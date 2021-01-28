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

package com.nextcloud.talk.models.json.signaling;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@JsonObject
@Parcel
public class NCMessagePayload {
    @JsonField(name = "type")
    String type;

    @JsonField(name = "sdp")
    String sdp;

    @JsonField(name = "nick")
    String nick;

    @JsonField(name = "candidate")
    NCIceCandidate iceCandidate;

    @JsonField(name = "name")
    String name;

    public NCMessagePayload() {
    }

    public String getType() {
        return this.type;
    }

    public String getSdp() {
        return this.sdp;
    }

    public String getNick() {
        return this.nick;
    }

    public NCIceCandidate getIceCandidate() {
        return this.iceCandidate;
    }

    public String getName() {
        return this.name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSdp(String sdp) {
        this.sdp = sdp;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public void setIceCandidate(NCIceCandidate iceCandidate) {
        this.iceCandidate = iceCandidate;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof NCMessagePayload)) return false;
        final NCMessagePayload other = (NCMessagePayload) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
        final Object this$sdp = this.getSdp();
        final Object other$sdp = other.getSdp();
        if (this$sdp == null ? other$sdp != null : !this$sdp.equals(other$sdp)) return false;
        final Object this$nick = this.getNick();
        final Object other$nick = other.getNick();
        if (this$nick == null ? other$nick != null : !this$nick.equals(other$nick)) return false;
        final Object this$iceCandidate = this.getIceCandidate();
        final Object other$iceCandidate = other.getIceCandidate();
        if (this$iceCandidate == null ? other$iceCandidate != null : !this$iceCandidate.equals(other$iceCandidate))
            return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NCMessagePayload;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $sdp = this.getSdp();
        result = result * PRIME + ($sdp == null ? 43 : $sdp.hashCode());
        final Object $nick = this.getNick();
        result = result * PRIME + ($nick == null ? 43 : $nick.hashCode());
        final Object $iceCandidate = this.getIceCandidate();
        result = result * PRIME + ($iceCandidate == null ? 43 : $iceCandidate.hashCode());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        return result;
    }

    public String toString() {
        return "NCMessagePayload(type=" + this.getType() + ", sdp=" + this.getSdp() + ", nick=" + this.getNick() + ", iceCandidate=" + this.getIceCandidate() + ", name=" + this.getName() + ")";
    }
}
