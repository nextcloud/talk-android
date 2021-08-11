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
package com.moyn.talk.models.json.sharees;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

import java.util.List;

@Parcel
@JsonObject
public class ExactSharees {
    @JsonField(name = "users")
    List<Sharee> exactSharees;

    public List<Sharee> getExactSharees() {
        return this.exactSharees;
    }

    public void setExactSharees(List<Sharee> exactSharees) {
        this.exactSharees = exactSharees;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ExactSharees)) {
            return false;
        }
        final ExactSharees other = (ExactSharees) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$exactSharees = this.getExactSharees();
        final Object other$exactSharees = other.getExactSharees();

        return this$exactSharees == null ? other$exactSharees == null : this$exactSharees.equals(other$exactSharees);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ExactSharees;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $exactSharees = this.getExactSharees();
        result = result * PRIME + ($exactSharees == null ? 43 : $exactSharees.hashCode());
        return result;
    }

    public String toString() {
        return "ExactSharees(exactSharees=" + this.getExactSharees() + ")";
    }
}
