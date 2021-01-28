/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.models.json.userprofile;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject()
public class UserProfileData {
    @JsonField(name = "display-name")
    String displayName;

    @JsonField(name = "displayname")
    String displayNameAlt;

    @JsonField(name = "id")
    String userId;

    public UserProfileData() {
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDisplayNameAlt() {
        return this.displayNameAlt;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setDisplayNameAlt(String displayNameAlt) {
        this.displayNameAlt = displayNameAlt;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof UserProfileData)) return false;
        final UserProfileData other = (UserProfileData) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$displayName = this.getDisplayName();
        final Object other$displayName = other.getDisplayName();
        if (this$displayName == null ? other$displayName != null : !this$displayName.equals(other$displayName))
            return false;
        final Object this$displayNameAlt = this.getDisplayNameAlt();
        final Object other$displayNameAlt = other.getDisplayNameAlt();
        if (this$displayNameAlt == null ? other$displayNameAlt != null : !this$displayNameAlt.equals(other$displayNameAlt))
            return false;
        final Object this$userId = this.getUserId();
        final Object other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof UserProfileData;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $displayName = this.getDisplayName();
        result = result * PRIME + ($displayName == null ? 43 : $displayName.hashCode());
        final Object $displayNameAlt = this.getDisplayNameAlt();
        result = result * PRIME + ($displayNameAlt == null ? 43 : $displayNameAlt.hashCode());
        final Object $userId = this.getUserId();
        result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
        return result;
    }

    public String toString() {
        return "UserProfileData(displayName=" + this.getDisplayName() + ", displayNameAlt=" + this.getDisplayNameAlt() + ", userId=" + this.getUserId() + ")";
    }
}
