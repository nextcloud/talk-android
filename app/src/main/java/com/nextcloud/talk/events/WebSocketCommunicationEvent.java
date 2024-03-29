/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.events;

import java.util.HashMap;

import androidx.annotation.Nullable;

public class WebSocketCommunicationEvent {
    public final String type;
    @Nullable
    public final HashMap<String, String> hashMap;

    public WebSocketCommunicationEvent(String type, HashMap<String, String> hashMap) {
        this.type = type;
        this.hashMap = hashMap;
    }

    public String getType() {
        return this.type;
    }

    @Nullable
    public HashMap<String, String> getHashMap() {
        return this.hashMap;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof WebSocketCommunicationEvent)) {
            return false;
        }
        final WebSocketCommunicationEvent other = (WebSocketCommunicationEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        final Object this$hashMap = this.getHashMap();
        final Object other$hashMap = other.getHashMap();

        return this$hashMap == null ? other$hashMap == null : this$hashMap.equals(other$hashMap);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof WebSocketCommunicationEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $hashMap = this.getHashMap();
        return result * PRIME + ($hashMap == null ? 43 : $hashMap.hashCode());
    }

    public String toString() {
        return "WebSocketCommunicationEvent(type=" + this.getType() + ", hashMap=" + this.getHashMap() + ")";
    }
}