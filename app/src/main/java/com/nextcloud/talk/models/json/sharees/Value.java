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
package com.nextcloud.talk.models.json.sharees;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class Value {
    @JsonField(name = "shareWith")
    String shareWith;

    public String getShareWith() {
        return this.shareWith;
    }

    public void setShareWith(String shareWith) {
        this.shareWith = shareWith;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Value)) {
            return false;
        }
        final Value other = (Value) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$shareWith = this.getShareWith();
        final Object other$shareWith = other.getShareWith();

        return this$shareWith == null ? other$shareWith == null : this$shareWith.equals(other$shareWith);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Value;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $shareWith = this.getShareWith();
        result = result * PRIME + ($shareWith == null ? 43 : $shareWith.hashCode());
        return result;
    }

    public String toString() {
        return "Value(shareWith=" + this.getShareWith() + ")";
    }
}
