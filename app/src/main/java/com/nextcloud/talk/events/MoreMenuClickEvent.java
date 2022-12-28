/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

import com.nextcloud.talk.models.json.conversations.Conversation;

public class MoreMenuClickEvent {
    private final Conversation conversation;

    public MoreMenuClickEvent(Conversation conversation) {
        this.conversation = conversation;
    }

    public Conversation getConversation() {
        return this.conversation;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MoreMenuClickEvent)) {
            return false;
        }
        final MoreMenuClickEvent other = (MoreMenuClickEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$conversation = this.getConversation();
        final Object other$conversation = other.getConversation();

        return this$conversation == null ? other$conversation == null : this$conversation.equals(other$conversation);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof MoreMenuClickEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $conversation = this.getConversation();
        return result * PRIME + ($conversation == null ? 43 : $conversation.hashCode());
    }

    public String toString() {
        return "MoreMenuClickEvent(conversation=" + this.getConversation() + ")";
    }
}
