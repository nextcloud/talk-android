/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.models.json.chat;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;

@Parcel
@JsonObject
public class ChatShareOverviewOCS {
    @JsonField(name = "data")
    public HashMap<String, List<Object>> data;

    public HashMap<String, List<Object>> getData() {
        return this.data;
    }

    public void setData(HashMap<String, List<Object>> data) {
        this.data = data;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ChatShareOverviewOCS)) {
            return false;
        }
        final ChatShareOverviewOCS other = (ChatShareOverviewOCS) o;
        if (!other.canEqual(this)) {
            return false;
        }
        final Object this$data = this.getData();
        final Object other$data = other.getData();

        return Objects.equals(this$data, other$data);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ChatShareOverviewOCS;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $data = this.getData();
        return result * PRIME + ($data == null ? 43 : $data.hashCode());
    }

    public String toString() {
        return "ChatShareOverviewOCS(data=" + this.getData() + ")";
    }
}
