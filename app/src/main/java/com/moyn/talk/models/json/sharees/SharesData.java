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
public class SharesData {
    @JsonField(name = "users")
    List<Sharee> users;

    @JsonField(name = "exact")
    ExactSharees exactUsers;

    public List<Sharee> getUsers() {
        return this.users;
    }

    public ExactSharees getExactUsers() {
        return this.exactUsers;
    }

    public void setUsers(List<Sharee> users) {
        this.users = users;
    }

    public void setExactUsers(ExactSharees exactUsers) {
        this.exactUsers = exactUsers;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SharesData)) {
            return false;
        }
        final SharesData other = (SharesData) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$users = this.getUsers();
        final Object other$users = other.getUsers();
        if (this$users == null ? other$users != null : !this$users.equals(other$users)) {
            return false;
        }
        final Object this$exactUsers = this.getExactUsers();
        final Object other$exactUsers = other.getExactUsers();

        return this$exactUsers == null ? other$exactUsers == null : this$exactUsers.equals(other$exactUsers);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SharesData;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $users = this.getUsers();
        result = result * PRIME + ($users == null ? 43 : $users.hashCode());
        final Object $exactUsers = this.getExactUsers();
        result = result * PRIME + ($exactUsers == null ? 43 : $exactUsers.hashCode());
        return result;
    }

    public String toString() {
        return "SharesData(users=" + this.getUsers() + ", exactUsers=" + this.getExactUsers() + ")";
    }
}
