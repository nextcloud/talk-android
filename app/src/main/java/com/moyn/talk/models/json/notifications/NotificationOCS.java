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

package com.moyn.talk.models.json.notifications;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.moyn.talk.models.json.generic.GenericOCS;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class NotificationOCS extends GenericOCS {
    @JsonField(name = "data")
    Notification notification;

    public Notification getNotification() {
        return this.notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NotificationOCS)) {
            return false;
        }
        final NotificationOCS other = (NotificationOCS) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$notification = this.getNotification();
        final Object other$notification = other.getNotification();

        return this$notification == null ? other$notification == null : this$notification.equals(other$notification);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NotificationOCS;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $notification = this.getNotification();
        result = result * PRIME + ($notification == null ? 43 : $notification.hashCode());
        return result;
    }

    public String toString() {
        return "NotificationOCS(notification=" + this.getNotification() + ")";
    }
}
