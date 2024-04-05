/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
