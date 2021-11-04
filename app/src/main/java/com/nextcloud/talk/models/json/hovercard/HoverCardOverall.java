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

import java.util.Objects;

@JsonObject
public class HoverCardOverall {
    @JsonField(name = "ocs")
    public HoverCardOCS ocs;

    public HoverCardOCS getOcs() {
        return this.ocs;
    }

    public void setOcs(HoverCardOCS ocs) {
        this.ocs = ocs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HoverCardOverall that = (HoverCardOverall) o;
        return Objects.equals(ocs, that.ocs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ocs);
    }

    @Override
    public String toString() {
        return "HoverCardOverall{" +
            "ocs=" + ocs +
            '}';
    }
}
