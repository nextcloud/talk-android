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

@Parcel
@JsonObject
public class CapabilitiesList {
    @JsonField(name = "capabilities")
    Capabilities capabilities;

    public CapabilitiesList() {
    }

    public Capabilities getCapabilities() {
        return this.capabilities;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CapabilitiesList)) {
            return false;
        }
        final CapabilitiesList other = (CapabilitiesList) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$capabilities = this.getCapabilities();
        final Object other$capabilities = other.getCapabilities();
        if (this$capabilities == null ? other$capabilities != null : !this$capabilities.equals(other$capabilities)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof CapabilitiesList;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $capabilities = this.getCapabilities();
        result = result * PRIME + ($capabilities == null ? 43 : $capabilities.hashCode());
        return result;
    }

    public String toString() {
        return "CapabilitiesList(capabilities=" + this.getCapabilities() + ")";
    }
}
