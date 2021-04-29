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
import com.nextcloud.talk.controllers.ProfileController;
import com.nextcloud.talk.models.json.converters.ScopeConverter;

import org.parceler.Parcel;

@Parcel
@JsonObject()
public class UserProfileData {
    @JsonField(name = "display-name")
    String displayName;

    @JsonField(name = "displaynameScope", typeConverter = ScopeConverter.class)
    Scope displayNameScope;

    @JsonField(name = "displayname")
    String displayNameAlt;

    @JsonField(name = "id")
    String userId;

    @JsonField(name = "phone")
    String phone;

    @JsonField(name = "phoneScope", typeConverter = ScopeConverter.class)
    Scope phoneScope;

    @JsonField(name = "email")
    String email;

    @JsonField(name = "emailScope", typeConverter = ScopeConverter.class)
    Scope emailScope;

    @JsonField(name = "address")
    String address;

    @JsonField(name = "addressScope", typeConverter = ScopeConverter.class)
    Scope addressScope;

    @JsonField(name = "twitter")
    String twitter;

    @JsonField(name = "twitterScope", typeConverter = ScopeConverter.class)
    Scope twitterScope;

    @JsonField(name = "website")
    String website;

    @JsonField(name = "websiteScope", typeConverter = ScopeConverter.class)
    Scope websiteScope;

    public String getValueByField(ProfileController.Field field) {
        switch (field) {
            case EMAIL:
                return email;
            case DISPLAYNAME:
                return displayName;
            case PHONE:
                return phone;
            case ADDRESS:
                return address;
            case WEBSITE:
                return website;
            case TWITTER:
                return twitter;
            default:
                return "";
        }
    }

    public Scope getScopeByField(ProfileController.Field field) {
        switch (field) {
            case EMAIL:
                return emailScope;
            case DISPLAYNAME:
                return displayNameScope;
            case PHONE:
                return phoneScope;
            case ADDRESS:
                return addressScope;
            case WEBSITE:
                return websiteScope;
            case TWITTER:
                return twitterScope;
            default:
                return null;
        }
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public Scope getDisplayNameScope() {
        return this.displayNameScope;
    }

    public String getDisplayNameAlt() {
        return this.displayNameAlt;
    }

    public String getUserId() {
        return this.userId;
    }

    public String getPhone() {
        return this.phone;
    }

    public Scope getPhoneScope() {
        return this.phoneScope;
    }

    public String getEmail() {
        return this.email;
    }

    public Scope getEmailScope() {
        return this.emailScope;
    }

    public String getAddress() {
        return this.address;
    }

    public Scope getAddressScope() {
        return this.addressScope;
    }

    public String getTwitter() {
        return this.twitter;
    }

    public Scope getTwitterScope() {
        return this.twitterScope;
    }

    public String getWebsite() {
        return this.website;
    }

    public Scope getWebsiteScope() {
        return this.websiteScope;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setDisplayNameScope(Scope displayNameScope) {
        this.displayNameScope = displayNameScope;
    }

    public void setDisplayNameAlt(String displayNameAlt) {
        this.displayNameAlt = displayNameAlt;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setPhoneScope(Scope phoneScope) {
        this.phoneScope = phoneScope;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setEmailScope(Scope emailScope) {
        this.emailScope = emailScope;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setAddressScope(Scope addressScope) {
        this.addressScope = addressScope;
    }

    public void setTwitter(String twitter) {
        this.twitter = twitter;
    }

    public void setTwitterScope(Scope twitterScope) {
        this.twitterScope = twitterScope;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public void setWebsiteScope(Scope websiteScope) {
        this.websiteScope = websiteScope;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserProfileData)) {
            return false;
        }
        final UserProfileData other = (UserProfileData) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$displayName = this.getDisplayName();
        final Object other$displayName = other.getDisplayName();
        if (this$displayName == null ? other$displayName != null : !this$displayName.equals(other$displayName)) {
            return false;
        }
        final Object this$displayNameScope = this.getDisplayNameScope();
        final Object other$displayNameScope = other.getDisplayNameScope();
        if (this$displayNameScope == null ? other$displayNameScope != null : !this$displayNameScope.equals(other$displayNameScope)) {
            return false;
        }
        final Object this$displayNameAlt = this.getDisplayNameAlt();
        final Object other$displayNameAlt = other.getDisplayNameAlt();
        if (this$displayNameAlt == null ? other$displayNameAlt != null : !this$displayNameAlt.equals(other$displayNameAlt)) {
            return false;
        }
        final Object this$userId = this.getUserId();
        final Object other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) {
            return false;
        }
        final Object this$phone = this.getPhone();
        final Object other$phone = other.getPhone();
        if (this$phone == null ? other$phone != null : !this$phone.equals(other$phone)) {
            return false;
        }
        final Object this$phoneScope = this.getPhoneScope();
        final Object other$phoneScope = other.getPhoneScope();
        if (this$phoneScope == null ? other$phoneScope != null : !this$phoneScope.equals(other$phoneScope)) {
            return false;
        }
        final Object this$email = this.getEmail();
        final Object other$email = other.getEmail();
        if (this$email == null ? other$email != null : !this$email.equals(other$email)) {
            return false;
        }
        final Object this$emailScope = this.getEmailScope();
        final Object other$emailScope = other.getEmailScope();
        if (this$emailScope == null ? other$emailScope != null : !this$emailScope.equals(other$emailScope)) {
            return false;
        }
        final Object this$address = this.getAddress();
        final Object other$address = other.getAddress();
        if (this$address == null ? other$address != null : !this$address.equals(other$address)) {
            return false;
        }
        final Object this$addressScope = this.getAddressScope();
        final Object other$addressScope = other.getAddressScope();
        if (this$addressScope == null ? other$addressScope != null : !this$addressScope.equals(other$addressScope)) {
            return false;
        }
        final Object this$twitter = this.getTwitter();
        final Object other$twitter = other.getTwitter();
        if (this$twitter == null ? other$twitter != null : !this$twitter.equals(other$twitter)) {
            return false;
        }
        final Object this$twitterScope = this.getTwitterScope();
        final Object other$twitterScope = other.getTwitterScope();
        if (this$twitterScope == null ? other$twitterScope != null : !this$twitterScope.equals(other$twitterScope)) {
            return false;
        }
        final Object this$website = this.getWebsite();
        final Object other$website = other.getWebsite();
        if (this$website == null ? other$website != null : !this$website.equals(other$website)) {
            return false;
        }
        final Object this$websiteScope = this.getWebsiteScope();
        final Object other$websiteScope = other.getWebsiteScope();

        return this$websiteScope == null ? other$websiteScope == null : this$websiteScope.equals(other$websiteScope);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof UserProfileData;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $displayName = this.getDisplayName();
        result = result * PRIME + ($displayName == null ? 43 : $displayName.hashCode());
        final Object $displayNameScope = this.getDisplayNameScope();
        result = result * PRIME + ($displayNameScope == null ? 43 : $displayNameScope.hashCode());
        final Object $displayNameAlt = this.getDisplayNameAlt();
        result = result * PRIME + ($displayNameAlt == null ? 43 : $displayNameAlt.hashCode());
        final Object $userId = this.getUserId();
        result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
        final Object $phone = this.getPhone();
        result = result * PRIME + ($phone == null ? 43 : $phone.hashCode());
        final Object $phoneScope = this.getPhoneScope();
        result = result * PRIME + ($phoneScope == null ? 43 : $phoneScope.hashCode());
        final Object $email = this.getEmail();
        result = result * PRIME + ($email == null ? 43 : $email.hashCode());
        final Object $emailScope = this.getEmailScope();
        result = result * PRIME + ($emailScope == null ? 43 : $emailScope.hashCode());
        final Object $address = this.getAddress();
        result = result * PRIME + ($address == null ? 43 : $address.hashCode());
        final Object $addressScope = this.getAddressScope();
        result = result * PRIME + ($addressScope == null ? 43 : $addressScope.hashCode());
        final Object $twitter = this.getTwitter();
        result = result * PRIME + ($twitter == null ? 43 : $twitter.hashCode());
        final Object $twitterScope = this.getTwitterScope();
        result = result * PRIME + ($twitterScope == null ? 43 : $twitterScope.hashCode());
        final Object $website = this.getWebsite();
        result = result * PRIME + ($website == null ? 43 : $website.hashCode());
        final Object $websiteScope = this.getWebsiteScope();
        result = result * PRIME + ($websiteScope == null ? 43 : $websiteScope.hashCode());
        return result;
    }

    public String toString() {
        return "UserProfileData(displayName=" + this.getDisplayName() + ", displayNameScope=" + this.getDisplayNameScope() + ", displayNameAlt=" + this.getDisplayNameAlt() + ", userId=" + this.getUserId() + ", phone=" + this.getPhone() + ", phoneScope=" + this.getPhoneScope() + ", email=" + this.getEmail() + ", emailScope=" + this.getEmailScope() + ", address=" + this.getAddress() + ", addressScope=" + this.getAddressScope() + ", twitter=" + this.getTwitter() + ", twitterScope=" + this.getTwitterScope() + ", website=" + this.getWebsite() + ", websiteScope=" + this.getWebsiteScope() + ")";
    }
}
