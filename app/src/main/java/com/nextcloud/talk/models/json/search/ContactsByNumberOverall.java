/*
 * Nextcloud Talk application
 *  
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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

package com.nextcloud.talk.models.json.search;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class ContactsByNumberOverall {
    @JsonField(name = "ocs")
    public ContactsByNumberOCS ocs;

    public ContactsByNumberOCS getOcs() {
        return this.ocs;
    }

    public void setOcs(ContactsByNumberOCS ocs) {
        this.ocs = ocs;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ContactsByNumberOverall)) {
            return false;
        }
        final ContactsByNumberOverall other = (ContactsByNumberOverall) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$ocs = this.getOcs();
        final Object other$ocs = other.getOcs();

        return this$ocs == null ? other$ocs == null : this$ocs.equals(other$ocs);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ContactsByNumberOverall;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $ocs = this.getOcs();
        result = result * PRIME + ($ocs == null ? 43 : $ocs.hashCode());
        return result;
    }

    public String toString() {
        return "ContactsByNumberOverall(ocs=" + this.getOcs() + ")";
    }
}
