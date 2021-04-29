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
import com.nextcloud.talk.models.json.generic.GenericOCS;

import java.util.List;

@JsonObject
public class SignalingOCS extends GenericOCS {
    @JsonField(name = "data")
    List<Signaling> signalings;

    public List<Signaling> getSignalings() {
        return this.signalings;
    }

    public void setSignalings(List<Signaling> signalings) {
        this.signalings = signalings;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SignalingOCS)) {
            return false;
        }
        final SignalingOCS other = (SignalingOCS) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$signalings = this.getSignalings();
        final Object other$signalings = other.getSignalings();

        return this$signalings == null ? other$signalings == null : this$signalings.equals(other$signalings);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SignalingOCS;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $signalings = this.getSignalings();
        result = result * PRIME + ($signalings == null ? 43 : $signalings.hashCode());
        return result;
    }

    public String toString() {
        return "SignalingOCS(signalings=" + this.getSignalings() + ")";
    }
}
