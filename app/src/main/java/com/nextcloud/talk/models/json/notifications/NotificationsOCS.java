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

package com.nextcloud.talk.models.json.notifications;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.models.json.generic.GenericOCS;

import org.parceler.Parcel;

import java.util.List;

@Parcel
@JsonObject
public class NotificationsOCS extends GenericOCS {
    @JsonField(name = "data")
    List<Notification> notificationsList;

    public NotificationsOCS() {
    }

    public List<Notification> getNotificationsList() {
        return this.notificationsList;
    }

    public void setNotificationsList(List<Notification> notificationsList) {
        this.notificationsList = notificationsList;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof NotificationsOCS)) return false;
        final NotificationsOCS other = (NotificationsOCS) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$notificationsList = this.getNotificationsList();
        final Object other$notificationsList = other.getNotificationsList();
        if (this$notificationsList == null ? other$notificationsList != null : !this$notificationsList.equals(other$notificationsList))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NotificationsOCS;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $notificationsList = this.getNotificationsList();
        result = result * PRIME + ($notificationsList == null ? 43 : $notificationsList.hashCode());
        return result;
    }

    public String toString() {
        return "NotificationsOCS(notificationsList=" + this.getNotificationsList() + ")";
    }
}
