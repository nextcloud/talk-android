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

import java.util.List;
import java.util.Objects;

@Parcel
@JsonObject
public class HoverCard {

    @JsonField(name = "userId")
    public String userId;

    @JsonField(name = "displayName")
    public String displayName;

    @JsonField(name = "actions")
    public List<HoverCardAction> actions;


    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<HoverCardAction> getActions() {
        return actions;
    }

    public void setActions(List<HoverCardAction> actions) {
        this.actions = actions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HoverCard hoverCard = (HoverCard) o;
        return Objects.equals(userId, hoverCard.userId) &&
            Objects.equals(displayName, hoverCard.displayName) &&
            Objects.equals(actions, hoverCard.actions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, displayName, actions);
    }

    @Override
    public String toString() {
        return "HoverCard{" +
            "userId='" + userId + '\'' +
            ", displayName='" + displayName + '\'' +
            ", actions=" + actions +
            '}';
    }
}
