/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
