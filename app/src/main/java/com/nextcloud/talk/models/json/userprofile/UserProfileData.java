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

import lombok.Data;

@Parcel
@Data
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
}
