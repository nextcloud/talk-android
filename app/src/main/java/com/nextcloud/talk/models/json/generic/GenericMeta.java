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
package com.nextcloud.talk.models.json.generic;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject(serializeNullObjects = true)
public class GenericMeta {
    @JsonField(name = "status")
    public String status;

    @JsonField(name = "statuscode")
    public int statusCode;

    @JsonField(name = "message")
    public String message;

    public String getStatus() {
        return this.status;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public String getMessage() {
        return this.message;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof GenericMeta)) {
            return false;
        }
        final GenericMeta other = (GenericMeta) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$status = this.getStatus();
        final Object other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) {
            return false;
        }
        if (this.getStatusCode() != other.getStatusCode()) {
            return false;
        }
        final Object this$message = this.getMessage();
        final Object other$message = other.getMessage();

        return this$message == null ? other$message == null : this$message.equals(other$message);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GenericMeta;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $status = this.getStatus();
        result = result * PRIME + ($status == null ? 43 : $status.hashCode());
        result = result * PRIME + this.getStatusCode();
        final Object $message = this.getMessage();
        result = result * PRIME + ($message == null ? 43 : $message.hashCode());
        return result;
    }

    public String toString() {
        return "GenericMeta(status=" + this.getStatus() + ", statusCode=" + this.getStatusCode() + ", message=" + this.getMessage() + ")";
    }
}
