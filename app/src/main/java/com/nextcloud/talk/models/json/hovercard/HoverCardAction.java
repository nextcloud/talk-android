/*
 *
 *   Nextcloud Talk application
 *
 *   @author Tim Krüger
 *   Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
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
package com.nextcloud.talk.models.json.hovercard;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

import java.util.Objects;

@Parcel
@JsonObject
public class HoverCardAction {

    @JsonField(name = "title")
    public String title;

    @JsonField(name = "icon")
    public String icon;

    @JsonField(name = "hyperlink")
    public String hyperlink;

    @JsonField(name = "appId")
    public String appId;

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getHyperlink() {
        return hyperlink;
    }

    public void setHyperlink(String hyperlink) {
        this.hyperlink = hyperlink;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HoverCardAction that = (HoverCardAction) o;
        return Objects.equals(title, that.title) &&
            Objects.equals(icon, that.icon) &&
            Objects.equals(hyperlink, that.hyperlink) &&
            Objects.equals(appId, that.appId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, icon, hyperlink, appId);
    }

    @Override
    public String toString() {
        return "HoverCardAction{" +
            "title='" + title + '\'' +
            ", icon='" + icon + '\'' +
            ", hyper='" + hyperlink + '\'' +
            ", appId='" + appId + '\'' +
            '}';
    }
}
