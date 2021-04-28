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

package com.nextcloud.talk.models.json.capabilities;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

import java.util.List;

@Parcel
@JsonObject
public class NotificationsCapability {
    @JsonField(name = "ocs-endpoints")
    List<String> features;

    public NotificationsCapability() {
    }

    public List<String> getFeatures() {
        return this.features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NotificationsCapability)) {
            return false;
        }
        final NotificationsCapability other = (NotificationsCapability) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$features = this.getFeatures();
        final Object other$features = other.getFeatures();
        if (this$features == null ? other$features != null : !this$features.equals(other$features)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NotificationsCapability;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $features = this.getFeatures();
        result = result * PRIME + ($features == null ? 43 : $features.hashCode());
        return result;
    }

    public String toString() {
        return "NotificationsCapability(features=" + this.getFeatures() + ")";
    }
}
