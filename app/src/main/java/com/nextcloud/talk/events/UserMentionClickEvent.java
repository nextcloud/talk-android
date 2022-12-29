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

package com.nextcloud.talk.events;

public class UserMentionClickEvent {
    public final String userId;

    public UserMentionClickEvent(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return this.userId;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserMentionClickEvent)) {
            return false;
        }
        final UserMentionClickEvent other = (UserMentionClickEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$userId = this.getUserId();
        final Object other$userId = other.getUserId();

        return this$userId == null ? other$userId == null : this$userId.equals(other$userId);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof UserMentionClickEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $userId = this.getUserId();
        return result * PRIME + ($userId == null ? 43 : $userId.hashCode());
    }

    public String toString() {
        return "UserMentionClickEvent(userId=" + this.getUserId() + ")";
    }
}
