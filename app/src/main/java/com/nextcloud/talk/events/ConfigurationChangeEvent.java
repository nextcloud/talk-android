/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.events;

public class ConfigurationChangeEvent {
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConfigurationChangeEvent)) {
            return false;
        }
        final ConfigurationChangeEvent other = (ConfigurationChangeEvent) o;

        return other.canEqual((Object) this);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ConfigurationChangeEvent;
    }

    public int hashCode() {
        return 1;
    }

    public String toString() {
        return "ConfigurationChangeEvent()";
    }
}
