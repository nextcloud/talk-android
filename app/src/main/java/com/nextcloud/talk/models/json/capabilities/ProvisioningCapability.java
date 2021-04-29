/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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
public class ProvisioningCapability {
    @JsonField(name = "AccountPropertyScopesVersion")
    Integer accountPropertyScopesVersion;

    public Integer getAccountPropertyScopesVersion() {
        return this.accountPropertyScopesVersion;
    }

    public void setAccountPropertyScopesVersion(Integer accountPropertyScopesVersion) {
        this.accountPropertyScopesVersion = accountPropertyScopesVersion;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProvisioningCapability)) {
            return false;
        }
        final ProvisioningCapability other = (ProvisioningCapability) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$accountPropertyScopesVersion = this.getAccountPropertyScopesVersion();
        final Object other$accountPropertyScopesVersion = other.getAccountPropertyScopesVersion();

        return this$accountPropertyScopesVersion == null ? other$accountPropertyScopesVersion == null : this$accountPropertyScopesVersion.equals(other$accountPropertyScopesVersion);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ProvisioningCapability;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $accountPropertyScopesVersion = this.getAccountPropertyScopesVersion();
        result = result * PRIME + ($accountPropertyScopesVersion == null ? 43 : $accountPropertyScopesVersion.hashCode());
        return result;
    }

    public String toString() {
        return "ProvisioningCapability(accountPropertyScopesVersion=" + this.getAccountPropertyScopesVersion() + ")";
    }
}
