/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

package com.nextcloud.talk.models.json.generic;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class Status {
    @JsonField(name = "installed")
    public boolean installed;

    @JsonField(name = "maintenance")
    public boolean maintenance;

    @JsonField(name = "upgrade")
    public boolean needsUpgrade;

    @JsonField(name = "version")
    public String version;

    @JsonField(name = "versionstring")
    public String versionString;

    @JsonField(name = "edition")
    public String edition;

    @JsonField(name = "productname")
    public String productName;

    public boolean isInstalled() {
        return this.installed;
    }

    public boolean isMaintenance() {
        return this.maintenance;
    }

    public boolean isNeedsUpgrade() {
        return this.needsUpgrade;
    }

    public String getVersion() {
        return this.version;
    }

    public String getVersionString() {
        return this.versionString;
    }

    public String getEdition() {
        return this.edition;
    }

    public String getProductName() {
        return this.productName;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public void setMaintenance(boolean maintenance) {
        this.maintenance = maintenance;
    }

    public void setNeedsUpgrade(boolean needsUpgrade) {
        this.needsUpgrade = needsUpgrade;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setVersionString(String versionString) {
        this.versionString = versionString;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Status)) {
            return false;
        }
        final Status other = (Status) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.isInstalled() != other.isInstalled()) {
            return false;
        }
        if (this.isMaintenance() != other.isMaintenance()) {
            return false;
        }
        if (this.isNeedsUpgrade() != other.isNeedsUpgrade()) {
            return false;
        }
        final Object this$version = this.getVersion();
        final Object other$version = other.getVersion();
        if (this$version == null ? other$version != null : !this$version.equals(other$version)) {
            return false;
        }
        final Object this$versionString = this.getVersionString();
        final Object other$versionString = other.getVersionString();
        if (this$versionString == null ? other$versionString != null : !this$versionString.equals(other$versionString)) {
            return false;
        }
        final Object this$edition = this.getEdition();
        final Object other$edition = other.getEdition();
        if (this$edition == null ? other$edition != null : !this$edition.equals(other$edition)) {
            return false;
        }
        final Object this$productName = this.getProductName();
        final Object other$productName = other.getProductName();

        return this$productName == null ? other$productName == null : this$productName.equals(other$productName);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Status;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isInstalled() ? 79 : 97);
        result = result * PRIME + (this.isMaintenance() ? 79 : 97);
        result = result * PRIME + (this.isNeedsUpgrade() ? 79 : 97);
        final Object $version = this.getVersion();
        result = result * PRIME + ($version == null ? 43 : $version.hashCode());
        final Object $versionString = this.getVersionString();
        result = result * PRIME + ($versionString == null ? 43 : $versionString.hashCode());
        final Object $edition = this.getEdition();
        result = result * PRIME + ($edition == null ? 43 : $edition.hashCode());
        final Object $productName = this.getProductName();
        result = result * PRIME + ($productName == null ? 43 : $productName.hashCode());
        return result;
    }

    public String toString() {
        return "Status(installed=" + this.isInstalled() + ", maintenance=" + this.isMaintenance() + ", needsUpgrade=" + this.isNeedsUpgrade() + ", version=" + this.getVersion() + ", versionString=" + this.getVersionString() + ", edition=" + this.getEdition() + ", productName=" + this.getProductName() + ")";
    }
}
